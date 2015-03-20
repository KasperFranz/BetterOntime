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
