package net.kaikk.mc.bot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.kaikk.mc.uuidprovider.UUIDProvider;

class DataStore {
	private BetterOntime instance;
	private String dbUrl;
	private String username;
	private String password;
	protected Connection db = null;
	
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	
	List<StoredCommand> commands = new CopyOnWriteArrayList<StoredCommand>();
	
	Map<UUID,PlayerStats> onlinePlayersStats = new ConcurrentHashMap<UUID,PlayerStats>(8, 1f, 1);
	
	DataStore(final BetterOntime instance) throws Exception {
		this.instance=instance;
		this.dbUrl = "jdbc:mysql://"+instance.config.dbHostname+"/"+instance.config.dbDatabase;
		this.username = instance.config.dbUsername;
		this.password = instance.config.dbPassword;
		
		try {
			//load the java driver for mySQL
			Class.forName("com.mysql.jdbc.Driver");
		} catch(Exception e) {
			this.instance.getLogger().severe("Unable to load Java's mySQL database driver.  Check to make sure you've installed it properly.");
			throw e;
		}
		
		try {
			this.dbCheck();
		} catch(Exception e) {
			this.instance.getLogger().severe("Unable to connect to database.  Check your config file settings. Details: \n"+e.getMessage());
			throw e;
		}
		
		Statement statement = db.createStatement();

		try {
			// Creates tables on the database
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS commands ("
					+ "  id int(11) NOT NULL AUTO_INCREMENT,"
					+ "  server tinyint(4) NOT NULL,"
					+ "  time int(11) NOT NULL,"
					+ "  repeated tinyint(1) NOT NULL,"
					+ "  command varchar(120) NOT NULL,"
					+ "  PRIMARY KEY (id)"
					+ ") AUTO_INCREMENT=1;");
				
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS playerinfo ("
					+ "  player binary(16) NOT NULL COMMENT 'uuid',"
					+ "  server tinyint(4) NOT NULL,"
					+ "  lastglobalplaytime int(11) NOT NULL,"
					+ "  PRIMARY KEY (server,player));");
			
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS playtimes ("
					+ "  player binary(16) NOT NULL COMMENT 'uuid',"
					+ "  server tinyint(3) unsigned NOT NULL,"
					+ "  day smallint(6) NOT NULL,"
					+ "  playtime int(10) unsigned NOT NULL,"
					+ "  PRIMARY KEY (player,server,day),"
					+ "  KEY server (server));");
		} catch(Exception e) {
			this.instance.getLogger().severe("Unable to create the necessary database table. Details: \n"+e.getMessage());
			throw e;
		}
		
		if (instance.config.serverId==0) {
			instance.config.serverId = this.newServer();
			instance.getConfig().set("ServerId", instance.config.serverId);
			instance.saveConfig();
		}
		
		ResultSet results = statement.executeQuery("SELECT player, playtime FROM playtimes WHERE server = "+instance.config.serverId+" AND day!=0 AND day < "+(Utils.daysFromEpoch()-instance.config.compactDataDays));
		
		HashMap<UUID, Integer> timesToMerge = new HashMap<UUID, Integer>();
		while (results.next()) {
			UUID uuid = Utils.toUUID(results.getBytes(1));
			Integer time = timesToMerge.get(uuid);
			if (time==null) {
				time=0;
			}
			
			timesToMerge.put(uuid, time+results.getInt(2));
		}
		
		statement.executeUpdate("DELETE FROM playtimes WHERE server = "+instance.config.serverId+" AND day!=0 AND day < "+(Utils.daysFromEpoch()-instance.config.compactDataDays));
		
		if (!timesToMerge.isEmpty()) {
			for (Entry<UUID, Integer> entry : timesToMerge.entrySet()) {
				statement.executeUpdate("INSERT INTO playtimes VALUES ("+Utils.UUIDtoHexString(entry.getKey())+", "+instance.config.serverId+", 0, "+entry.getValue()+") ON DUPLICATE KEY UPDATE playtime=playtime+"+entry.getValue());
			}
		}
		
		results = statement.executeQuery("SELECT id, time, repeated, command FROM commands WHERE server = "+instance.config.serverId+" OR server = 0 ORDER BY id");
		while (results.next()) {
			this.commands.add(new StoredCommand(results.getInt(1), results.getInt(2), results.getBoolean(3), results.getString(4)));
		}
		
		// Load online players to stats
		new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : instance.getServer().getOnlinePlayers()) {
					UUID uuid=UUIDProvider.get(player.getName());
					if (uuid==null) {
						instance.getLogger().severe(player+" UUID is null! I'll ignore this player.");
						continue;
					}
					
					PlayerStats stats = instance.ds.getPlayerStatsFromDB(uuid);
					if (stats!=null) { // retrievePlayerStats returns null if there was a mysql exception... ignore this player in that case.
						stats.setPlayer(player);
					}
				}
			}
		}.runTaskLater(instance, 1L);
		
	}
	
	void asyncUpdate(List<String> sql) {
		String[] arr = new String[(sql.size())];
		asyncUpdate(sql.toArray(arr));
	}

	void asyncUpdate(String... sql) {
		executor.execute(new DatabaseUpdate(sql));
	}
	
	Future<ResultSet> asyncQuery(String sql) {
		return executor.submit(new DatabaseQuery(sql));
	}
	
	Future<ResultSet> asyncUpdateGenKeys(String sql) {
		return executor.submit(new DatabaseUpdateGenKeys(sql));
	}
	
	synchronized void update(String... sql) throws SQLException {
		this.update(this.statement(), sql);
	}
	
	synchronized void update(Statement statement, String... sql) throws SQLException {
		for (String sqlRow : sql) {
			statement.executeUpdate(sqlRow);
		}
	}
	
	synchronized ResultSet query(String sql) throws SQLException {
		return this.query(this.statement(), sql);
	}
	
	synchronized ResultSet query(Statement statement, String sql) throws SQLException {
		return statement.executeQuery(sql);
	}
	
	synchronized ResultSet updateGenKeys(String sql) throws SQLException {
		return this.updateGenKeys(this.statement(), sql);
	}
	
	synchronized ResultSet updateGenKeys(Statement statement, String sql) throws SQLException {
		statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
		return statement.getGeneratedKeys();
	}
	
	void leaderboard(final CommandSender sender) {
		final Future<ResultSet> futureResult = instance.ds.asyncQuery("SELECT player, SUM(playtime) FROM playtimes GROUP BY player ORDER BY SUM(playtime) DESC LIMIT 10;");
		executor.execute(new Runnable() {
			@Override
			public void run() {
				// Asynchronously wait for result
				try {
					ResultSet result = futureResult.get();

					// Generate leaderboard
					final StringBuilder sb = new StringBuilder(ChatColor.translateAlternateColorCodes('&', "&6==------ &3BetterOntime Leaderboard &6------==\n"));
					
					int i = 1;
					while(result.next()) {
						try {
							String playerName = UUIDProvider.get(Utils.toUUID(result.getBytes(1)));
							
							sb.append(ChatColor.translateAlternateColorCodes('&', "&3"+i+"- &a"+playerName+": &2"+Utils.timeToString(result.getInt(2)))+"\n");
						} catch (Exception e) {
							e.printStackTrace();
						}
						i++;
					}

					sb.append(ChatColor.translateAlternateColorCodes('&', "&6==-----------------------------------=="));

					new BukkitRunnable() {
						@Override
						public void run() {
							// Bukkit synchronous add command and send reply to player
							sender.sendMessage(sb.toString());
						}
					}.runTask(instance);
				} catch (InterruptedException | ExecutionException | SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}
	
	synchronized void addTime(UUID playerId, int time) {
		addTime(playerId, time, instance.config.serverId);
	}
	
	synchronized void addTime(PlayerStats stats, int time) {
		addTime(stats, time, instance.config.serverId);
	}
	
	synchronized void addTime(UUID playerId, int time, int server) {
		addTime(playerId, time, server, Utils.daysFromEpoch());
	}
	
	synchronized void addTime(PlayerStats stats, int time, int server) {
		addTime(stats, time, server, Utils.daysFromEpoch());
	}
	
	synchronized void addTime(UUID playerId, int time, int server, int day) {
		PlayerStats stats = this.onlinePlayersStats.get(playerId);
		if (stats==null) {
			stats = new PlayerStats(playerId);
		}
		
		addTime(stats, time, server, day);
	}
	
	synchronized void addTime(PlayerStats stats, int time, int server, int day) {
		this.asyncUpdate(Utils.addTimeSql(stats.uuid, time, server, day));
		stats.addTime(time);
	}
	
	synchronized void setTime(UUID playerId, int time) {
		this.asyncUpdate("DELETE FROM playtimes WHERE player = "+Utils.UUIDtoHexString(playerId),
						"INSERT INTO playtimes VALUES ("+Utils.UUIDtoHexString(playerId)+", 0, 0, "+time+")");
	}
	
	/** Gets the player stats from the internal cache, or request  <br>
	 * @return a PlayerStats object for this player, null if the player's UUID couldn't be retrieved */
	public PlayerStats getPlayerStats(UUID uuid) {
		PlayerStats playerStats = this.getOnlinePlayerStats(uuid);
		if (playerStats!=null) {
			return playerStats;
		}
		
		return getPlayerStatsFromDB(uuid);
	}
	
	/** Warning: Use getPlayerStats(UUID) when possible as this requests the UUID to UUIDProvider<br>
	 * Gets the player stats from the internal cache, or request  <br>
	 * @return a PlayerStats object for this player, null if the player's UUID couldn't be retrieved */
	public PlayerStats getPlayerStats(OfflinePlayer player) {
		if (player.isOnline()) {
			return getOnlinePlayerStats(player.getPlayer());
		}
		
		UUID uuid = UUIDProvider.get(player.getName());
		if (uuid==null) {
			return null;
		}
		return getPlayerStatsFromDB(uuid);
	}
	
	/** Warning: Use getOnlinePlayerStats(UUID) when possible as this requests the UUID to UUIDProvider */
	public PlayerStats getOnlinePlayerStats(Player player) {
		UUID uuid = UUIDProvider.get(player.getName());
		if (uuid==null) {
			return null;
		}
		return this.getOnlinePlayerStats(uuid);
	}
	
	/** Returns players stats from local cache, available if they're online. */
	public PlayerStats getOnlinePlayerStats(UUID playerId) {
		return instance.ds.onlinePlayersStats.get(playerId);
	}
	
	/** Retrieve player stats from the database */
	synchronized public PlayerStats getPlayerStatsFromDB(UUID playerId) {
		PlayerStats stats = new PlayerStats(playerId);
		
		try {
			Statement statement = this.statement();
			
			ResultSet results = this.query(statement, "SELECT server, day, playtime FROM playtimes WHERE player = "+Utils.UUIDtoHexString(playerId));
			
			int daysFromEpoch=Utils.daysFromEpoch(), thisServerId=instance.config.serverId, playtime, serverId;
			
			while(results.next()) {
				serverId=results.getInt(1);
				playtime=results.getInt(3);
				
				if(results.getInt(2)>=daysFromEpoch-7) {
					if (serverId==thisServerId) {
						stats.localLast+=playtime;
					}
					stats.globalLast+=playtime;
				}
				
				if (serverId==thisServerId) {
					stats.local+=playtime;
				}
				stats.global+=playtime;
			}
			
			results = this.query(statement, "SELECT lastglobalplaytime FROM playerinfo WHERE player = "+Utils.UUIDtoHexString(playerId)+" AND server="+thisServerId);
			
			if(results.next()) {
				stats.lastGlobalCheck=results.getInt(1);
			}
			
			stats.lastLocalCheck=stats.local;
			
			stats.lastEpochTime=Utils.epoch();
			
			return stats;
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	Statement statement() throws SQLException {
		this.dbCheck();
		return this.db.createStatement();
	}
	
	synchronized public int newServer() {
		try {
			ResultSet results = statement().executeQuery("SELECT server FROM playtimes ORDER BY server DESC LIMIT 1");
			
			int serverId=1;
			
			if (results.next()) {
				serverId = results.getInt(1)+1;
			}
			
			this.update("INSERT IGNORE INTO playtimes VALUES(0x0,"+serverId+",0,0)");
			
			return serverId;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	synchronized void dbCheck() throws SQLException {
		if(this.db == null || this.db.isClosed()) {
			Properties connectionProps = new Properties();

			connectionProps.put("user", this.username);
			connectionProps.put("password", this.password);
			
			this.db = DriverManager.getConnection(this.dbUrl, connectionProps); 
		}
	}
	
	synchronized void dbClose()  {
		try {
			if (!this.db.isClosed()) {
				this.db.close();
				this.db=null;
			}
		} catch (SQLException e) {
			
		}
	}
	
	private class DatabaseUpdate implements Runnable {
		private String[] sql;
		
		public DatabaseUpdate(String... sql) {
			this.sql = sql;
		}

		@Override
		public void run() {
			try {
				for (String sql : this.sql) {
					if (sql==null) {
						break;
					}
					update(sql);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	private class DatabaseUpdateGenKeys implements Callable<ResultSet> {
		private String sql;
		
		public DatabaseUpdateGenKeys(String sql) {
			this.sql = sql;
		}
		
		@Override
		public ResultSet call() throws Exception {
			return updateGenKeys(sql);
		}
		
	}
	
	private class DatabaseQuery implements Callable<ResultSet> {
		private String sql;
		
		public DatabaseQuery(String sql) {
			this.sql = sql;
		}
		
		@Override
		public ResultSet call() throws Exception {
			return query(sql);
		}
		
	}
}
