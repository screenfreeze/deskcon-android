package net.screenfreeze.deskcon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
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
		controlserver.cancel(true);
		controlserver.stopServer();
		super.onDestroy();
	}

	@SuppressLint("NewApi")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {       
		controlserver = new ControlServer();		
		
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
		    controlserver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
		    controlserver.execute();
		}
		
		return START_STICKY;
	}	
	
	private class ControlServer extends AsyncTask<Void, Void, Void> {
		private SSLServerSocket sslServerSocket;
		private SSLSocket socket;

		@Override
		protected Void doInBackground(Void... params) {
			Log.d("Control: ", "start Server");	
			try {		
				// create SSLServerSocket
				sslServerSocket = Connection.createSSLServerSocket(getApplicationContext(), PORT);
			} catch (Exception e) {
				e.printStackTrace();
				Log.d("Control: ", "could not start");	
			} 
			
			//begin serving
			while (!isCancelled()) {
				try {
					socket = (SSLSocket) sslServerSocket.accept();
					socket.startHandshake();
				} catch (IOException e) {					
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

			return null;
		}
		
		private void handleClientSocket(SSLSocket socket) throws Exception {
			BufferedReader inFromClient;			
			inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));	        

			// receive Data
	        byte[] data = readWithMaxBuffer(inFromClient);
	        String datastring = new String(data);
			socket.getOutputStream().write("OK".getBytes());			
			
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
		}
		
		// force server stop
		private void stopServer() {
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
