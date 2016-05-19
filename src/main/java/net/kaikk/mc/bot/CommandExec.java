/*
    BetterOntime plugin for Minecraft Bukkit server
    Copyright (C) 2015 Antonino Kai Pocorobba

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.kaikk.mc.bot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

class CommandExec implements CommandExecutor {
	private BetterOntime instance;
	private ExecutorService executor = Executors.newSingleThreadExecutor();

	CommandExec(BetterOntime instance) {
		this.instance = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("betterontime")) {
			if (args.length==0) {
				// check own playtime
				return stats(sender, label, args);
			}

			switch(args[0].toLowerCase()) {
			case "info":
				return info(sender, label, args);
			case "reload":
				return reload(sender, label, args);
			case "help":
				return help(sender, label, args);
			case "add":
				return add(sender, label, args);
			case "set":
				return set(sender, label, args);
			case "leaderboard":
				return leaderboard(sender, label, args);
			case "cmd":
				return cmd(sender, label, args);
			default:
				// check specified player's playtime
				return stats(sender, label, args);
			}
		} else if (cmd.getName().equalsIgnoreCase("msgraw")) {
			if (!sender.isOp()) {
				sender.sendMessage("This is an OP only command.");
				return false;
			}

			if (args.length<2) {
				sender.sendMessage("Usage: /msgraw [PlayerName] [Raw Message]");
				return false;
			}

			Player targetPlayer = instance.getServer().getPlayer(args[0]);
			if (targetPlayer==null) {
				sender.sendMessage("Invalid player");
				return false;
			}

			targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', Utils.mergeStringArrayFromIndex(args,1)));
			return true;
		}

		return false;
	}


	private boolean info(final CommandSender sender, final String label, final String[] args) {
		if (!sender.hasPermission("betterontime.manage")) {
			sender.sendMessage("You're not allowed to run this command.");
			return false;
		}

		sender.sendMessage("BetterOntime serverId: "+instance.config.serverId);
		return true;
	}

	private boolean help(final CommandSender sender, final String label, final String[] args) {
		sender.sendMessage("Aliases: betterontime, bot, ontime\n"
				+ "- shows your statistics\n"
				+ (sender.hasPermission("betterontime.others") ? "- [name] - checks the player's playtime\n" : "")
				+ (sender.hasPermission("betterontime.leaderboard") ? "- leaderboard - shows the leaderboard\n" : "")
				+ (sender.hasPermission("betterontime.manage") ? ""
						+ "- add [name] [time] - add playtime to player's statistics\n"
						+ "- set [name] [time] - set player's global playtime\n"
						+ "- cmd - manages commands\n"
						+ "- reload - reloads data\n" : ""));
		return true;
	}

	private boolean reload(final CommandSender sender, final String label, final String[] args) {
		if (!sender.hasPermission("betterontime.manage")) {
			sender.sendMessage("You're not allowed to run this command.");
			return false;
		}

		sender.sendMessage("Reloading BetterOntime... prepare for unforeseen consequences!");

		try {
			Bukkit.getPluginManager().disablePlugin(instance);
			Bukkit.getPluginManager().enablePlugin(instance);
			sender.sendMessage("BetterOntime reloaded!");
		} catch (Exception e) {
			e.printStackTrace();
			sender.sendMessage("BetterOntime reload error!");
		}

		return true;
	}

	private boolean cmd(final CommandSender sender, final String label, final String[] args) {
		if (!sender.hasPermission("betterontime.manage")) {
			sender.sendMessage("You're not allowed to run this command.");
			return false;
		}

		if (args.length==1) {
			sender.sendMessage("Usage: /betterontime cmd [list|add|remove]");
			return false;
		}

		if (args[1].equalsIgnoreCase("list")) {
			if (instance.ds.commands.isEmpty()) {
				sender.sendMessage("No commands found for this server.");
				return true;
			}
			sender.sendMessage(ChatColor.RED + "BetterOntime commands list:");
			for(StoredCommand command : instance.ds.commands) {
				String msg="&3("+command.id+") "+(command.repeated?"Every ":"At &2")+Utils.timeToString(command.time);

				String[] commandsToRun = command.command.split("@n@");

				for (String cmdToRun : commandsToRun) {
					msg=msg+"\n&c>&f "+cmdToRun;
				}

				sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
			}
			return true;
		} else if (args[1].equalsIgnoreCase("add")) {
			if (args.length<5) {
				sender.sendMessage("Usage: /betterontime cmd add [time] [repeated: true|false] [command]");
				return false;
			}

			Integer secs = Utils.stringToTime(args[2]);
			if (secs==null) {
				sender.sendMessage("Usage: /betterontime cmd add [time] [repeated: true|false] [command]");
				return false;
			}

			if (secs<1) {
				sender.sendMessage("Time must be greater than 0.");
				return false;
			}

			boolean repeated = args[3].equalsIgnoreCase("true") ? true : false;

			String stringCommand = Utils.mergeStringArrayFromIndex(args, 4);

			final StoredCommand command = new StoredCommand(0, secs, repeated, stringCommand);
			final Future<ResultSet> futureResult = instance.ds.asyncUpdateGenKeys("INSERT INTO commands (server, time, repeated, command) VALUES ("+instance.config.serverId+", "+command.time+", "+(command.repeated?"1":"0")+", \""+command.command+"\")");

			executor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						// Asynchronously wait for result
						ResultSet result = futureResult.get();
						if (result.next()) {
							command.id=result.getInt(1);
							instance.ds.commands.add(command);
							sender.sendMessage("Command added.");
						}
					} catch (InterruptedException | ExecutionException | SQLException e) {
						e.printStackTrace();
					}
				}
			});
		} else if (args[1].equalsIgnoreCase("remove")) {
			if (args.length!=3) {
				sender.sendMessage("Usage: /betterontime cmd remove [id]");
				return false;
			}

			try {
				Integer id = Integer.valueOf(args[2]);
				instance.ds.asyncUpdate("DELETE FROM commands WHERE id="+id);
				int i=-1;
				
				for (int j=0; j<instance.ds.commands.size(); j++) {
					if (instance.ds.commands.get(j).id==id) {
						i=j;
						break;
					}
				}
				
				if (i==-1) {
					sender.sendMessage("Command id doesn't exist.");
					return false;
				}
				
				instance.ds.commands.remove(i);
				
				for(PlayerStats stats : instance.ds.onlinePlayersStats.values()) {
					stats.calculateCommandsExclusions();
				}
				
				sender.sendMessage("Command removed.");
			} catch (NumberFormatException e) {
				sender.sendMessage("Wrong id.");
				return false;
			}
		}
		return true;
	}

	private boolean leaderboard(final CommandSender sender, final String label, final String[] args) {
		if (!sender.hasPermission("betterontime.leaderboard")) {
			sender.sendMessage("You're not allowed to run this command.");
			return false;
		}

		sender.sendMessage(ChatColor.AQUA + "Retrieving leaderboard...");
		
		instance.ds.leaderboard(sender);

		return true;
	}

	private boolean add(final CommandSender sender, final String label, final String[] args) {
            
                if(1 == 1){
                    // @TODO: It should be possible add to others time.
                    sender.sendMessage("Only Possible to check yourself currently.");
                    return false;
                }
                
		if (!sender.hasPermission("betterontime.manage")) {
			sender.sendMessage("You're not allowed to run this command.");
			return false;
		}

		if (args.length!=3) {
			sender.sendMessage("Usage: /betterontime add [PlayerName] [time]"); 
			return false;
		}

		Integer time=Utils.stringToTime(args[2]);
		if (time==null||time<1) {
			sender.sendMessage("Invalid time.");
			return false;
		}

		UUID uuid = null; // UUIDProvider.get(args[1]);
		if (uuid==null) {
			sender.sendMessage("Invalid player name.");
			return false;
		}

		instance.ds.addTime(uuid, time, 0, 0);
		sender.sendMessage("Added "+Utils.timeToString(time)+" to "+args[1]+"'s playtime.");
		return true;
	}

	private boolean set(final CommandSender sender, final String label, final String[] args) {
            				
                if(1 == 1){
                    // @TODO: It should be possible set others time.
                    sender.sendMessage("Only Possible to check yourself currently.");
                    return false;
                }
                                
		if (!sender.hasPermission("betterontime.manage")) {
			sender.sendMessage("You're not allowed to run this command.");
			return false;
		}

		if (args.length!=3) {
			sender.sendMessage("Usage: /betterontime set [PlayerName] [time]"); 
			return false;
		}

		Integer time=Utils.stringToTime(args[2]);
		if (time==null||time<1) {
			sender.sendMessage("Invalid time.");
			return false;
		}

		UUID uuid = null; //UUIDProvider.get(args[1]);
		if (uuid==null) {
			sender.sendMessage("Invalid player name.");
			return false;
		}
		
		OfflinePlayer targetPlayer=instance.getServer().getOfflinePlayer(args[1]);
		if (targetPlayer.isOnline()) {
			PlayerStats stats = new PlayerStats(uuid, 0, 0, time, 0, time, 0, Utils.epoch());
			stats.player=targetPlayer.getPlayer();
			stats.calculateCommandsExclusions();
			BetterOntime.instance().ds.onlinePlayersStats.put(uuid, stats);
		}

		instance.ds.setTime(uuid, time);
		sender.sendMessage("Set "+args[1]+"'s playtime to "+Utils.timeToString(time));
		return true;
	}

	private boolean stats(final CommandSender sender, final String label, final String[] args) {
		final String playerToCheck;
                final UUID uuidToCheck;
		if (args.length==1 && !sender.getName().equalsIgnoreCase(args[0])) {
			if (!sender.hasPermission("betterontime.others")) {
				sender.sendMessage("You're not allowed to run this command.");
				return false;
			}
			
			playerToCheck = args[0];
                        uuidToCheck = null;
		} else {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Usage: /"+label+" [PlayerName]");
				return false;
			}
			if (!sender.hasPermission("betterontime.self")) {
				sender.sendMessage("You're not allowed to run this command.");
				return false;
			}
			
			uuidToCheck = ((Player) sender).getUniqueId();
                        playerToCheck = sender.getName();
		}
		
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if(uuidToCheck == null){
                                    // @TODO: It should be possible to check others.
                                    sender.sendMessage("Only Possible to check yourself currently.");
                                    return;
                                }
				if (uuidToCheck == null) {
					sender.sendMessage("Invalid player name: "+playerToCheck);
					return;
				}
				
				final String message;
				PlayerStats stats = instance.ds.getPlayerStats(uuidToCheck);
				if (stats==null || stats.global==0) {
					message = "No statistics found for this player.";
				} else {
					int timeToAdd=Utils.epoch()-stats.lastEpochTime;

					message = ChatColor.translateAlternateColorCodes('&', "&6---- Statistics for "+playerToCheck+" ----\n"
							+ "&cGlobal:\n&e- Total    : &f"+Utils.timeToString(stats.global+timeToAdd)+"\n&e- Last 7d : &f"+Utils.timeToString(stats.globalLast+timeToAdd)+"\n"
							+ "&cLocal:\n&e- Total    : &f"+Utils.timeToString(stats.local+timeToAdd)+"\n&e- Last 7d : &f"+Utils.timeToString(stats.localLast+timeToAdd));	
				}

				new BukkitRunnable() {
					@Override
					public void run() {
						sender.sendMessage(message);
					}
				}.runTask(instance);
			}
		});
		
		return true;
	}
}
