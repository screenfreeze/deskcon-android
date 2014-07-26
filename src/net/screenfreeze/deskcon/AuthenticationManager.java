package net.screenfreeze.deskcon;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import net.screenfreeze.deskcon.DesktopHostsActivity.OnPairListener;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("ShowToast")
public class AuthenticationManager {
	private Context context;
	private DesktopHostsDBHelper dbhelper;
	private static Toast ConnectionError;
	
	public AuthenticationManager(Context context) {
		this.context = context;
		dbhelper = new DesktopHostsDBHelper(context);
		
        ConnectionError = Toast.makeText(this.context, "could not connect", 
        		Toast.LENGTH_SHORT);
	}

	@SuppressLint("NewApi")
	public void pairWithHost(String ip, int port, String ssid, OnPairListener onpairlistener) {
		Log.d("Pairing: ", "start");	
        // set UI Callback
		ParingTask pairingTask = new ParingTask();
		pairingTask.setOnPairListener(onpairlistener);
		
		// start pairing
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			pairingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, ip, port, ssid);
		} else {
			pairingTask.execute(ip, port, ssid);
		}	
	}	
		
	private AlertDialog buildValidateFingerprintDialog(String myfp, String serverfp, final ParingTask pairingtask) {
		LayoutInflater li = ((Activity) context).getLayoutInflater();
		View verifyFpView = li.inflate(R.layout.verify_fingerprint_dialog, null);
		
		
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
		alertDialogBuilder.setView(verifyFpView);
		
		alertDialogBuilder.setTitle("Pairing");
		final TextView serverfptextview = (TextView) verifyFpView.findViewById(R.id.serverfptextView);
		final TextView myfptextview = (TextView) verifyFpView.findViewById(R.id.myfptextView);
		
		serverfptextview.setText(serverfp);
		myfptextview.setText(myfp);
		
		alertDialogBuilder.setPositiveButton("yes", new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				pairingtask.setFingerprintsValid(true);
				dialog.cancel();
			}});
		alertDialogBuilder.setNegativeButton("no", new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				pairingtask.setFingerprintsValid(false);
				dialog.cancel();
			}});
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		
		return alertDialog;
	}
	
	private AlertDialog buildPairingFailedDialog() {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
		alertDialogBuilder.setTitle("Pairing");
		alertDialogBuilder.setMessage("The pairing Process failed!");			
		alertDialogBuilder.setCancelable(true);
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		
		return alertDialog;
	}	
	
	
	private class ParingTask extends AsyncTask<Object, Object, Boolean> {
        String ServerName;
        String ServerUUID;
        String ServerFp;
        X509Certificate ServerCert;
        String Host;
        int Port;
        int SecPort;
        String Wifi;
        String MacAddress;
        String MyFp;
        AlertDialog fpDialog;
        ProgressDialog progressDialog = null;
        private OnPairListener onpaircallback;
        private boolean isValid = false;
        private boolean validated = false;
        private KeyStore MyKeyStore;

		@Override
		protected Boolean doInBackground(Object... params) {
			Host = (String) params[0];
			Port = (Integer) params[1];
			Wifi = (String) params[2];
			Socket clientSocket;			
			InputStream keyStoreStream;
			try {
				keyStoreStream = context.openFileInput("devicekeystore.bks");
			} catch (FileNotFoundException e1) {
				return false;
			}
			
			try {
				try {
					String HostAddress = InetAddress.getByName(Host).getHostAddress();
					clientSocket = new Socket(HostAddress, Port);
				}
				catch (Exception e) {
					ConnectionError.show();
					return false;
				}					
				
				// load the keystore
				MyKeyStore = KeyStore.getInstance("BKS");
				MyKeyStore.load(keyStoreStream, "android".toCharArray());
							
		        OutputStream outToServer = clientSocket.getOutputStream();
		        BufferedReader inFromServer = new BufferedReader(
		        		new InputStreamReader(clientSocket.getInputStream()));
		        
		        // 1.  send Pair request "P"
		        publishProgress(1);
		        outToServer.write("P".getBytes());
		        
		        // 2. Cert transfer
		        publishProgress(2);
		        byte[] ServerCertdata = readWithMaxBuffer(inFromServer);
		        byte[] dec_server_cert = Base64.decode(ServerCertdata, 0);
		        ServerFp = SHA256(dec_server_cert).toUpperCase(Locale.US).replaceAll("....(?=.)", "$0 ");
		        
		        byte[] mycert = MyKeyStore.getCertificate("mykeypair").getEncoded();		        
		        MyFp = SHA256(mycert).toUpperCase(Locale.US).replaceAll("....(?=.)", "$0 ");
		        
		        outToServer.write(mycert);
		        Log.d("Pairing: ", "exchanged Certificates");			        

		        // 3. Wait for Ack from user		        
		        publishProgress(4);
		        
		        CertificateFactory cf = CertificateFactory.getInstance("X.509");
		        ByteArrayInputStream bis = new ByteArrayInputStream(dec_server_cert);
		        ServerCert = (X509Certificate) cf.generateCertificate(bis);
		        String CertIssuer = ServerCert.getIssuerX500Principal().getName().replace("CN=", "");
		        ServerUUID = CertIssuer.split("/",2)[0];
		        ServerName = CertIssuer.split("/",2)[1];
		        //dont override mykeypair (possible attack)
		        if (ServerUUID.equals("mykeypair")) {return false;}
		        
		        String ack = inFromServer.readLine();
		        SecPort = Integer.parseInt(ack);  // Server send his Secure Port if he accepts, 0 otherwise
		        
		        while (!validated) {
		        	Thread.sleep(1000); // wait for users choice
		        }
		        
		        if (SecPort > 0 && isValid) {
		        	// successfully paired
		        	outToServer.write("OK".getBytes());
		        	return true;
		        }
		        else {
		        	// pairing failed
		        	outToServer.write(0);
		        }

	        } catch (Exception e) {
				e.printStackTrace();
			}
			
			return false;
		}		
		
		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(context);
			progressDialog.setMessage("Connecting to Desktop Host...");
			progressDialog.show();
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Object... values) {
			int phase = (Integer) values[0];
			switch (phase) {
			case 1:
				progressDialog.setMessage("Sending pair Request...");
				break;
			case 2:
				progressDialog.setMessage("Exchange Certificates...");
				break;
			case 4:
				progressDialog.setMessage("Waiting for Desktop...");
				fpDialog = buildValidateFingerprintDialog(MyFp, ServerFp, this);
				fpDialog.show();
				break;
			}
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			progressDialog.dismiss();

			if (result) {				
				try {
					// add to keystore
					MyKeyStore.setCertificateEntry(ServerUUID, ServerCert);
					OutputStream output = context.openFileOutput("devicekeystore.bks", Context.MODE_PRIVATE);
					MyKeyStore.store(output, "android".toCharArray());	
					output.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

				dbhelper.addHost(Long.parseLong(ServerUUID), ServerName, Host, SecPort, Wifi, MacAddress, ServerFp);
				onpaircallback.onPairingCompleted();				
			}
			else {
				AlertDialog pairingfaileddiealog = buildPairingFailedDialog();
				pairingfaileddiealog.show();
			}
			
			super.onPostExecute(result);
		}
		
		// User input
		public void setFingerprintsValid(boolean isvalid) {
			this.isValid = isvalid;	
			validated = true;
		}
		
		public void setOnPairListener(OnPairListener listener) {
			this.onpaircallback = listener;
		}
	}	
	
	public void deleteCertificate(Long uuid) {
		String alias = String.valueOf(uuid);
		InputStream keyStoreStream;
		try {
			keyStoreStream = context.openFileInput("devicekeystore.bks");
		} catch (FileNotFoundException e1) {
			return;
		}
		
		// load the keystore
		KeyStore MyKeyStore;
		try {
			MyKeyStore = KeyStore.getInstance("BKS");
			MyKeyStore.load(keyStoreStream, "android".toCharArray());
			MyKeyStore.deleteEntry(alias);
			// save keystore
			OutputStream output = context.openFileOutput("devicekeystore.bks", Context.MODE_PRIVATE);
			MyKeyStore.store(output, "android".toCharArray());
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	public static String SHA256 (byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(data);
 
        byte byteData[] = md.digest();

        StringBuffer hexString = new StringBuffer();
    	for (int i=0;i<byteData.length;i++) {
    		String hex=Integer.toHexString(0xff & byteData[i]);
   	     	if(hex.length()==1) hexString.append('0');
   	     	hexString.append(hex);
    	}

    	return hexString.toString();
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
