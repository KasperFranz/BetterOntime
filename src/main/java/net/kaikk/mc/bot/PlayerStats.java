package net.kaikk.mc.bot;

public class PlayerStats {
	int local, localLast, global, globalLast, lastGlobalCheck, lastEpochTime;
	
	PlayerStats() {	}
	
	PlayerStats(int local, int localLast, int global, int globalLast, int lastGlobalCheck, int lastEpochTime) {
		this.local = local;
		this.localLast = localLast;
		this.global = global;
		this.globalLast = globalLast;
		this.lastGlobalCheck = lastGlobalCheck;
		this.lastEpochTime = lastEpochTime;
	}
}
