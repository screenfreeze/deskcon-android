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
	private static DataOutputStream outToServer;
	private static DataInputStream inFromServer;
	private static String UUID;
	private static String PNAME;
	private static String HOST;
	private static int PORT;
	private static SharedPreferences sharedPrefs;
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
		private String message;

		@Override
		protected Void doInBackground(Bundle... params) {
			Bundle data = params[0];
			type = "FILE_UP";
			message = data.getString("filepath");
			
			try {
				sendData();
			} catch (Exception e) {
				e.printStackTrace();
			}			
			stopSelf();
			
			return null;
		}		
		
		// Building Protocol MSG
		private String buildmsg(String type, String msgstr) {		
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
			
	        File myFile = new File (message);
	        String msg = buildmsg(type, myFile.getName());
	        
			// send request
			
			outToServer.write("C".getBytes());
			
			// negotiate new secure connection port
			byte[] newportdata = new byte[4];
			BufferedInputStream br = new BufferedInputStream(inFromServer);
			br.read(newportdata);
			clientSocket.close();
			int newport = Integer.parseInt(new String(newportdata));
			
//			// load the keystore
//			InputStream keyStoreStream;
//			try {
//				keyStoreStream = openFileInput("devicekeystore.bks");
//			} catch (FileNotFoundException e1) {
//				return;
//			}
//			KeyStore MyKeyStore = KeyStore.getInstance("BKS");
//			MyKeyStore.load(keyStoreStream, "android".toCharArray());
//			Enumeration<String> aliases = MyKeyStore.aliases();
//			while(aliases.hasMoreElements()) {
//				System.out.println(aliases.nextElement());
//			}
//			
//			// initialize trust manager factory with the read truststore
//		    TrustManagerFactory trustManagerFactory = null;
//		    trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//			trustManagerFactory.init(MyKeyStore);
//			TrustManager[] tm = trustManagerFactory.getTrustManagers();					    
//			
//			// init KeyManagerFactory
//			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
//			keyManagerFactory.init(MyKeyStore, "passwd".toCharArray());
//			KeyManager[] km = keyManagerFactory.getKeyManagers();
//			
//			SSLContext sslcontext = SSLContext.getInstance("TLSv1.2");
//			sslcontext.init(km, tm, new SecureRandom());
//			
//			// make secure Connection
//		    SSLSocketFactory factory = (SSLSocketFactory) sslcontext.getSocketFactory();
//		    SSLSocket sslsocket = (SSLSocket) factory.createSocket(HOST, newport);
//		    sslsocket.setEnabledProtocols(new String[] {"TLSv1.2"});
//
//		    System.out.println(sslsocket.getSSLParameters().getProtocols()[0]);
			
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
				sendFile(myFile, sslsocket);
	        }						
	    }		
	}
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}	
}
