package net.screenfreeze.deskcon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import org.spongycastle.jce.X509Principal;
import org.spongycastle.x509.X509V3CertificateGenerator;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;


@SuppressWarnings("deprecation")
public class MainActivity extends PreferenceActivity {
	private static SharedPreferences sharedPrefs;
	private static AlarmManager alarmManager;
	private static PendingIntent statusUpdateServicePIntent;
	private static Editor sharedPrefsEditor;
	private static Intent statusUpdateServiceIntent;
	private static Intent controlServiceIntent;
	private static Intent discoveryServiceIntent;
	
	
    @SuppressLint("CommitPrefEdits")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);     
        
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefsEditor = sharedPrefs.edit();
		alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		statusUpdateServiceIntent = new Intent(this, StatusUpdateService.class);
		statusUpdateServicePIntent = PendingIntent.getService(this, 0, statusUpdateServiceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		controlServiceIntent = new Intent(this, ControlService.class);
		discoveryServiceIntent = new Intent(this, DiscoveryService.class);
		
        Preference desktophostsnewpref = findPreference("desktophosts");
        Preference notificationaccesspref = findPreference("notification_access");
        Preference notificationwhitelistpref = findPreference("notification_whitelist");
        Preference firstrunpref = findPreference("firstrun");
        Preference generatekeypref = findPreference("generatekeys");
        Preference uuidpref = findPreference("uuid");
        Preference aboutpref = findPreference("about");
        Preference changelogpref = findPreference("changelog");
        
        // hide from user
        getPreferenceScreen().removePreference(firstrunpref);
        getPreferenceScreen().removePreference(uuidpref);
        
        desktophostsnewpref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent ha = new Intent(getApplicationContext(), DesktopHostsActivity.class);
				startActivity(ha);
				return false;
			}
		});
        
        generatekeypref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showGenerateKeypairDialog();
				return false;
			}
		});
        
        aboutpref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showAboutDialog();
				return false;
			}
		});
        
        changelogpref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showChangelogDialog();
				return false;
			}
		});
       
        notificationaccesspref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				// Because Notification Access only on API18 and up
				int currentapiVersion = android.os.Build.VERSION.SDK_INT;
				if (currentapiVersion >= 18){
					// NotificationListener is still buggy
					//Intent nai = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"); 
					Intent nai = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
					startActivity(nai);
				} else{
					Intent nai = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
					startActivity(nai);
				}				
				return false;
			}
		});
        
        notificationwhitelistpref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent wa = new Intent(getApplicationContext(), NotificationWhitelistActivity.class);
				startActivity(wa);
		
				return false;
			}
		});
        
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsChangeListener);
        
        // start at firstrun
        boolean isfirstrun = sharedPrefs.getBoolean("firstrun", true);
        if (isfirstrun) { 
        	onFirstrun(); 
        }
        else {
        	initServices();
        }
    }
    
    private boolean isControlServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ControlService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    private void initServices() {		
    	if (sharedPrefs.getBoolean("allow_control", true)) {
    		boolean cs_running = isControlServiceRunning();
    		if (!cs_running) {
    			startService(controlServiceIntent);
    		}    		
    	}
    	if (sharedPrefs.getBoolean("status_updates", true)) {
			Log.d("Update Service: ", "update Alarm");

			int min = Integer.parseInt(sharedPrefs.getString("status_update_interval", "40"));
			Calendar cal = Calendar.getInstance();
			int secs = min * 60;
			alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 
					secs*1000, statusUpdateServicePIntent);	
    	}    
    	
    	//startService(discoveryServiceIntent);
    }
    
    // Actions if a Preference changes
    private OnSharedPreferenceChangeListener prefsChangeListener = 
    		new OnSharedPreferenceChangeListener() {
				
				@Override
				public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
						String key) {
					
					// status_updates Preference
					if (key.equals("status_updates")) {
						boolean state = sharedPrefs.getBoolean(key, true);
						
						if (state) {
							// start update service
							Log.d("Update Service: ", "start");

							int min = Integer.parseInt(sharedPrefs.getString("status_update_interval", "40"));
							Calendar cal = Calendar.getInstance();
							int secs = min * 60;
							alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 
									secs*1000, statusUpdateServicePIntent);	
						}
						else {
							// stop update service							
							alarmManager.cancel(statusUpdateServicePIntent);
							Log.d("Update Service: ", "stop");	
						}
					}
					// status_update_interval Preference
					if (key.equals("status_update_interval")) {
						boolean state = sharedPrefs.getBoolean("status_updates", true);
						
						// apply new interval
						if (state) {
							Log.d("Update Service: ", "restart");
							alarmManager.cancel(statusUpdateServicePIntent);
							// start update service
							int min = Integer.parseInt(sharedPrefs.getString("status_update_interval", "40"));
							Calendar cal = Calendar.getInstance();
							int secs = min * 60;
							alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 
									secs*1000, statusUpdateServicePIntent);	
						}
					}
					// allow_control Preference
					else if (key.equals("allow_control")) {
						boolean state = sharedPrefs.getBoolean(key, true);
						
						if (state) {
							// start control service
							startService(controlServiceIntent);
						}
						else {
							// stop control service
							stopService(controlServiceIntent);
						}
					}
					// allow_control Preference
					else if (key.equals("control_port")) {
						boolean state = sharedPrefs.getBoolean("allow_control", true);
						
						//apply new port
						if (state) {
							stopService(controlServiceIntent);
							// start control service
							startService(controlServiceIntent);
						}
					}
				}
			};
			
	private void onFirstrun() {
		// set the UUID
		TelephonyManager telephonyManager  = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String UUID = telephonyManager.getDeviceId();
        
        // if no telephone uid, then generate random number
        if (UUID == null || UUID.equals("")) {
        	Random rand = new Random();
        	long MAX = 999999999999999L;
        	long MIN = 100000000000000L;
        	long number = Math.abs(Long.valueOf(rand.nextLong()*(MAX - MIN)));
        	UUID = Long.toString(number);
        }
        
        // read default Notification Whitelist
        InputStream is = this.getResources().openRawResource(R.raw.defaultnotificationwl);        
        byte[] b = null;
		try {
			b = new byte[is.available()];
			is.read(b);
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        String notwl = new String(b);
        
        sharedPrefsEditor.putString("notification_whitelist", notwl);
        sharedPrefsEditor.putString("uuid", UUID);
        sharedPrefsEditor.putBoolean("firstrun", false);
        sharedPrefsEditor.commit();        
        
        // generate keypair
        new KeypairgenerationTask().execute();
        
		// start update service
//		int min = Integer.parseInt(sharedPrefs.getString("status_update_interval", "40"));
//		Calendar cal = Calendar.getInstance();
//		int secs = min * 60;
//		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 
//				secs*1000, statusUpdateServicePIntent);
	}
	
	private void showAboutDialog() {
		LayoutInflater li = getLayoutInflater();
		View aboutView = li.inflate(R.layout.about, null);
		
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setView(aboutView);
		
		alertDialogBuilder.setCancelable(true);
		alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();					
			}
		});
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();	
	}
	
	private void showChangelogDialog() {
		LayoutInflater li = getLayoutInflater();
		View aboutView = li.inflate(R.layout.changelog, null);
		
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setView(aboutView);
		alertDialogBuilder.setTitle("Change log");
		
		alertDialogBuilder.setCancelable(true);
		alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();					
			}
		});
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();	
	}
	
	private void showGenerateKeypairDialog() {		
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setTitle("Keypair");
		alertDialogBuilder.setMessage("Do you really want to generate a new Keypair?\nEvery already paired Device, will be erased!");
		alertDialogBuilder.setCancelable(true);
		alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				DesktopHostsDBHelper dbhelper = new DesktopHostsDBHelper(getApplicationContext());
				dbhelper.clearDB();
				new KeypairgenerationTask().execute();
				dialog.cancel();					
			}
		});
		alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();					
			}
		});
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();	
	}
    
    private class KeypairgenerationTask extends AsyncTask<Void, Void, Void> {
    	private ProgressDialog progress = null;

		@Override
		protected Void doInBackground(Void... arg0) {			
			Log.d("Cert Gen: ", "begin to generate");
			
	        try {        	
	    		// gen the RSA keypair
	    		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
	    		keyPairGenerator.initialize(2048, new SecureRandom());
	    		KeyPair KPair = keyPairGenerator.generateKeyPair();

	    		// generate Certificate
	    		X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();
	    		
	    		BigInteger serial = BigInteger.valueOf(new SecureRandom().nextInt());
	    		String devicename = sharedPrefs.getString("device_name", "Device");
	    		String deviceuuid = sharedPrefs.getString("uuid", "000000001111111");	    		

	    		v3CertGen.setSerialNumber(serial.abs());
	            v3CertGen.setIssuerDN(new X509Principal("CN=" + deviceuuid+"/" +devicename + ", OU=None, O=None L=None, C=None"));
	            v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
	            v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365*10)));
	            v3CertGen.setSubjectDN(new X509Principal("CN=" + deviceuuid+"/" +devicename + ", OU=None, O=None L=None, C=None"));

	            v3CertGen.setPublicKey(KPair.getPublic());
	            v3CertGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
	            
	            X509Certificate PKCertificate = v3CertGen.generate(KPair.getPrivate());	            
	            
	            // create keystore		
	            InputStream keyStoreStream = getResources().openRawResource(R.raw.defaultkeystore);
	    		
	    		KeyStore MyKeyStore = KeyStore.getInstance("BKS");
	    		MyKeyStore.load(keyStoreStream, "android".toCharArray());

	    		Certificate[] certchain = new Certificate[1];
	    		certchain[0] = PKCertificate;
	    		
	    		PrivateKey privkey = KPair.getPrivate();
	    		MyKeyStore.setKeyEntry("mykeypair",privkey, "passwd".toCharArray(), certchain);
	    		
	    		// write new Keystore
	    		OutputStream output = openFileOutput("devicekeystore.bks", Context.MODE_PRIVATE);

	            MyKeyStore.store(output, "android".toCharArray());
	            output.close();
	            
	        } catch (Exception e) {
	          e.printStackTrace();
	        }
	        
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			progress.dismiss();
			Log.d("Cert Gen: ", "finished to generate");
			initServices();
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			progress = ProgressDialog.show(MainActivity.this, null,
					"Generating Encryption Keypair...");
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
		}
    }
}