/*
 * Village Defense - Protect villagers from hordes of zombies
 * Copyright (C) 2021  Plugily Projects - maintained by 2Wild4You, Tigerpanzer_02 and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugily.projects.minigamesbox.classic.user.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import plugily.projects.commonsbox.database.MysqlDatabase;
import plugily.projects.minigamesbox.classic.Main;
import plugily.projects.minigamesbox.classic.api.StatisticType;
import plugily.projects.minigamesbox.classic.user.User;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 21.09.2021
 */
public class MysqlManager implements UserDatabase {

  private final Main plugin;
  private final MysqlDatabase database;

  public MysqlManager(Main plugin) {
    this.plugin = plugin;

    FileConfiguration config = ConfigUtils.getConfig(plugin, "mysql");
    database = new MysqlDatabase(config.getString("user"), config.getString("password"), config.getString("address"), config.getLong("maxLifeTime", 1800000));
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try(Connection connection = database.getConnection();
          Statement statement = connection.createStatement()) {
        plugin.getDebugger().debug("Database enabled");

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + getTableName() + "` (\n"
            + "  `UUID` char(36) NOT NULL PRIMARY KEY,\n"
            + "  `name` varchar(32) NOT NULL,\n"
            + "  `kills` int(11) NOT NULL DEFAULT '0',\n"
            + "  `deaths` int(11) NOT NULL DEFAULT '0',\n"
            + "  `highestwave` int(11) NOT NULL DEFAULT '0',\n"
            + "  `gamesplayed` int(11) NOT NULL DEFAULT '0',\n"
            + "  `level` int(11) NOT NULL DEFAULT '0',\n"
            + "  `xp` int(11) NOT NULL DEFAULT '0',\n"
            + "  `orbs` int(11) NOT NULL DEFAULT '0'\n"
            + ");");

        //temporary workaround
        try {
          statement.executeUpdate("ALTER TABLE " + getTableName() + " ADD `name` text NOT NULL");
        } catch(SQLException e) {
          if(!e.getMessage().contains("Duplicate column name")) {
            plugin.getLogger().log(Level.WARNING, "Could not connect to MySQL database! Cause: {0} ({1})", new Object[]{e.getSQLState(), e.getErrorCode()});
          }
        }
      } catch(SQLException e) {
        plugin.getLogger().log(Level.WARNING, "Could not connect to MySQL database! Cause: {0} ({1})", new Object[]{e.getSQLState(), e.getErrorCode()});
        plugin.getMessageUtils().errorOccurred();
        plugin.getDebugger().sendConsoleMsg("Cannot save contents to MySQL database!");
        plugin.getDebugger().sendConsoleMsg("Check configuration of mysql.yml file or disable mysql option in config.yml");
      }
    });
  }

  public String getTableName() {
    return ConfigUtils.getConfig(plugin, "mysql").getString("table", "playerstats");
  }

  @Override
  public MysqlDatabase getMySQLDatabase() {
    return database;
  }

  @Override
  public void saveStatistic(User user, StatisticType stat) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        database.executeUpdate("UPDATE " + getTableName() + " SET " + stat.getName() + "=" + user.getStat(stat) + " WHERE UUID='" + user.getUniqueId().toString() + "';"));
  }

  @Override
  public void saveAllStatistic(User user) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.executeUpdate(getUpdateQuery(user)));
  }

  @Override
  public void loadStatistics(User user) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      String uuid = user.getUniqueId().toString();
      try(Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
        String playerName = user.getPlayer().getName();

        database.executeUpdate("UPDATE " + getTableName() + " SET name='" + playerName + "' WHERE UUID='" + uuid + "';");
        ResultSet rs = statement.executeQuery("SELECT * from " + getTableName() + " WHERE UUID='" + uuid + "'");
        if(rs.next()) {
          //player already exists - get the stats
          plugin.getStatsStorage().getStatistics().forEach((s, statisticType) -> {
            if(statisticType.isPersistent()) {
              try {
                user.setStat(statisticType, rs.getInt(statisticType.getName()));
              } catch(SQLException e) {
                e.printStackTrace();
              }
            }
          });
        } else {
          //player doesn't exist - make a new record
          statement.executeUpdate("INSERT INTO " + getTableName() + " (UUID,name) VALUES ('" + uuid + "','" + playerName + "')");
          plugin.getStatsStorage().getStatistics().forEach((s, statisticType) -> {
            if(statisticType.isPersistent()) {
              user.setStat(statisticType, 0);
            }
          });
        }
      } catch(SQLException e) {
        plugin.getLogger().log(Level.WARNING, "Could not connect to MySQL database! Cause: {0} ({1})", new Object[]{e.getSQLState(), e.getErrorCode()});
      }
    });
  }

  @NotNull
  @Override
  public Map<UUID, Integer> getStats(StatisticType stat) {
    try(Connection connection = database.getConnection();
        Statement statement = connection.createStatement();
        ResultSet set = statement.executeQuery("SELECT UUID, " + stat.getName() + " FROM " + getTableName() + " ORDER BY " + stat.getName())) {
      Map<UUID, Integer> column = new LinkedHashMap<>();
      while(set.next()) {
        String uuid = set.getString("UUID");

        if(uuid == null)
          continue;

        try {
          column.put(UUID.fromString(uuid), set.getInt(stat.getName()));
        } catch(IllegalArgumentException ex) {
          plugin.getLogger().log(Level.WARNING, "Cannot load the UUID for {0}", uuid);
        }
      }
      return column;
    } catch(SQLException e) {
      plugin.getLogger().log(Level.WARNING, "SQLException occurred! " + e.getSQLState() + " (" + e.getErrorCode() + ")");
      plugin.getMessageUtils().errorOccurred();
      Bukkit.getConsoleSender().sendMessage("Cannot get contents from MySQL database!");
      Bukkit.getConsoleSender().sendMessage("Check configuration of mysql.yml file or disable mysql option in config.yml");
      return Collections.emptyMap();
    }
  }

  @Override
  public void disable() {
    for(Player player : plugin.getServer().getOnlinePlayers()) {
      database.executeUpdate(getUpdateQuery(plugin.getUserManager().getUser(player)));
    }
    database.shutdownConnPool();
  }

  @Override
  public String getPlayerName(UUID uuid) {
    try(Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
      return statement.executeQuery("SELECT `name` FROM " + getTableName() + " WHERE UUID='" + uuid.toString() + "'").toString();
    } catch(SQLException | NullPointerException e) {
      return null;
    }
  }

  private String getUpdateQuery(User user) {
    StringBuilder update = new StringBuilder(" SET ");
    plugin.getStatsStorage().getStatistics().forEach((s, statisticType) -> {
      if(statisticType.isPersistent()) {
        if(!update.toString().equalsIgnoreCase(" SET ")) {
          update.append(", ");
        }
        update.append(statisticType.getName()).append("=").append(user.getStat(statisticType));
      }
    });
    return "UPDATE " + getTableName() + update + " WHERE UUID='" + user.getUniqueId().toString() + "';";
  }
}
