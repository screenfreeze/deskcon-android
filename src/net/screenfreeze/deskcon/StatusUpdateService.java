package net.screenfreeze.deskcon;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.util.Log;

public class StatusUpdateService extends Service {
	private static String UUID;
	private static String PNAME;
	private String[] HOSTS;
	private int[] PORTS;
	private DesktopHostsDBHelper dbhelper;
	private SharedPreferences sharedPrefs;
	
	@SuppressLint("ShowToast")
	@Override
	public void onCreate() {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		super.onCreate();
	}
	
	private void loadIdentifiers() {
        UUID = sharedPrefs.getString("uuid", "0000000011111111");
        String storedname = sharedPrefs.getString("device_name", "");
        if (storedname.equals("")) {
        	PNAME = android.os.Build.MODEL;
        }
        else {
        	PNAME = storedname;
        }        
	}
	
	private void loadHosts() {
		dbhelper = new DesktopHostsDBHelper(this);		
    	String current_wifi = getWifiSSID();
    	
    	Cursor cursor = dbhelper.getHostsOnWifiCursor(current_wifi);
    	int hostcount = cursor.getCount();			    	

    	HOSTS = new String[hostcount];
    	PORTS = new int[hostcount];
    	
        if (cursor.moveToFirst()) {
            do {    
            	HOSTS[cursor.getPosition()] = cursor.getString(2);
            	PORTS[cursor.getPosition()] = cursor.getInt(3);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        dbhelper.close();
	}
	
	@SuppressLint("NewApi")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		loadIdentifiers();
		loadHosts();
		if ( HOSTS.length == 0 ) {
			stopSelf();
			return super.onStartCommand(intent, flags, startId);
		}
		
		Bundle extras = intent.getExtras();

		if (extras == null || !extras.containsKey("commandtype")) {
			sendStatusUpdate();
		}
		else {
			sendStatusUpdate();
//			SendDataClient dataclient = new SendDataClient();			
//			//exe in parallel
//			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//			    dataclient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, extras);
//			} else {
//			    dataclient.execute(extras);
//			}
			SendDataThread datathread = new SendDataThread(extras);
		    Thread dt = new Thread(datathread);
		    dt.start();
		}
				
		return super.onStartCommand(intent, flags, startId); 
	}
	
	@SuppressLint("NewApi")
	private void sendStatusUpdate() {
		String jsonstr = getCurrentStatus();
		Bundle data = new Bundle();
		data.putString("commandtype", "STATS");
		data.putString("message", jsonstr);
//		SendDataClient dataclient = new SendDataClient();
//		
//		//exe in parallel
//		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
//		    dataclient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, data);
//		} else {
//		    dataclient.execute(data);
//		}
		
		SendDataThread datathread = new SendDataThread(data);
	    Thread dt = new Thread(datathread);
	    dt.start();
	}

	// Building Protocol MSG
	private static String buildmsg(String type, String msgstr) {
        JSONObject jobject = new JSONObject();
        try {
			jobject.put("uuid", UUID);        
			jobject.put("devicename", PNAME);
	        jobject.put("type", type);
	        jobject.put("data", msgstr);
		} catch (JSONException e) {
			e.printStackTrace();
		}   
        
		return jobject.toString();
	}
		
	//get current Stats
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private String getCurrentStatus() {
		// get Permissions
		boolean allow_control = sharedPrefs.getBoolean("allow_control", false);
		boolean allow_compose_message = sharedPrefs.getBoolean("allow_compose_message", false);
		boolean send_vol = sharedPrefs.getBoolean("send_vol", false);
		boolean send_bat = sharedPrefs.getBoolean("send_bat", false);
		boolean send_messages = sharedPrefs.getBoolean("send_messages", false);
		boolean send_calls = sharedPrefs.getBoolean("send_calls", false);
		boolean send_storage = sharedPrefs.getBoolean("send_storage", false);
		
		//create json object to send
        JSONObject jobject = new JSONObject();
        
        PackageManager pm = getApplicationContext().getPackageManager();
        boolean has_telephone = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        	
		// get control permissions
    	if (allow_control) {
    		int control_port = Integer.parseInt(sharedPrefs.getString("control_port", "9096"));
    		try {
				jobject.put("controlport", control_port);
			} catch (JSONException e) {
				e.printStackTrace();
			}
    		
    		//get sms capability    		
    		boolean can_message = has_telephone;
    		
    		try {
				jobject.put("canmessage", can_message && allow_compose_message);
			} catch (JSONException e) {
				e.printStackTrace();
			}    		
		}
    	
    	// get used Storage
    	if (send_storage) {
    		long KILOBYTE = 1024;
    		
    		StatFs internalStatFs = new StatFs( Environment.getDataDirectory().getAbsolutePath() );
    		long internalTotal;
    		long internalFree;

    		StatFs externalStatFs = new StatFs( Environment.getExternalStorageDirectory().getAbsolutePath() );
    		long externalTotal;
    		long externalFree;

    		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
    		    internalTotal = ( internalStatFs.getBlockCountLong() * internalStatFs.getBlockSizeLong() ) / ( KILOBYTE * KILOBYTE );
    		    internalFree = ( internalStatFs.getAvailableBlocksLong() * internalStatFs.getBlockSizeLong() ) / ( KILOBYTE * KILOBYTE );
    		    externalTotal = ( externalStatFs.getBlockCountLong() * externalStatFs.getBlockSizeLong() ) / ( KILOBYTE * KILOBYTE );
    		    externalFree = ( externalStatFs.getAvailableBlocksLong() * externalStatFs.getBlockSizeLong() ) / ( KILOBYTE * KILOBYTE );
    		}
    		else {
    		    internalTotal = ( (long) internalStatFs.getBlockCount() * (long) internalStatFs.getBlockSize() ) / ( KILOBYTE * KILOBYTE );
    		    internalFree = ( (long) internalStatFs.getAvailableBlocks() * (long) internalStatFs.getBlockSize() ) / ( KILOBYTE * KILOBYTE );
    		    externalTotal = ( (long) externalStatFs.getBlockCount() * (long) externalStatFs.getBlockSize() ) / ( KILOBYTE * KILOBYTE );
    		    externalFree = ( (long) externalStatFs.getAvailableBlocks() * (long) externalStatFs.getBlockSize() ) / ( KILOBYTE * KILOBYTE );
    		}

    		long total = internalTotal + externalTotal;
    		long free = internalFree + externalFree;
    		long used = total - free;
    		
    		int storagePer = Math.round((used / (float)total) * 100);

    		try {
				jobject.put("storage", storagePer);
			} catch (JSONException e) {
				e.printStackTrace();
			}    		
    	}
    	
    	//get Volume
    	if (send_vol) {
	        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
	        
	        int cvolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
	        int maxvolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
	        int volumePer = Math.round((cvolume / (float)maxvolume) * 100);
    		
    		try {
				jobject.put("volume", volumePer);
			} catch (JSONException e) {
				e.printStackTrace();
			}    		
    	}
    	
	    //get Battery
    	if (send_bat) {
    		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
	        // Are we charging / charged?
	        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
	        
	        boolean batteryisCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
	                             status == BatteryManager.BATTERY_STATUS_FULL;
	        
	        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
	        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
	
	        int batteryPer = Math.round((level / (float)scale) * 100);
	        
    		try {
				jobject.put("battery", batteryPer);
				jobject.put("batterystate", batteryisCharging);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}  

        //get missed Calls
    	if (send_calls) {        
    		String[] projection = { CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_NUMBER_LABEL, CallLog.Calls.TYPE };
	        String where = CallLog.Calls.TYPE+"="+CallLog.Calls.MISSED_TYPE+ " AND " + CallLog.Calls.NEW + "=1";          
	        Cursor c = this.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, where, null, null);
	        c.moveToFirst();
	        int missedCalls = c.getCount();
	        c.close();
	        
    		try {
				jobject.put("missedcalls", missedCalls);
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	}
        
        //get missed Messages
    	if (send_messages) {
	    	Uri sms_content = Uri.parse("content://sms/inbox");
	        Cursor cm = this.getContentResolver().query(sms_content, null,"read = 0", null, null);
	        cm.moveToFirst();
	        int missedMsgs = cm.getCount();
	        cm.close();
	        
	        try {
				jobject.put("missedmsgs", missedMsgs);
			} catch (JSONException e) {
				e.printStackTrace();
			}
    	}
    	
    	//get Wifi strength
    	int wifistrength = getWifiStrength();
        try {
			jobject.put("wifistrength", wifistrength);
		} catch (JSONException e) {
			e.printStackTrace();
		}    	
    	
		return jobject.toString();
	}
	
	public static boolean isTablet(Context context) {
	    return (context.getResources().getConfiguration().screenLayout
            & Configuration.SCREENLAYOUT_SIZE_MASK)
            >= Configuration.SCREENLAYOUT_SIZE_LARGE;
	}
	
	private class SendDataThread implements Runnable {
		private String type;
		private String message;
		private Bundle data;
		
		public SendDataThread(Bundle data) {
			type = data.getString("commandtype");
			message = data.getString("message");	
			this.data = data;
		}

		@SuppressLint("Wakelock")
		@Override
		public void run() {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Status Update WL");
			wl.acquire();
			
			if (data.containsKey("host")) {
				// send only to one Host
				String HOST = data.getString("host");
				int PORT = data.getInt("port");
				
				try {
					sendData(HOST, PORT);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			else {
				//send to all Hosts
				for (int i=0; i<HOSTS.length; i++) {
					try {
						sendData(HOSTS[i], PORTS[i]);
					} catch (Exception e) {
						e.printStackTrace();
					}				
				}
			}
			
			wl.release();			
		}
		
		private void sendData(String host, int port) throws Exception {
			Socket sslsocket = null;
			try {
				// create SSl Connection
				sslsocket = Connection.createSSLSocket(getApplicationContext(), host, port);
			} catch (Exception e) {
				Log.d("Connection: ", "could not connect");
				return;
			}
		    
			// write data
			OutputStream out = sslsocket.getOutputStream();
			String data = buildmsg(type, message);
			out.write(data.getBytes());
			sslsocket.close();
	    }	
		
	}
	
	//old code
	private class SendDataClient extends AsyncTask<Bundle, Void, Void> {
		private String type;
		private String message;

		@SuppressLint("Wakelock")
		@Override
		protected Void doInBackground(Bundle... params) {
			Bundle data = params[0];
			type = data.getString("commandtype");
			message = data.getString("message");
			
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Status Update WL");
			wl.acquire();			
			
			if (data.containsKey("host")) {
				// send only to one Host
				String HOST = data.getString("host");
				int PORT = data.getInt("port");
				
				try {
					sendData(HOST, PORT);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			else {
				//send to all Hosts
				for (int i=0; i<HOSTS.length; i++) {
					try {
						sendData(HOSTS[i], PORTS[i]);
					} catch (Exception e) {
						e.printStackTrace();
					}				
				}
			}
			
			wl.release();
			
			stopSelf();
			
			return null;
		}		
		
		private void sendData(String host, int port) throws Exception {
			Socket sslsocket = null;
			try {
				host = InetAddress.getByName(host).getHostAddress();
				// create SSl Connection
				sslsocket = Connection.createSSLSocket(getApplicationContext(), host, port);
			} catch (Exception e) {
				Log.d("Connection: ", "could not connect");
				return;
			}
		    
			// write data
			OutputStream out = sslsocket.getOutputStream();
			String data = buildmsg(type, message);
			out.write(data.getBytes());
			sslsocket.close();
	    }		
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
    private String getWifiSSID() {
    	String ssid = "";
    	WifiManager wifiManager = (WifiManager) getApplicationContext()
            		.getSystemService(Context.WIFI_SERVICE);
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            ssid = wifiInfo.getSSID();
        } catch (Exception a) { }
        
        if (ssid == null) {ssid = "";}
        return ssid;    	
    }
    
    private int getWifiStrength() {
        try {
      	   WifiManager wifiManager = (WifiManager) getApplicationContext()
           		.getSystemService(Context.WIFI_SERVICE);
            int rssi = wifiManager.getConnectionInfo().getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 10);
            int percentage = (int) ((level/10.0)*100);
            return percentage;
        }
        catch (Exception e) {
           return -1;
        }
	}        
}
