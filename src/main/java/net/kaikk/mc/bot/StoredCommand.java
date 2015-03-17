package net.kaikk.mc.bot;

class StoredCommand {
	int id, server, time;
	boolean repeated;
	String command;
	
	StoredCommand(int id, int time, boolean repeated, String command) {
		this.id=id;
		this.server = BetterOntime.instance.config.serverId;
		this.time = time;
		this.repeated = repeated;
		this.command = command;
	}
	
	StoredCommand(int id, int server, int time, boolean repeated, String command) {
		this.id=id;
		this.server = server;
		this.time = time;
		this.repeated = repeated;
		this.command = command;
	}
}
