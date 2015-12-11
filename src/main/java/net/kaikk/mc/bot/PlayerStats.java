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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class PlayerStats {
	Player player;
	UUID uuid;

	int local, localLast, global, globalLast, lastGlobalCheck, lastLocalCheck, lastEpochTime;
	long lastInteraction=System.currentTimeMillis();
	Block lastBlockInteraction;

	List<StoredCommand> excludedCommands = Collections.emptyList();

	PlayerStats(UUID uuid) {
		this.uuid = uuid;
	}

	PlayerStats(UUID uuid, int local, int localLast, int global, int globalLast, int lastGlobalCheck, int lastLocalCheck, int lastEpochTime) {
		this.uuid = uuid;
		this.local = local;
		this.localLast = localLast;
		this.global = global;
		this.globalLast = globalLast;
		this.lastGlobalCheck = lastGlobalCheck;
		this.lastLocalCheck = lastLocalCheck;
		this.lastEpochTime = lastEpochTime;
	}

	public boolean isPlayerOnline() {
		if (player==null) {
			return false;
		}

		return player.isOnline();
	}

	public boolean isAFK() {
		return System.currentTimeMillis()-this.lastInteraction>BetterOntime.instance().config.inactivityMinutes*60000L;
	}

	public String getPlayerName() {
		if (player==null || player.getName()==null) {
			return "(UUID:"+uuid+")";
		}

		return player.getName();
	}

	/** Add time to players stats. This doesn't update the database. */
	void addTime(int time) {
		this.localLast+=time;
		this.globalLast+=time;
		this.local+=time;
		this.global+=time;
		this.lastEpochTime=Utils.epoch();
	}

	/** Set the player and add this object to the players updated by the Update Task */
	void setPlayer(Player player) {
		this.player=player;
		this.calculateCommandsExclusions();
		BetterOntime.instance().ds.onlinePlayersStats.put(uuid, this);
	}

	/** Calculate commands exclusions with permissions. The player must be set. Thread-unsafe. */
	void calculateCommandsExclusions() {
		if (this.player==null) {
			throw new IllegalStateException("You must set the player object before calling this method.");
		}

		if (!this.player.hasPermission("betterontime.log")) {
			throw new IllegalStateException("The player doesn't have the betterontime.log permission.");
		}

		List<StoredCommand> scList = new ArrayList<StoredCommand>();

		for(StoredCommand cmd : BetterOntime.instance().ds.commands) {
			if (Utils.hasExplicitPermission(this.player, "betterontime.exclude."+cmd.id)) {
				scList.add(cmd);
			}
		}

		if (!scList.isEmpty()) {
			this.excludedCommands=Collections.unmodifiableList(scList);
		}
	}
}
