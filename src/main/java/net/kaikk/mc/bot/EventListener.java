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

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {
	@EventHandler(priority=EventPriority.NORMAL)
	public void onPlayerLogin(PlayerLoginEvent event) {
		//BetterOntime.instance.getLogger().info("BOT oPL");
		UUID uuid=UUIDProvider.get(event.getPlayer());
		if (uuid!=null) {
			PlayerStats stats = BetterOntime.instance.ds.retrievePlayerStats(uuid);
			if (stats!=null) {
				BetterOntime.instance.ds.playersStats.put(uuid, stats);
			}
		}
	}
	
	@EventHandler(priority=EventPriority.NORMAL)
	public void onPlayerQuit(PlayerQuitEvent event) {
		UUID uuid=UUIDProvider.get(event.getPlayer());
		//BetterOntime.instance.getLogger().info(event.getPlayer().getName()+" logs out");
		if (uuid==null) {
			BetterOntime.instance.getLogger().severe(event.getPlayer().getName()+" UUID is null!");
			return;
		}
		
		PlayerStats stats = BetterOntime.instance.ds.playersStats.remove(uuid);

		if (stats!=null&&stats.lastEpochTime!=0) {
			int timeToAdd=DataStore.epoch()-stats.lastEpochTime;
			//BetterOntime.instance.getLogger().info(event.getPlayer().getName()+" logs out -> time to add: "+timeToAdd);
			BetterOntime.instance.ds.addTime(uuid, timeToAdd);
		}
	}
}
