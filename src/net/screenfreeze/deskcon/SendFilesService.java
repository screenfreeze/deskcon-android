package net.screenfreeze.deskcon;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;


public class SendFilesService extends Service {
	private DataOutputStream outToServer;
	private DataInputStream inFromServer;
	private static String UUID;
	private static String PNAME;
	private String HOST;
	private int PORT;
	private SharedPreferences sharedPrefs;
	private static Toast ConnectionError;	
	
	@SuppressLint("ShowToast")
	@Override
	public void onCreate() {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		loadIdentifiers();
        ConnectionError = Toast.makeText(getApplicationContext(), "could not connect to "+HOST, Toast.LENGTH_SHORT);
        
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
	
	@SuppressLint("NewApi")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Bundle extras = intent.getExtras();
		
		HOST = extras.getString("host");
		PORT = extras.getInt("port");
		
		if (extras != null) {
			SendFilesClient sfclient = new SendFilesClient();
			
			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
			    sfclient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, extras);
			} else {
			    sfclient.execute(extras);
			}
		}
				
		return super.onStartCommand(intent, flags, startId); 
	}
	
	private class SendFilesClient extends AsyncTask<Bundle, Void, Void> {
		private String type;
		private String[] filepaths;

		@Override
		protected Void doInBackground(Bundle... params) {
			Bundle data = params[0];
			type = "FILE_UP";
			filepaths = data.getStringArray("filepaths");
			
			try {
				sendData();
			} catch (Exception e) {
				e.printStackTrace();
			}			
			stopSelf();
			
			return null;
		}		
		
		// Building Protocol MSG
		private String buildmsg(String type, String[] filenames) {		
	        JSONObject jobject = new JSONObject();
	        JSONArray jarray = new JSONArray();
	        for (int i=0; i<filenames.length; i++){
	        	jarray.put(filenames[i]);
	        }
	        try {
				jobject.put("uuid", UUID);        
				jobject.put("devicename", PNAME);
		        jobject.put("type", type);
		        jobject.put("data", jarray.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}   
	        
			return jobject.toString();
		}
		
		// sendfile
		@SuppressWarnings("resource")
		private void sendFile(File f, SSLSocket sslsocket) throws Exception {		      
		      byte [] mybytearray  = new byte [(int)f.length()];
		      FileInputStream fis = new FileInputStream(f);		      
		      BufferedInputStream bis = new BufferedInputStream(fis);
		      OutputStream outputstream = sslsocket.getOutputStream();
		      InputStream inputstream = sslsocket.getInputStream();
		      // send file size
		      outputstream.write(String.valueOf(mybytearray.length).getBytes());
		      // wait for ready
		      inputstream.read();
		      bis.read(mybytearray,0,mybytearray.length);		      
		      outputstream.write(mybytearray,0,mybytearray.length);
		      outputstream.flush();
		}		
		
		private void sendData() throws Exception {			
			Socket clientSocket;
			try {
				clientSocket = new Socket(HOST, PORT);
			} catch (Exception e) {
				Log.d("Connection: ", "could not connect");
				ConnectionError.show();
				return;
			}						

			outToServer = new DataOutputStream(clientSocket.getOutputStream()); 
	        inFromServer = new DataInputStream(clientSocket.getInputStream());
	        
	        // create Filehandler
	        File[] files = new File[filepaths.length];
	        String[] filenames = new String[filepaths.length];
	        for (int i=0; i< files.length; i++) {
	        	files[i] = new File(filepaths[i]);
	        	filenames[i] = files[i].getName();
	        }
			
	        String msg = buildmsg(type, filenames);        
	        
			// send request			
			outToServer.write("C".getBytes());
			
			// negotiate new secure connection port
			byte[] newportdata = new byte[4];
			BufferedInputStream br = new BufferedInputStream(inFromServer);
			br.read(newportdata);
			clientSocket.close();
			int newport = Integer.parseInt(new String(newportdata));
			
			
			// create SSL Connection
			SSLSocket sslsocket = Connection.createSSLSocket(getApplicationContext(), HOST, newport);
			
		    // write data
			OutputStream out = sslsocket.getOutputStream();
			DataInputStream in = new DataInputStream(sslsocket.getInputStream());
			//Ask Host for Conformation
			out.write(msg.getBytes());
				
			//wait for ACK from Server
			Log.d("FileUp: ", "wait for Ack");
			int return_code = in.read();
	        
	        // send a File
	        if (return_code == 49) {
	        	for (File file : files) {
	        		sendFile(file, sslsocket);
	        	}				
	        }						
	    }		
	}
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}	
}
