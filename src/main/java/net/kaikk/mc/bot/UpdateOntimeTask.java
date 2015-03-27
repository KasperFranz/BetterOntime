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
						if (timeToAdd>180) {
							BetterOntime.instance.getLogger().warning(player.getName()+"'s timeToAdd ("+timeToAdd+" seconds) ignored and added just 30 seconds.");
							timeToAdd=30;
						}

						BetterOntime.instance.ds.addTime(uuid, timeToAdd);

						boolean executedGlobalCommand=false;
						for(StoredCommand command : BetterOntime.instance.ds.commands) {
							if (isTimeToRunCommand(command, stats) && !player.hasPermission("betterontime.exclude."+command.id)) {
								String commandToRun=command.command.replace("{p.name}", player.getName()).replace("{p.uuid}", uuid.toString());
								
								try {
									BetterOntime.instance.getLogger().info("Run command id "+command.id+": "+commandToRun);
									String[] commandsToRun = commandToRun.split("@n@");
									for (String cmdToRun : commandsToRun) {
										BetterOntime.instance.getServer().dispatchCommand(BetterOntime.instance.getServer().getConsoleSender(), cmdToRun);
									}
									if (!command.repeated) {
										executedGlobalCommand=true;
									}
								} catch (CommandException e) {
									BetterOntime.instance.getLogger().info("An error occurred while running command id "+command.id+": "+commandToRun);
									e.printStackTrace();
								}
							}
						}
						
						if (executedGlobalCommand) {
							BetterOntime.instance.ds.setLastExecutedCommand(uuid, stats.global, BetterOntime.instance.config.serverId);
						} else {
							stats.lastGlobalCheck=stats.global;
						}
						stats.lastLocalCheck=stats.local;
					}
				}
			}
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
			if (stats.lastGlobalCheck<command.time && command.time<stats.global) {
				return true;
			}
		}
		return false;
	}
}
