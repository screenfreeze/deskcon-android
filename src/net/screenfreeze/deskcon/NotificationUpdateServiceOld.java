package net.screenfreeze.deskcon;

import java.util.ArrayList;
import java.util.Arrays;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class NotificationUpdateServiceOld extends AccessibilityService {
	private static SharedPreferences sharedPrefs;

	@Override
	public void onCreate() {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		super.onCreate();
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
	    if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
	        Notification not = (Notification) event.getParcelableData();
		
	        	
			// permissions
			boolean send_other_notifications = sharedPrefs.getBoolean("send_other_notifications", false);
			ArrayList<String> whitelist = getNotificationWhitelist();
			String packagename = String.valueOf(event.getPackageName());
			
			if (not != null && send_other_notifications && whitelist.contains(packagename)) {
				Log.d("Notification: ", "new post");

				if (not.tickerText != null) {
					String text = not.tickerText.toString();
					startUpdateServiceCommand(text);
				}
			}
	    }
	}
	
	@Override
	protected void onServiceConnected() {
	    AccessibilityServiceInfo info = new AccessibilityServiceInfo();
	    info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
	    info.notificationTimeout = 1;
	    info.feedbackType = AccessibilityEvent.TYPES_ALL_MASK;
	    setServiceInfo(info);
	}
	
	//send specific Command
	private void startUpdateServiceCommand(String mess) {			
		Intent i = new Intent(getBaseContext(), StatusUpdateService.class);
		i.putExtra("commandtype", "OTH_NOT");
		i.putExtra("message", mess);

		startService(i);
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

	@Override
	public void onInterrupt() {		
	}

}
