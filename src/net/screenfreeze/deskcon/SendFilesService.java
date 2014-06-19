package net.screenfreeze.deskcon;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;

import javax.net.ssl.SSLSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;


public class SendFilesService extends Service {
	private static String UUID;
	private static String PNAME;
	private String HOST;
	private int PORT;
	private SharedPreferences sharedPrefs;
	private static Toast ConnectionError;
	public static Context context;
	
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
		context = this;
		
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
		private void sendFile(Uri f, SSLSocket sslsocket) throws Exception {
			  byte [] buffer = new byte [4096];
			  long filesize = getFileSize(f);
			  
			  InputStream is = getContentResolver().openInputStream(f);	      
		      BufferedInputStream bis = new BufferedInputStream(is);
		      OutputStream outputstream = sslsocket.getOutputStream();
		      InputStream inputstream = sslsocket.getInputStream();
		      
		      // send file size
		      outputstream.write(String.valueOf(filesize).getBytes());
		      // wait for ready
		      inputstream.read();
		      long cnt = Math.round(filesize / 4096)+1;
		      
		      for (long i=0; i < cnt; i++) {    		  
		    	  int bytesread = bis.read(buffer, 0, 4096);
		    	  outputstream.write(buffer, 0 ,bytesread);
			      outputstream.flush();
		      }
		}		
		
		private void sendData() throws Exception {			
	        // create Filehandler
	        Uri[] fileUris = new Uri[filepaths.length];
	        String[] filenames = new String[filepaths.length];

	        for (int i=0; i< fileUris.length; i++) {
	        	if (filepaths[i].startsWith("content://com.google.android.gallery3d.provider/picasa/")){	        		
	        		fileUris[i] = cachePicasaFile(Uri.parse(filepaths[i])); // Picasa workaround
	        	}
	        	else {
	        		fileUris[i] = Uri.parse(filepaths[i]);
	        	}	        	
	        	filenames[i] = getFileName(fileUris[i]);
	        }
			
	        String msg = buildmsg(type, filenames);	        
	        
			SSLSocket sslsocket;
			try {
				HOST = InetAddress.getByName(HOST).getHostAddress();
				// create SSL Connection
				sslsocket = Connection.createSSLSocket(getApplicationContext(), HOST, PORT);
			} catch (Exception e) {
				Log.d("Connection: ", "could not connect");
				ConnectionError.show();
				return;
			}

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
	        	for (int j=0; j<fileUris.length ;j++) {
	        		Uri file = fileUris[j];
	        		sendFile(file, sslsocket);
	        	}				
	        }						
	    }		
	}
	
	// Workaround ,because of a Google Bug!
	private Uri cachePicasaFile(Uri contentUri) throws IOException {
		String fname = getFileName(contentUri);
	    File cDir = getBaseContext().getCacheDir();               
	    File tempFile = new File(cDir.getPath() + "/" + fname) ;
	    
		InputStream is = getContentResolver().openInputStream(contentUri);	      
	    BufferedInputStream bis = new BufferedInputStream(is);
	    FileOutputStream fos = new FileOutputStream(tempFile);
	    long filesize = getFileSize(contentUri);
	    
	    byte[] buffer = new byte[4096];
	    
	    long cnt = Math.round(filesize / 4096)+1;
	    for (long i=0; i < cnt; i++) {    		  
	    	  int bytesread = bis.read(buffer, 0, 4096);
	    	  fos.write(buffer, 0 ,bytesread);
		      fos.flush();
	    }
	    fos.close();
	      
	    Uri uri = Uri.fromFile(tempFile);
		return uri;		
	}
	
	private long getFileSize(Uri contentUri) {
        if (contentUri.getScheme().equals("file")) {
        	File f = new File(contentUri.getPath());
        	return f.length();
        }
        else {
        	String [] proj={MediaStore.Images.Media.SIZE};
	        Cursor cursor = getBaseContext().getContentResolver().query( contentUri,
                    proj, // Which columns to return
                    null,       // WHERE clause; which rows to return (all rows)
                    null,       // WHERE clause selection arguments (none)
                    null); // Order-by clause (ascending by name)

	        cursor.moveToFirst();

	        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
	        long size = cursor.getLong(column_index);
	        
	        cursor.close();
	        
	        return size;
        }
	}
	
	private String getFileName(Uri contentUri) {
        if (contentUri.getScheme().equals("file")) {
        	return contentUri.getLastPathSegment();
        }
        else {
        	String [] proj={MediaStore.Images.Media.DISPLAY_NAME};
	        Cursor cursor = getBaseContext().getContentResolver().query( contentUri,
                    proj, // Which columns to return
                    null,       // WHERE clause; which rows to return (all rows)
                    null,       // WHERE clause selection arguments (none)
                    null); // Order-by clause (ascending by name)

	        cursor.moveToFirst();

	        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
	        String name = cursor.getString(column_index);
	        cursor.close();
	        
	        return name;
        }
	}	
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}	
}
