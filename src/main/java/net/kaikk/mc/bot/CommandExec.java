package net.kaikk.mc.bot;

import java.util.ArrayList;
import java.util.UUID;

import net.kaikk.mc.uuidprovider.UUIDProvider;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandExec implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Player player = null;
		if (sender instanceof Player) {
			player = (Player) sender;
		}
		
		if (cmd.getName().equalsIgnoreCase("betterontime")) {
			if (args.length!=0) {
				if (args[0].equalsIgnoreCase("info")) {
					if (!sender.hasPermission("betterontime.manage")) {
						sender.sendMessage("You're not allowed to run this command.");
						return false;
					}
					
					sender.sendMessage("BetterOntime serverId: "+BetterOntime.instance.config.serverId);
					return true;
				}
				
				if (args[0].equalsIgnoreCase("reload")) {
					if (!sender.hasPermission("betterontime.manage")) {
						sender.sendMessage("You're not allowed to run this command.");
						return false;
					}
					
					sender.sendMessage("Reloading BetterOntime... prepare for unforeseen consequences!");

					try {
						BetterOntime.instance.load();
						sender.sendMessage("BetterOntime reloaded!");
					} catch (Exception e) {
						e.printStackTrace();
						sender.sendMessage("BetterOntime reload error!");
					}

					return true;
				}
				if (args[0].equalsIgnoreCase("help")) {
					sender.sendMessage("Aliases: betterontime, bot, ontime\n"
							+ "- shows your statistics\n"
							+ (sender.hasPermission("betterontime.others") ? "- [name] - checks the player's playtime\n" : "")
							+ (sender.hasPermission("betterontime.leaderboard") ? "- shows the leaderboard\n" : "")
							+ (sender.hasPermission("betterontime.manage") ? "- add [name] [time] - add playtime to player's statistics\n"
							+ "- cmd - manages commands\n"
							+ "- reload - reloads data\n" : ""));
					return true;
				}
				
				if (args[0].equalsIgnoreCase("leaderboard")) {
					if (!sender.hasPermission("betterontime.leaderboard")) {
						sender.sendMessage("You're not allowed to run this command.");
						return false;
					}
					
					ArrayList<Leaderboard> leaderboard = BetterOntime.instance.ds.getLeaderboard();
					
					sender.sendMessage(Color.RED + "==------ BetterOntime Leaderboard ------==");
					
					int i=1;
					for (Leaderboard stats : leaderboard) {
						sender.sendMessage(i+"- "+stats.getName()+": "+timeToString(stats.time));
						i++;
					}
					
					return true;
				}
				
				if (args[0].equalsIgnoreCase("cmd")) {
					if (!sender.hasPermission("betterontime.manage")) {
						sender.sendMessage("You're not allowed to run this command.");
						return false;
					}
					
					if (args.length==1) {
						sender.sendMessage("Usage: /betterontime cmd [list|add|remove]");
						return false;
					}
					
					if (args[1].equalsIgnoreCase("list")) {
						if (BetterOntime.instance.ds.commands.isEmpty()) {
							sender.sendMessage("No commands found for this server.");
							return true;
						}
						sender.sendMessage(ChatColor.RED + "BetterOntime commands list:");
						for(StoredCommand command : BetterOntime.instance.ds.commands) {
							sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&3("+command.id+") "+(command.repeated?"Every ":"At &2")+timeToString(command.time)+"\n &c>&f "+command.command));
						}
						return true;
					} else if (args[1].equalsIgnoreCase("add")) {
						if (args.length<5) {
							sender.sendMessage("Usage: /betterontime cmd add [time] [repeated: true|false] [command]");
							return false;
						}
	
						Integer secs = stringToTime(args[2]);
						if (secs==null) {
							sender.sendMessage("Usage: /betterontime cmd add [time] [repeated: true|false] [command]");
							return false;
						}
						
						if (secs<1) {
							sender.sendMessage("Time must be greater than 0.");
							return false;
						}
						
						boolean repeated = args[3].equalsIgnoreCase("true") ? true : false;
						
						String stringCommand = mergeStringArrayFromIndex(args, 4);
						
						BetterOntime.instance.ds.addCommand(new StoredCommand(0, secs, repeated, stringCommand));
						sender.sendMessage("Command added.");
					} else if (args[1].equalsIgnoreCase("remove")) {
						if (args.length!=3) {
							sender.sendMessage("Usage: /betterontime cmd remove [id]");
							return false;
						}
	
						try {
							Integer id = Integer.valueOf(args[2]);
							
							BetterOntime.instance.ds.removeCommand(id);
							sender.sendMessage("Command removed.");
						} catch (NumberFormatException e) {
							sender.sendMessage("Wrong id.");
							return false;
						}
					}
					return true;
				}
				
				if (args[0].equalsIgnoreCase("add")) {
					if (!sender.hasPermission("betterontime.manage")) {
						sender.sendMessage("You're not allowed to run this command.");
						return false;
					}
	
					if (args.length!=3) {
						sender.sendMessage("Usage: /betterontime add [PlayerName] [time]"); 
						return false;
					}
					
					Integer time=stringToTime(args[2]);
					if (time==null||time<1) {
						sender.sendMessage("Invalid time.");
						return false;
					}
					
					OfflinePlayer targetPlayer=BetterOntime.instance.getServer().getPlayer(args[1]);
					
					if (targetPlayer==null) {
						sender.sendMessage("Invalid player name.");
						return false;
					}
					
					UUID uuid = UUIDProvider.get(targetPlayer);
					if (uuid==null) {
						sender.sendMessage("Invalid player name.");
						return false;
					}
					
					BetterOntime.instance.ds.addTime(uuid, time);
					sender.sendMessage("Added "+timeToString(time)+" to "+targetPlayer.getName()+"'s playtime.");
					return true;
				}
			}
			
			// Time check
			OfflinePlayer targetPlayer=player;
			if (args.length==1) {
				if (!sender.hasPermission("betterontime.others")) {
					sender.sendMessage("You're not allowed to run this command.");
					return false;
				}
				targetPlayer=BetterOntime.instance.getServer().getOfflinePlayer(args[0]);
				
				if (targetPlayer==null) {
					sender.sendMessage("Player "+args[0]+" not found.");
					return false;
				}
			} else if (!sender.hasPermission("betterontime.self")) {
				sender.sendMessage("You're not allowed to run this command.");
				return false;
			}
			
			PlayerStats stats = BetterOntime.instance.ds.getPlayerStats(targetPlayer);
			if (stats==null || stats.global==0) {
				sender.sendMessage("No statistics found for this player.");
				return false;
			}
			
			int timeToAdd=DataStore.epoch()-stats.lastEpochTime;
			
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6---- Statistics for "+targetPlayer.getName()+" ----\n"
					+ "&cGlobal:\n&e- Total    : &f"+timeToString(stats.global+timeToAdd)+"\n&e- Last 7d : &f"+timeToString(stats.globalLast+timeToAdd)+"\n"
					+ "&cLocal:\n&e- Total    : &f"+timeToString(stats.local+timeToAdd)+"\n&e- Last 7d : &f"+timeToString(stats.localLast+timeToAdd)));
			return true;
			
		}
		
		if (cmd.getName().equalsIgnoreCase("msgraw")) {
			if (!sender.isOp()) {
				sender.sendMessage("This is an OP only command.");
				return false;
			}
			
			if (args.length<2) {
				sender.sendMessage("Usage: /msgraw [PlayerName] [Raw Message]");
				return false;
			}
			
			Player targetPlayer = BetterOntime.instance.getServer().getPlayer(args[0]);
			if (targetPlayer==null) {
				sender.sendMessage("Invalid player");
				return false;
			}
			
			targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', mergeStringArrayFromIndex(args,1)));
			return true;
		}
		
		return false;
	}

	static String timeToString(int time) {
		ArrayList<String> strs = new ArrayList<String>();

		if (time<0) {
			time*=-1;
		}
		
		int secs = time % 60;
		if (secs!=0||time==0) {
			strs.add(secs+" second"+(secs!=1?"s":""));
		}
		
		if (time<60) {
			return mergeTimeStrings(strs);
		}
		
		int tmins = (time-secs) / 60;
		int mins = tmins % 60;
		
		if (mins!=0) {
			strs.add(mins+" minute"+(mins!=1?"s":""));
		}
		
		if (tmins<60) {
			return mergeTimeStrings(strs);
		}
		
		int thours = (tmins-mins) / 60;
		int hours = thours % 24;
		
		if (hours!=0) {
			strs.add(hours+" hour"+(hours!=1?"s":""));
		}
		
		if (thours<24) {
			return mergeTimeStrings(strs);
		}
		
		int tdays = (thours-hours) / 24;
		int days = tdays % 24;
		
		if (days!=0) {
			strs.add(days+" day"+(days!=1?"s":""));
		}
		
		return mergeTimeStrings(strs);
	}
	
	public static String mergeTimeStrings(ArrayList<String> strs) {
		String string="";
		for(String str : strs) {
			string=str+(string==""?"":" ")+string;
		}
		return string;
	}
	
	public static Integer stringToTime(String tString) {
		Integer time;
		tString=tString.replace(" ", "");
		
		try {
			time=Integer.valueOf(tString);
			return time;
		} catch (NumberFormatException e) {
			try {
				time=Integer.valueOf(tString.substring(0, tString.length()-1));
			} catch (NumberFormatException e1) {
				return null;
			}
			
			char unit = tString.charAt(tString.length()-1);

			switch(unit) {
			case 's':
				return time;
			case 'm':
				return time*60;
			case 'h':
				return time*3600;
			case 'd':
				return time*86400;
			default:
				return null;
			}
		}
	}
	
	/** This static method will merge an array of strings from a specific index 
	 * @return null if arrayString.length < i*/
	static String mergeStringArrayFromIndex(String[] arrayString, int i) {
		if (i<arrayString.length){
			String string=arrayString[i];
			i++;
			for(;i<arrayString.length;i++){
				string=string+" "+arrayString[i];
			}
			return string;
		}
		return null;
	}
}
