package net.kaikk.mc.bot;

import org.bukkit.plugin.java.JavaPlugin;

public class Config {
	public int serverId, inactivityMinutes, saveDataInterval;
	public String dbHostname, dbUsername, dbPassword, dbDatabase;
	
	Config(JavaPlugin instance) {
		instance.getConfig().options().copyDefaults(true);
		instance.saveDefaultConfig();
		
		this.serverId=instance.getConfig().getInt("ServerId", 0);
		
		this.inactivityMinutes=instance.getConfig().getInt("InactiveAfterMins", 5);
		this.saveDataInterval=instance.getConfig().getInt("SaveDataInterval");
		this.dbHostname=instance.getConfig().getString("MySQL.Hostname");
		this.dbUsername=instance.getConfig().getString("MySQL.Username");
		this.dbPassword=instance.getConfig().getString("MySQL.Password");
		this.dbDatabase=instance.getConfig().getString("MySQL.Database");
	}
}
