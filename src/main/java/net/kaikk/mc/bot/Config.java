package net.kaikk.mc.bot;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

class Config {
	final static String configFilePath = "plugins" + File.separator + "BetterOntime" + File.separator + "config.yml";
	private File configFile;
	FileConfiguration config;
	
	int serverId;
	String dbUrl;
	String dbUsername;
	String dbPassword;
	
	Config() {
		this.configFile = new File(configFilePath);
		this.config = YamlConfiguration.loadConfiguration(this.configFile);
		this.load();
	}
	
	void load() {
		this.serverId=config.getInt("serverId", 0);
		this.dbUrl=config.getString("dbUrl", "jdbc:mysql://127.0.0.1/betterontime");
		this.dbUsername=config.getString("dbUsername", "betterontime");
		this.dbPassword=config.getString("dbPassword", "");
		
		this.save();
	}
	
	void save() {
		try {
			this.config.set("serverId", this.serverId);
			this.config.set("dbUrl", this.dbUrl);
			this.config.set("dbUsername", this.dbUsername);
			this.config.set("dbPassword", this.dbPassword);

			this.config.save(this.configFile);
		} catch (IOException e) {
			BetterOntime.instance.log(Level.SEVERE, "Couldn't create or save config file.");
			e.printStackTrace();
		}
	}
}