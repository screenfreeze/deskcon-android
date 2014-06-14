package net.screenfreeze.deskcon;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

public class ControlService extends Service {
	
	private static SharedPreferences sharedPrefs;
	private static int PORT;
	private static ControlServer controlserver;
	private static Toast SMSToastMessage;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}	
	
	@SuppressLint("ShowToast")
	@Override
	public void onCreate() {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		SMSToastMessage = Toast.makeText(getApplicationContext(), "SMS Sent",Toast.LENGTH_LONG);
		PORT = Integer.parseInt(sharedPrefs.getString("control_port", "9096"));
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		Log.d("Control: ", "stop Server");	
		//controlserver.cancel(true);
		controlserver.stopServer();
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
		controlserver = new ControlServer();
		
	    Thread cs = new Thread(controlserver);
	    cs.start();

		return START_STICKY;
	}	
	
	private class ControlServer implements Runnable {
		private SSLServerSocket sslServerSocket;
		private SSLSocket socket;
		private boolean isStopped = false;

		@Override
		public void run() {
			Log.d("Control: ", "start Server");	
			try {		
				// create SSLServerSocket
				sslServerSocket = Connection.createSSLServerSocket(getApplicationContext(), PORT);
			} catch (Exception e) {
				e.printStackTrace();
				Log.d("Control: ", "could not start");	
				return;
			} 
			
			//begin serving
			while (!isStopped) {
				try {
					socket = (SSLSocket) sslServerSocket.accept();
					socket.startHandshake();
				} catch (IOException e) {
					e.printStackTrace();
				}	
				if (socket != null && socket.isConnected()) {					
					try {
						handleClientSocket(socket);
						socket.close();
					} catch (Exception e) {
						e.printStackTrace();
					}					
				}
			}			
		}
		
		private void handleClientSocket(SSLSocket socket) throws Exception {		
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));	        
			DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream()); 
			// receive Data
	        byte[] data = readWithMaxBuffer(inFromClient);
	        String datastring = new String(data);
			outToClient.write("OK".getBytes());			
			
			Log.d("Control: ", "received CMD");
			
			// parse CMD Data
			JSONObject jobject = new JSONObject(datastring);
			//Long uuid = jobject.getLong("uuid");
			String name = jobject.getString("name");
			String cmdtype = jobject.getString("type");
			String cmddata = jobject.getString("data");
			
			// SMS
			if (cmdtype.equals("sms")) {
				sendSMS(cmddata);
			}
			// PING
			else if (cmdtype.equals("ping")) {
				playPing(name);
			}
			// FILEUP
			else if (cmdtype.equals("fileup")) {
				Log.d("Control: ", "File Transfer");
				JSONArray jarray = new JSONArray(cmddata);
				String[] filenames = new String[jarray.length()];
				for (int i=0; i<jarray.length(); i++) {
					filenames[i] = jarray.getString(i);
				}
				receiveFiles(filenames, socket);
				publishProgress(1);				
			}
		}

		private void publishProgress(int i) {
			Looper.prepare();		
			Toast finishedToast = Toast.makeText(getBaseContext(), 
					"received File(s)", Toast.LENGTH_LONG);			

			
			finishedToast.show();			
		}
		
		// force server stop
		private void stopServer() {
			isStopped = true;
			try {
				sslServerSocket.close();
			} catch (IOException e) {}
		}
		
	}

	//stores sent sms in sent folder
	public boolean storeSMS(String number, String message) {
	    boolean ret = false;
	    try {
	        ContentValues values = new ContentValues();
	        values.put("address", number);
	        values.put("body", message);
	        values.put("read", true);

	        getContentResolver().insert(Uri.parse("content://sms/sent"), values);
	        ret = true;
	    } catch (Exception ex) {
	        ret = false;
	    }
	    return ret;
	}
	
	public void sendSMS(String data) throws JSONException {
		JSONObject smsjobject = new JSONObject(data);
    	String number = smsjobject.getString("number");
    	String message = smsjobject.getString("message");
    	SmsManager smsManager = SmsManager.getDefault();
    	
		smsManager.sendTextMessage(number, null, message, null, null);
		storeSMS(number,message);
		
		SMSToastMessage.show();		
	}
	
	// play default ringtone with Notification
	public void playPing(String data) {
		Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(R.drawable.connector_launcher)
		        .setContentTitle("Ping!")
		        .setContentText("from "+data)
		        .setSound(ringtone);
		
		NotificationManager notificationManager =
			    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		notificationManager.notify(555, mBuilder.build());
	}
	
	private void receiveFiles(String[] filenames, SSLSocket socket) throws IOException {
		DataInputStream dataInFromClient = new DataInputStream(socket.getInputStream());
		BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		DataOutputStream outToClient = new DataOutputStream(socket.getOutputStream());
		
		File downloadfolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		
		for (int i=0; i<filenames.length; i++) {
			String filename = filenames[i];

			// receive Filesize
			String ins = inFromClient.readLine();
			long filesize = Long.parseLong(ins);
			
			byte[] buffer = new byte[4096];
			int loopcnt = Math.round(filesize/4096);
			int lastbytes = (int) (filesize % 4096);

			// open File
			File newFile = new File(downloadfolder, filename);
			FileOutputStream fos = new FileOutputStream(newFile);
			
			// send ready
			outToClient.write(1);
			// send Data
			for (int j=0; j<loopcnt; j++) {
				dataInFromClient.read(buffer, 0, 4096);
				fos.write(buffer, 0, 4096);
			}
			dataInFromClient.read(buffer, 0, lastbytes);
			fos.write(buffer, 0, lastbytes);
			fos.flush();
			fos.close();						
		}
	}
	
    public byte[] convertChartoByteArray(char[] chars) {
	    byte[] bytes = String.valueOf(chars).getBytes();
	    return bytes;
    }	
	
	private byte[] readWithMaxBuffer(BufferedReader inFromServer) throws IOException {
        char[] recvbuffer = new char[4096]; // avoid buffer overflow

        int readcnt = inFromServer.read(recvbuffer);
        char[] tmp_dataArray = new char[readcnt];
        for (int i=0; i<tmp_dataArray.length; i++) {
        	tmp_dataArray[i] = recvbuffer[i];
        }
        byte[] data = convertChartoByteArray(tmp_dataArray);
    
		return data;
	}
}
