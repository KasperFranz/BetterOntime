package net.kaikk.mc.bot;

import java.util.UUID;
import java.util.logging.Level;

import net.kaikk.mc.uuidprovider.UUIDProvider;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BetterOntime extends JavaPlugin {
	static BetterOntime instance;
	private Listener eventListener=new EventListener();
	private BukkitTask updateOntimeTask=null;
	protected Config config;
	protected DataStore ds;
	
	public void onEnable() {
		instance=this;
		
		try {
			this.load();
			
			CommandExec commandExec = new CommandExec();
			this.getCommand("betterontime").setExecutor(commandExec);
			this.getCommand("msgraw").setExecutor(commandExec);
			
			PluginManager pm = getServer().getPluginManager();
			
			pm.registerEvents(this.eventListener, this);
			
			this.updateOntimeTask = new UpdateOntimeTask().runTaskTimer(this, 600L, 600L);
			
			this.getLogger().info("BetterOntime loaded");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			instance=null;
		}
	}
	
	void load() throws Exception {
		this.config=new Config();
		this.getLogger().info("Loaded config: serverId = "+this.config.serverId);
		try {
			this.ds=new DataStore(this, config.dbUrl, config.dbUsername, config.dbPassword);
			
			for(Player player : BetterOntime.instance.getServer().getOnlinePlayers()) {
				UUID uuid=UUIDProvider.get(player);
				if (uuid!=null) {
					PlayerStats stats = BetterOntime.instance.ds.retrievePlayerStats(uuid);
					if (stats!=null) {
						BetterOntime.instance.ds.playersStats.put(uuid, stats);
					}
				}
			}
			
			this.getLogger().info("Loaded "+this.ds.commands.size()+" commands.");
		} catch (Exception e) {
			throw e;
		}
	}
	
	public void onDisable() {
		this.updateOntimeTask.cancel();
		new UpdateOntimeTask().run();
		this.getLogger().info("BetterOntime unloaded");
	}
	
	void log (String msg) {
		getLogger().info(msg);
	}
	
	void log (Level level, String msg) {
		getLogger().log(level, msg);
	}
}
