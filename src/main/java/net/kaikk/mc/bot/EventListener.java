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

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kaikk.mc.uuidprovider.UUIDProvider;

@SuppressWarnings("deprecation")
public class EventListener implements Listener {
	BetterOntime instance;
	
	EventListener(BetterOntime instance) {
		this.instance = instance;
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerLogin(PlayerLoginEvent event) {
		if (event.getResult()!=Result.ALLOWED) {
			return;
		}
		
		UUID uuid=UUIDProvider.get(event.getPlayer().getName());
		if (uuid==null) {
			instance.getLogger().severe(event.getPlayer().getName()+" UUID is null! I'll ignore this player.");
			return;
		}

		PlayerStats stats = instance.ds.getPlayerStatsFromDB(uuid);
		if (stats!=null) { // retrievePlayerStats returns null if there was a mysql exception... ignore this player in that case.
			try {
				stats.setPlayer(event.getPlayer());
			} catch (IllegalStateException e) {
				instance.getLogger().severe(event.getPlayer().getName()+" wasn't loaded: "+e.getMessage());
			}
		}
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		System.out.println("PlayerQuitEvent: "+event.getPlayer().getName());
		UUID uuid=UUIDProvider.get(event.getPlayer().getName());
		if (uuid==null) {
			instance.getLogger().severe(event.getPlayer().getName()+" UUID is null! I've ignored this player.");
			return;
		}

		// Remove from 
		PlayerStats stats = instance.ds.onlinePlayersStats.remove(uuid);
		
		// Add time since last add
		if (stats!=null && !stats.isAFK()) {
			int timeToAdd=Utils.epoch()-stats.lastEpochTime;
			instance.ds.addTime(uuid, timeToAdd);
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction()==Action.LEFT_CLICK_BLOCK || event.getAction()==Action.RIGHT_CLICK_BLOCK) {
			PlayerStats stats = instance.ds.getOnlinePlayerStats(event.getPlayer().getUniqueId());
			if (stats!=null && stats.lastBlockInteraction!=event.getClickedBlock()) {
				stats.lastInteraction=System.currentTimeMillis();
				stats.lastBlockInteraction=event.getClickedBlock();
			}
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
		PlayerStats stats = instance.ds.getOnlinePlayerStats(event.getPlayer().getUniqueId());
		if (stats!=null) {
			stats.lastInteraction=System.currentTimeMillis();
		}
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerChat(PlayerChatEvent event) {
		PlayerStats stats = instance.ds.getPlayerStats(event.getPlayer());
		if (stats!=null) {
			stats.lastInteraction=System.currentTimeMillis();
		}
	}
}
