package net.screenfreeze.deskcon;

import java.util.ArrayList;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

@SuppressLint("NewApi")
public class NotificationUpdateService extends NotificationListenerService{
	private static SharedPreferences sharedPrefs;

	@Override
	public void onCreate() {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		super.onCreate();
	}

	@Override
	public void onNotificationPosted(StatusBarNotification not) {		
		
		// permissions
		boolean send_other_notifications = sharedPrefs.getBoolean("send_other_notifications", false);
		ArrayList<String> whitelist = getNotificationWhitelist();
		String packagename = not.getPackageName();
		
		if (send_other_notifications && whitelist.contains(packagename)) {
			Log.d("Notification: ", "new post");
			Notification notification = not.getNotification();

			String text = getAppnameFromPackagename(packagename) + " " +
					notification.tickerText.toString();
			startUpdateServiceCommand(text);	
		}	
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification arg0) {
	}
	
	private ArrayList<String> getNotificationWhitelist() {
		String whiteliststr = sharedPrefs.getString("notification_whitelist", "");
		ArrayList<String> whitelist = new ArrayList<String>();
		if (whiteliststr.equals("")) {
			whitelist = new ArrayList<String>();
		}
		else {
			whitelist = new ArrayList<String>(Arrays.<String>asList((whiteliststr.split(", "))));
		}	
		
		return whitelist;
	}
	
	//send specific Command
	private void startUpdateServiceCommand(String mess) {			
		Intent i = new Intent(getBaseContext(), StatusUpdateService.class);
		i.putExtra("commandtype", "OTH_NOT");
		i.putExtra("message", mess);
		Log.d("Notification: ", "send text");
		
		startService(i);
	}
	
	private String getAppnameFromPackagename(String pname) {
		PackageManager packageManager = this.getPackageManager();
		
	    try {
	        ApplicationInfo app = packageManager.getApplicationInfo(pname, 0);        

	        //Drawable icon = packageManager.getApplicationIcon(app);
	        String name = packageManager.getApplicationLabel(app).toString();
	        
	        return name;
	    } catch (NameNotFoundException e) {
	    	return "";
	    }		
	}
}
