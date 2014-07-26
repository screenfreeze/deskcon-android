package net.screenfreeze.deskcon;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

public class DiscoveryService extends Service {
	
	private static SharedPreferences sharedPrefs;
	private static int PORT;
	private static ResponseServer responseserver;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}	
	
	@SuppressLint("ShowToast")
	@Override
	public void onCreate() {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		PORT = Integer.parseInt(sharedPrefs.getString("control_port", "9096"));
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		Log.d("Control: ", "stop Server");	
		//controlserver.cancel(true);
		responseserver.stopServer();
		super.onDestroy();
	}
	
	// workaround: sys stops task when UI closes
	@SuppressLint("NewApi")
	@Override
	public void onTaskRemoved(Intent rootIntent){
	    Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
	    restartServiceIntent.setPackage(getPackageName());

	    PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
	    AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
	    alarmService.set(
	    AlarmManager.ELAPSED_REALTIME,
	    SystemClock.elapsedRealtime() + 1000,
	    restartServicePendingIntent);

	    super.onTaskRemoved(rootIntent);
	 }

	@SuppressLint("NewApi")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {       
		responseserver = new ResponseServer();
		
	    Thread rs = new Thread(responseserver);
	    rs.start();

		return START_STICKY;
	}	
	
	private class ResponseServer implements Runnable {
		private DatagramSocket serverSocketUDP;
		private boolean isStopped = false;

		@Override
		public void run() {
			
			Log.d("Discovery: ", "start UDP Server");	
			
			try {		
				serverSocketUDP = new DatagramSocket(5108);   
			} catch (Exception e) {
				e.printStackTrace();
				Log.d("Dsicovery: ", "could not start");	
				return;
			} 
			
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			MulticastLock lock = wifi.createMulticastLock("net.screenfreeze.deskcon");
			lock.acquire();

			
			//begin serving
			while (!isStopped) {
				byte[] receiveData = new byte[128];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				
				try {
					serverSocketUDP.receive(receivePacket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
                InetAddress address = receivePacket.getAddress();
                String msg = new String(receiveData, 0, receivePacket.getLength());
                
                
                Log.d("udp from: ", "msg "+msg+"  "+address);
                
                handleReceiveUdp(msg);
			}
			lock.release();
		}
				
		// force server stop
		private void stopServer() {
			isStopped = true;
			serverSocketUDP.close();
		}		
	}
	
	private void handleReceiveUdp(String msg) {
		String[] split = msg.split("::");
		String code = split[0];
		String info = split[1];
		
		// Start the Update Service
		if (code.equals("startupdateservice01")) {
			Intent statusUpdateServiceIntent = new Intent(this, StatusUpdateService.class);
			startService(statusUpdateServiceIntent);			
		}
	}
}
