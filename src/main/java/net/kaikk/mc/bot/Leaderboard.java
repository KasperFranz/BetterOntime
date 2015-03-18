package net.kaikk.mc.bot;

import java.util.UUID;

import net.kaikk.mc.uuidprovider.UUIDProvider;

import org.bukkit.OfflinePlayer;

class Leaderboard {
	UUID uuid;
	int time;
	
	Leaderboard(UUID uuid, int time) {
		this.uuid=uuid;
		this.time=time;
	}
	
	String getName() {
		OfflinePlayer player = UUIDProvider.get(uuid);
		if (player==null) {
			return null;
		}
		
		return player.getName();
	}
}
