package net.kaikk.mc.bot;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.scheduler.BukkitRunnable;

/** Updates players playtime. It's thread-safe. */
class UpdateTask extends BukkitRunnable {
	final BetterOntime instance;
	
	UpdateTask(BetterOntime instance) {
		this.instance = instance;
	}

	@Override
	public void run() {
		// List of commands to run on the server thread
		final List<String> commandsToRun = new ArrayList<String>();
		final List<String> sqlCache = new ArrayList<String>(instance.ds.onlinePlayersStats.size());
		
		//System.out.println("UpdateTask: running. onlinePlayersStats size: "+instance.ds.onlinePlayersStats.size());
		for(PlayerStats stats : instance.ds.onlinePlayersStats.values()) {
			//System.out.println("UpdateTask: checking "+stats.getPlayerName());
			
			if (stats.isAFK()) {
				stats.lastEpochTime=Utils.epoch();
				//System.out.println("UpdateTask: "+stats.getPlayerName()+" is afk");
				continue;
			}
			
			int timeToAdd=Utils.epoch()-stats.lastEpochTime;
			if (timeToAdd>(instance.config.saveDataInterval*2)+60) {
				instance.getLogger().warning(stats.getPlayerName()+"'s timeToAdd ("+timeToAdd+" seconds) ignored and added just "+instance.config.saveDataInterval+" seconds. Is the server heavily overloaded?");
				timeToAdd=instance.config.saveDataInterval;
			}
			
			//System.out.println("UpdateTask: "+stats.getPlayerName()+" adding "+timeToAdd);
			
			// add the add time sql query to the cache
			sqlCache.add(Utils.addTimeSql(stats.uuid, timeToAdd, instance.config.serverId, Utils.daysFromEpoch()));

			// add time to the local stats (this doesn't update the database)
			stats.addTime(timeToAdd);

			boolean executedGlobalCommand=false;
			for(StoredCommand command : instance.ds.commands) {
				/*if (stats.excludedCommands.contains(command)) {
					System.out.println("command id "+command.id+" is excluded for "+stats.getPlayerName());
				}*/
				if (!stats.excludedCommands.contains(command) && isTimeToRunCommand(command, stats)) {
					String commandToRun=command.command.replace("{p.name}", stats.getPlayerName()).replace("{p.uuid}", stats.uuid.toString());

					String[] lCommandsToRun = commandToRun.split("@n@");
					for (String cmdToRun : lCommandsToRun) {
						commandsToRun.add(cmdToRun);
					}
					
					if (!command.repeated) {
						executedGlobalCommand=true;
					}
				}
			}
			
			if (executedGlobalCommand) {
				instance.ds.asyncUpdate("INSERT INTO playerinfo VALUES ("+Utils.UUIDtoHexString(stats.uuid)+", "+instance.config.serverId+", "+stats.global+") ON DUPLICATE KEY UPDATE lastglobalplaytime = "+stats.global);
			}
			
			stats.lastGlobalCheck=stats.global;
			stats.lastLocalCheck=stats.local;
		}
		
		// execute sql queries
		instance.ds.asyncUpdate(sqlCache);
		
		if (!commandsToRun.isEmpty()) {
			// stuff to run on the server thread
			new BukkitRunnable() {
				@Override
				public void run() {
					for(String cmd : commandsToRun) {
						try {
							instance.getLogger().info("> "+cmd);
							instance.getServer().dispatchCommand(instance.getServer().getConsoleSender(), cmd);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}.runTask(instance);
		}
	}
	
	private boolean isTimeToRunCommand(StoredCommand command, PlayerStats stats) {
		if (command.repeated) {
			if (stats.lastLocalCheck==0) {
				return false;
			}
			
			int t = stats.lastLocalCheck;
			
			while(t<stats.local) {
				if (t%command.time==0) {
					return true;
				}
				t++;
			}
		} else {
			//System.out.println("IsTimeToRunId"+command.id+": "+stats.lastGlobalCheck+"<"+command.time+" && "+command.time+"<"+stats.global);
			if (stats.lastGlobalCheck<command.time && command.time<stats.global) {
				return true;
			}
		}
		return false;
	}
}
