package net.kaikk.mc.bot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import net.kaikk.mc.uuidprovider.UUIDProvider;

import org.bukkit.OfflinePlayer;


class DataStore {
	private BetterOntime instance;
	private String dbUrl;
	private String username;
	private String password;
	protected Connection db = null;

	ConcurrentHashMap<UUID,PlayerStats> playersStats = new ConcurrentHashMap<UUID,PlayerStats>();
	ArrayList<StoredCommand> commands = new ArrayList<StoredCommand>();
	
	DataStore(BetterOntime instance, String url, String username, String password) throws Exception {
		this.instance=instance;
		this.dbUrl = url;
		this.username = username;
		this.password = password;
		
		try {
			//load the java driver for mySQL
			Class.forName("com.mysql.jdbc.Driver");
		} catch(Exception e) {
			this.instance.log(Level.SEVERE, "Unable to load Java's mySQL database driver.  Check to make sure you've installed it properly.");
			throw e;
		}
		
		try {
			this.dbCheck();
		} catch(Exception e) {
			this.instance.log(Level.SEVERE, "Unable to connect to database.  Check your config file settings.");
			throw e;
		}
		
		
		try {
			Statement statement = db.createStatement();

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
			this.instance.log(Level.SEVERE, "Unable to create the necessary database tables. Details:");
			throw e;
		}

		Config config = BetterOntime.instance.config;
		if (config.serverId==0) {
			config.serverId = this.newServer();
			config.save();
		}
		
		this.dbCheck();
		
		Statement statement = this.db.createStatement();
		ResultSet results = statement.executeQuery("SELECT player, playtime FROM playtimes WHERE server = "+config.serverId+" AND day!=0 AND day < "+(daysFromEpoch()-7));
		
		HashMap<UUID, Integer> timesToMerge = new HashMap<UUID, Integer>();
		while (results.next()) {
			UUID uuid = toUUID(results.getBytes(1));
			Integer time = timesToMerge.get(uuid);
			if (time==null) {
				time=0;
			}
			
			timesToMerge.put(uuid, time+results.getInt(2));
		}
		
		statement.executeUpdate("DELETE FROM playtimes WHERE server = "+config.serverId+" AND day!=0 AND day < "+(daysFromEpoch()-7));
		
		if (!timesToMerge.isEmpty()) {
			for (Entry<UUID, Integer> entry : timesToMerge.entrySet()) {
				statement.executeUpdate("INSERT INTO playtimes VALUES ("+UUIDtoHexString(entry.getKey())+", "+config.serverId+", 0, "+entry.getValue()+") ON DUPLICATE KEY UPDATE playtime=playtime+"+entry.getValue());
			}
		}
		
		results = statement.executeQuery("SELECT id, time, repeated, command FROM commands WHERE server = "+config.serverId+" ORDER BY id");
		while (results.next()) {
			this.commands.add(new StoredCommand(results.getInt(1), results.getInt(2), results.getBoolean(3), results.getString(4)));
		}
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
	
	synchronized public PlayerStats getPlayerStats(OfflinePlayer player) {
		UUID uuid = UUIDProvider.get(player);
		if (uuid==null) {
			return null;
		}
		if (player.isOnline()) {
			return getPlayerStats(uuid);
		}
		return retrievePlayerStats(uuid);
	}
	
	synchronized public PlayerStats getPlayerStats(UUID playerId) {
		PlayerStats stats = BetterOntime.instance.ds.playersStats.get(playerId);
		if (stats!=null) {
			return stats;
		}
		return retrievePlayerStats(playerId);
	}
	
	synchronized public PlayerStats retrievePlayerStats(UUID playerId) {
		PlayerStats stats = new PlayerStats();
		
		try {
			this.dbCheck();
			
			Statement statement = this.db.createStatement();
			
			ResultSet results = statement.executeQuery("SELECT server, day, playtime FROM playtimes WHERE player = "+UUIDtoHexString(playerId));
			
			int daysFromEpoch=daysFromEpoch(), thisServerId=BetterOntime.instance.config.serverId, playtime, serverId;
			
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
			
			results = statement.executeQuery("SELECT lastglobalplaytime FROM playerinfo WHERE player = "+UUIDtoHexString(playerId)+" AND server="+thisServerId);
			
			if(results.next()) {
				stats.lastGlobalCheck=results.getInt(1);
			}
			
			stats.lastEpochTime=(int) (System.currentTimeMillis()/1000);
			
			return stats;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
	
	synchronized void addTime(UUID playerId, int time) {
		addTime(playerId, time, BetterOntime.instance.config.serverId);
	}
	
	synchronized void addTime(UUID playerId, int time, int server) {
		if (time<1) { // playtime can't be less than 1
			return;
		}
		
		try {
			this.dbCheck();

			PlayerStats stats = this.playersStats.get(playerId);
			if (stats==null) {
				stats = new PlayerStats();
				this.playersStats.put(playerId, stats);
			}

			Statement statement = this.db.createStatement();
			statement.executeUpdate("INSERT INTO playtimes VALUES ("+UUIDtoHexString(playerId)+", "+server+", "+daysFromEpoch()+", "+time+") ON DUPLICATE KEY UPDATE playtime = playtime+"+time);

			stats.localLast+=time;
			stats.globalLast+=time;
			stats.local+=time;
			stats.global+=time;
			stats.lastEpochTime+=time;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	synchronized void setLastExecutedCommand(UUID playerId, int time, int server) {
		try {
			this.dbCheck();
			
			Statement statement = this.db.createStatement();
			
			statement.executeUpdate("INSERT INTO playerinfo VALUES ("+UUIDtoHexString(playerId)+", "+server+", "+time+") ON DUPLICATE KEY UPDATE lastglobalplaytime = "+time);
			
			PlayerStats stats = this.playersStats.get(playerId);
			if (stats==null) {
				stats = new PlayerStats();
				this.playersStats.put(playerId, stats);
			}

			stats.lastGlobalCheck=time;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	synchronized public int newServer() {
		try {
			this.dbCheck();
			
			Statement statement = this.db.createStatement();
			
			ResultSet results = statement.executeQuery("SELECT server FROM playtimes ORDER BY server DESC LIMIT 1");
			
			if (results.next()) {
				return results.getInt(1)+1;
			} else {
				return 1;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	synchronized public void addCommand(StoredCommand command) {
		try {
			this.dbCheck();
			Statement statement = this.db.createStatement();

			statement.executeUpdate("INSERT INTO commands (server, time, repeated, command) VALUES ("+BetterOntime.instance.config.serverId+", "+command.time+", "+(command.repeated?"1":"0")+", \""+command.command+"\")", Statement.RETURN_GENERATED_KEYS);
			
			ResultSet result = statement.getGeneratedKeys();
			if (result.next()) {
				command.id=result.getInt(1);
				this.commands.add(command);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	synchronized public void removeCommand(int id) {
		try {
			this.dbCheck();
			Statement statement = this.db.createStatement();

			statement.executeUpdate("DELETE FROM commands WHERE id="+id);
			
			int i=0;
			for(StoredCommand command : this.commands) {
				if (command.id==id) {
					this.commands.remove(i);
					break;
				}
				i++;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static int daysFromEpoch() {		
		Calendar start = Calendar.getInstance();
		Calendar now = Calendar.getInstance();
		
		start.setTimeInMillis(0);
		
		int days=0;
		
		while(start.get(Calendar.YEAR)<now.get(Calendar.YEAR)) {
			days+=start.getActualMaximum(Calendar.DAY_OF_YEAR);
			start.add(Calendar.YEAR, 1);
		}
		
		days+=now.get(Calendar.DAY_OF_YEAR)-start.get(Calendar.DAY_OF_YEAR);
		
		return days;
	}
	
	
	public static UUID toUUID(byte[] bytes) {
	    if (bytes.length != 16) {
	        throw new IllegalArgumentException();
	    }
	    int i = 0;
	    long msl = 0;
	    for (; i < 8; i++) {
	        msl = (msl << 8) | (bytes[i] & 0xFF);
	    }
	    long lsl = 0;
	    for (; i < 16; i++) {
	        lsl = (lsl << 8) | (bytes[i] & 0xFF);
	    }
	    return new UUID(msl, lsl);
	}
	
	public static String UUIDtoHexString(UUID uuid) {
		if (uuid==null) return "0x0";
		return "0x"+org.apache.commons.lang.StringUtils.leftPad(Long.toHexString(uuid.getMostSignificantBits()), 16, "0")+org.apache.commons.lang.StringUtils.leftPad(Long.toHexString(uuid.getLeastSignificantBits()), 16, "0");
	}
}
