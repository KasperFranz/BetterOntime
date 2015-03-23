package net.kaikk.mc.bot;

import java.util.UUID;

import net.kaikk.mc.uuidprovider.UUIDProvider;

import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

class UpdateOntimeTask extends BukkitRunnable {
	@Override
	public void run() {
		if (BetterOntime.instance!=null && BetterOntime.instance.ds!=null) {
			for(Player player : BetterOntime.instance.getServer().getOnlinePlayers()) {
				UUID uuid = UUIDProvider.get(player);
				if (uuid!=null){
					PlayerStats stats = BetterOntime.instance.ds.getPlayerStats(uuid);
					
					if (stats!=null) {
						if (stats.lastEpochTime==0) {
							stats.lastEpochTime=DataStore.epoch();
						}
						
						int timeToAdd=DataStore.epoch()-stats.lastEpochTime;
						if (timeToAdd>60) {
							// DEBUG
							BetterOntime.instance.getLogger().info("BOTWARNING "+player.getName()+" timeToAdd "+timeToAdd+" -> "+DataStore.epoch()+"-"+stats.lastEpochTime);
							BetterOntime.instance.getServer().dispatchCommand(BetterOntime.instance.getServer().getConsoleSender(), "w KaiNoMood BW "+BetterOntime.instance.config.serverId+" "+player.getName()+" add "+timeToAdd+" | "+DataStore.epoch()+"-"+stats.lastEpochTime);
							timeToAdd=30;
						}

						BetterOntime.instance.ds.addTime(uuid, timeToAdd);

						boolean executedCommand=false;
						for(StoredCommand command : BetterOntime.instance.ds.commands) {
							if (isTimeToRunCommand(command, stats) && !player.hasPermission("betterontime.exclude."+command.id) && !player.hasPermission("betterontime.exclude.all")) {
								String commandToRun=command.command.replace("{p.name}", player.getName()).replace("{p.uuid}", uuid.toString());
								
								try {
									BetterOntime.instance.getLogger().info("Run command id "+command.id+": "+commandToRun);
									BetterOntime.instance.getServer().dispatchCommand(BetterOntime.instance.getServer().getConsoleSender(), commandToRun);
									executedCommand=true;
								} catch (CommandException e) {
									BetterOntime.instance.getLogger().info("An error occurred while running command id "+command.id+": "+commandToRun);
									e.printStackTrace();
								}
							}
						}
						
						if (executedCommand || stats.lastGlobalCheck+600<stats.global) {
							BetterOntime.instance.ds.setLastExecutedCommand(uuid, stats.global, BetterOntime.instance.config.serverId);
						} else {
							stats.lastGlobalCheck=stats.global;
						}
					}
				}
			}
		}
	}
	
	private boolean isTimeToRunCommand(StoredCommand command, PlayerStats stats) {
		if (command.repeated) {
			if (stats.lastGlobalCheck==0) {
				return false;
			}
			
			int t = stats.lastGlobalCheck;
			//BetterOntime.instance.getLogger().info("BOT-DEBUG: repeated- "+t+"<"+stats.global+" - "+t+"%"+command.time);
			while(t<stats.global) {
				if (t%command.time==0) {
					return true;
				}
				t++;
			}
		} else {
			//BetterOntime.instance.getLogger().info("BOT-DEBUG: one time- "+stats.lastGlobalCheck+"<"+command.time+" && "+command.time+"<"+stats.global);
			if (stats.lastGlobalCheck<command.time && command.time<stats.global) {
				return true;
			}
		}
		return false;
	}
}
