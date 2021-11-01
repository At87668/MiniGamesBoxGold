/*
 * MiniGamesBox - Library box with massive content that could be seen as minigames core.
 * Copyright (C)  2021  Plugily Projects - maintained by Tigerpanzer_02 and contributors
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
 *
 */

package plugily.projects.minigamesbox.classic.commands.arguments.admin;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import plugily.projects.commonsbox.number.NumberUtils;
import plugily.projects.minigamesbox.classic.api.StatisticType;
import plugily.projects.minigamesbox.classic.commands.arguments.ArgumentsRegistry;
import plugily.projects.minigamesbox.classic.commands.arguments.data.CommandArgument;
import plugily.projects.minigamesbox.classic.commands.arguments.data.LabelData;
import plugily.projects.minigamesbox.classic.commands.arguments.data.LabeledCommandArgument;
import plugily.projects.minigamesbox.classic.handlers.hologram.LeaderboardHologram;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.minigamesbox.classic.utils.serialization.LocationSerializer;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 01.11.2021
 */
public class HologramArgument {

  private final ArgumentsRegistry registry;

  public HologramArgument(ArgumentsRegistry registry) {
    this.registry = registry;
    registry.mapArgument(registry.getPlugin().getCommandAdminPrefixLong(), new LabeledCommandArgument("hologram", registry.getPlugin().getPluginNamePrefixLong() + ".admin.hologram.manage", CommandArgument.ExecutorType.PLAYER,
        new LabelData("/" + registry.getPlugin().getCommandAdminPrefix() + " hologram &6<action>", "/" + registry.getPlugin().getCommandAdminPrefix() + " hologram <action>", "&7Command handles 3 arguments:\n&7• /" + registry.getPlugin().getCommandAdminPrefix() + " hologram add <statistic type> <amount> - creates new hologram"
            + "of target statistic\n&7with top X amount of players (max 20)\n&7• /" + registry.getPlugin().getCommandAdminPrefix() + " hologram remove <id> - removes hologram of target ID\n"
            + "&7• /" + registry.getPlugin().getCommandAdminPrefix() + " hologram list - prints list of all leaderboard holograms")) {
      @Override
      public void execute(CommandSender sender, String[] args) {
        if(args.length < 2) {
          sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cToo few arguments! Please type /" + registry.getPlugin().getCommandAdminPrefix() + " hologram <add/remove/list>"));
          return;
        }
        if(args[1].equalsIgnoreCase("add")) {
          handleAddArgument((Player) sender, args);
        } else if(args[1].equalsIgnoreCase("list")) {
          handleListArgument(sender);
        } else if(args[1].equalsIgnoreCase("remove")) {
          handleDeleteArgument(sender, args);
        } else {
          sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cBad arguments! Please type /" + registry.getPlugin().getCommandAdminPrefix() + " hologram <add/remove/list>"));
        }
      }
    });
  }

  private void handleAddArgument(Player player, String[] args) {
    StatisticType statistic;
    try {
      statistic = registry.getPlugin().getStatsStorage().getStatisticType(args[2].toUpperCase());
    } catch(IllegalArgumentException ex) {
      sendInvalidStatisticMessage(player);
      return;
    }

    if(!statistic.isPersistent()) {
      sendInvalidStatisticMessage(player);
      return;
    }

    if(args.length != 4) {
      player.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cToo few arguments! Please type /" + registry.getPlugin().getCommandAdminPrefix() + " hologram add <statistic type> <amount>"));
      return;
    }
    java.util.Optional<Integer> opt = NumberUtils.parseInt(args[3]);
    if(!opt.isPresent()) {
      player.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cLeaderboard amount entries must be a number!"));
      return;
    }
    int amount = opt.get();
    if(amount <= 0 || amount > 20) {
      player.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cLeaderboard amount entries amount are limited to 20 and minimum of 0!"));
      return;
    }

    FileConfiguration config = ConfigUtils.getConfig(registry.getPlugin(), "internal/holograms_data");
    int nextValue = config.getConfigurationSection("holograms").getKeys(false).size() + 1;
    config.set("holograms." + nextValue + ".statistics", statistic.getName());
    config.set("holograms." + nextValue + ".top-amount", amount);
    config.set("holograms." + nextValue + ".location", LocationSerializer.locationToString(player.getLocation()));
    ConfigUtils.saveConfig(registry.getPlugin(), config, "internal/holograms_data");

    LeaderboardHologram leaderboard = new LeaderboardHologram(registry.getPlugin(), nextValue, statistic, amount, player.getLocation());
    leaderboard.initUpdateTask();
    registry.getPlugin().getHologramsRegistry().registerHologram(leaderboard);

    player.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&aHologram with ID " + nextValue + " with statistic " + statistic.getName() + " added!"));
  }

  private void sendInvalidStatisticMessage(Player player) {
    StringBuilder values = new StringBuilder();
    for(StatisticType value : registry.getPlugin().getStatsStorage().getStatistics().values()) {
      values.append(value).append(' ');
    }
    player.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cInvalid statistic type! Valid types: &e" + values));
  }

  private void handleListArgument(CommandSender sender) {
    FileConfiguration config = ConfigUtils.getConfig(registry.getPlugin(), "internal/holograms_data");
    for(String key : config.getConfigurationSection("holograms").getKeys(false)) {
      sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&aID " + key));
      sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage(" &eTop: " + config.getInt("holograms." + key + ".top-amount")
          + " Stat: " + config.getStringList("holograms." + key + ".statistics")));
      sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage(" &eLocation: " + getFriendlyLocation(LocationSerializer.getLocation(config.getString("holograms." + key + ".location")))));
    }
  }

  private String getFriendlyLocation(Location location) {
    return "World: " + location.getWorld().getName() + ", X: " + location.getBlockX() + ", Y: " + location.getBlockY() + ", Z: " + location.getBlockZ();
  }

  private void handleDeleteArgument(CommandSender sender, String[] args) {
    if(args.length != 3) {
      sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cPlease type leaderboard ID to remove it!"));
      return;
    }
    java.util.Optional<Integer> opt = NumberUtils.parseInt(args[2]);
    if(!opt.isPresent()) {
      sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cLeaderboard ID must be a number!"));
      return;
    }
    FileConfiguration config = ConfigUtils.getConfig(registry.getPlugin(), "internal/holograms_data");
    if(!config.isSet("holograms." + args[2])) {
      sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&cLeaderboard with that ID doesn't exist!"));
      return;
    }
    config.set("holograms." + args[2], null);
    ConfigUtils.saveConfig(registry.getPlugin(), config, "internal/holograms_data");
    registry.getPlugin().getHologramsRegistry().disableHologram(opt.get());
    sender.sendMessage(registry.getPlugin().getChatManager().colorRawMessage("&aLeaderboard with ID " + args[2] + " sucessfully deleted!"));
  }

}
