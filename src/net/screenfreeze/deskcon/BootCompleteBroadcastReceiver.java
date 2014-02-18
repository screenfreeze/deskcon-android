package net.screenfreeze.deskcon;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootCompleteBroadcastReceiver extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent arg1) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		boolean isfirstrun = sharedPrefs.getBoolean("firstrun", true);
		boolean update_service_enabled = sharedPrefs.getBoolean("status_updates", false);
		boolean control_service_enabled = sharedPrefs.getBoolean("allow_control", false);
		int min = Integer.parseInt(sharedPrefs.getString("status_update_interval", "40"));
		
		if (isfirstrun) { return; } // exit, because Setup from user needs to run first
		if (update_service_enabled) {
			Log.d("Boot: ", "start update service");
			AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			Intent i = new Intent(context, StatusUpdateService.class);		
			PendingIntent pintent = PendingIntent.getService(context, 0, i, 0);
			Calendar cal = Calendar.getInstance();
			int secs = min * 60;
			alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), secs*1000, pintent);
		}
		if (control_service_enabled) {
			Intent j = new Intent(context,ControlService.class);
			context.startService(j);
		}
		Log.d("Boot: ", "startedup");	
	}
}
