package net.kaikk.mc.bot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.common.collect.Lists;

public class Utils {
	/** This static method will merge an array of strings from a specific index */
	static String mergeStringArrayFromIndex(String[] arrayString, int i) {
		StringBuilder sb = new StringBuilder();

		for(;i<arrayString.length;i++){
			sb.append(arrayString[i]);
			sb.append(' ');
		}

		if (sb.length()!=0) {
			sb.deleteCharAt(sb.length()-1);
		}
		return sb.toString();
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

	public static int epoch() {
		return (int) (System.currentTimeMillis()/1000);
	}

	public static String timeToString(int time) {
		ArrayList<String> strs = new ArrayList<String>();

		if (time<0) {
			time*=-1;
		}

		// seconds
		int secs = time % 60;
		if (secs!=0||time==0) {
			strs.add(secs+" second"+(secs!=1?"s":""));
		}
		if (time<60) {
			return mergeTimeStrings(strs);
		}

		// minutes
		int tmins = (time-secs) / 60;
		int mins = tmins % 60;
		if (mins!=0) {
			strs.add(mins+" minute"+(mins!=1?"s":""));
		}
		if (tmins<60) {
			return mergeTimeStrings(strs);
		}

		// hours
		int thours = (tmins-mins) / 60;
		int hours = thours % 24;
		if (hours!=0) {
			strs.add(hours+" hour"+(hours!=1?"s":""));
		}
		if (thours<24) {
			return mergeTimeStrings(strs);
		}

		// days
		int tdays = (thours-hours) / 24;
		if (tdays!=0) {
			strs.add(tdays+" day"+(tdays!=1?"s":""));
		}

		return mergeTimeStrings(strs);
	}

	public static String mergeTimeStrings(ArrayList<String> strs) {
		String string="";
		for(String str : strs) {
			string=str+(string==""?"":" ")+string;
		}
		return string;
	}

	public static Integer stringToTime(String tString) {
		Integer time;
		tString=tString.replace(" ", "").toLowerCase();

		try {
			time=Integer.valueOf(tString);
			return time;
		} catch (NumberFormatException e) {
			try {
				time=Integer.valueOf(tString.substring(0, tString.length()-1));
			} catch (NumberFormatException e1) {
				return null;
			}

			char unit = tString.charAt(tString.length()-1);

			switch(unit) {
			case 's':
				return time;
			case 'm':
				return time*60;
			case 'h':
				return time*3600;
			case 'd':
				return time*86400;
			default:
				return null;
			}
		}
	}

	public static int daysFromEpoch() {		
		Calendar start = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

		start.setTimeInMillis(0);

		int days=0;

		while(start.get(Calendar.YEAR)<now.get(Calendar.YEAR)) {
			days+=start.getActualMaximum(Calendar.DAY_OF_YEAR);
			start.add(Calendar.YEAR, 1);
		}

		days+=now.get(Calendar.DAY_OF_YEAR)-start.get(Calendar.DAY_OF_YEAR);

		return days;
	}

	public static List<Player> getOnlinePlayersList() {
		return Lists.newArrayList(Bukkit.getOnlinePlayers());
	}

	public static <T extends Object> List<T> newList(T[] array) {
		List<T> newList = new LinkedList<T>();
		Collections.addAll(newList, array);
		return newList;
	}

	public static <T extends Object> List<T> newList(Collection<T> collection) {
		return new LinkedList<T>(collection);
	}

	public static boolean hasExplicitPermission(Player player, String permission) {
		return player.isPermissionSet(permission) && player.hasPermission(permission);
	}

	static String addTimeSql(UUID uuid, int time, int server, int day) {
		return "INSERT INTO playtimes VALUES ("+Utils.UUIDtoHexString(uuid)+", "+server+", "+day+", "+time+") ON DUPLICATE KEY UPDATE playtime = playtime+"+time;
	}
}
