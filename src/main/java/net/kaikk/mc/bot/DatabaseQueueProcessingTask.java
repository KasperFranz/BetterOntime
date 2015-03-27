package net.kaikk.mc.bot;

import java.sql.Statement;

import org.bukkit.scheduler.BukkitRunnable;

public class DatabaseQueueProcessingTask extends BukkitRunnable {
	BetterOntime instance;
	
	DatabaseQueueProcessingTask(BetterOntime instance) {
		this.instance=instance;
	}
	
	@Override
	public void run() {
		String sql;
		while((sql=BetterOntime.instance.ds.dbQueue.poll())!=null) {
			try {
				BetterOntime.instance.ds.dbCheck();
				Statement statement = BetterOntime.instance.ds.db.createStatement();
				statement.executeUpdate(sql);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		new DatabaseQueueProcessingTask(this.instance).runTaskLaterAsynchronously(this.instance, 20L);
	}
}
