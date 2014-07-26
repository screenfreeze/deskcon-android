package net.screenfreeze.deskcon;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Formatter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DesktopHostsActivity extends Activity {
	private DesktopHostsDBHelper dbhelper;
	private AuthenticationManager authenticationmanager;
	private ListView hostslistview;
	private static DiscoveryTask responseserver;

	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.desktop_hosts);
		
		dbhelper = new DesktopHostsDBHelper(this);
		authenticationmanager = new AuthenticationManager(this);
		
		ImageView addButton = (ImageView) findViewById(R.id.hostsaddimageView);
		hostslistview = (ListView) findViewById(R.id.hostslistView);
		
		addButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showAddHostDialog();				
			}
		});
		
		loadHostlist();
		//testreaddb();				
	}
	
	@Override
	protected void onDestroy() {
		dbhelper.close();
		super.onDestroy();
	}

	private void loadHostlist() {
		hostslistview.removeAllViewsInLayout();
		HostsAdapter ha = new HostsAdapter(this, dbhelper.getAllCursor());
		hostslistview.setAdapter(ha);
		hostslistview.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View parent,
					int pos, long id) {
				Cursor cursor = dbhelper.getHostByIdCursor(id);
				String ip = cursor.getString(2);
				int port = cursor.getInt(3);
				String wifi = cursor.getString(6);
				cursor.close();

				showEditHostDialog(id, ip, port, wifi);

				return false;
			}
		});
		hostslistview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View parent, int pos,
					long id) {
				Cursor cursor = dbhelper.getHostByIdCursor(id);
				String host = cursor.getString(2);
				int port = cursor.getInt(3);
				cursor.close();
				
				Intent i = new Intent(getApplicationContext(), StatusUpdateService.class);
	    		i.putExtra("commandtype", "PING");
	    		i.putExtra("message", "");
	    		i.putExtra("host", host);
	    		i.putExtra("port", port);
	    		startService(i);
	    	    Toast pingmsg = Toast.makeText(getApplicationContext(), 
	            		"send Ping", Toast.LENGTH_SHORT);
	    	    pingmsg.show();
			}
		});
	}
	
	@SuppressWarnings("unused")
	private void testreaddb() {
		Cursor cursor = dbhelper.getHostsOnWifiCursor("<unknown ssid>");
		System.out.println(cursor.getCount());

        if (cursor.moveToFirst()) {
            do {                
                System.out.println(cursor.getLong(0));
                System.out.println(cursor.getString(2));
                System.out.println(cursor.getInt(3));
                //System.out.println(cursor.getString(6));
            } while (cursor.moveToNext());
        }
	}
	
	private void showAddHostDialog() {
		LayoutInflater li = getLayoutInflater();
		View addhostView = li.inflate(R.layout.add_host_dialog, null);
		
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setView(addhostView);
		alertDialogBuilder.setTitle("Pair with Desktop");		
		
		
		alertDialogBuilder.setCancelable(true);
		final EditText ipedittext = (EditText) addhostView.findViewById(R.id.newipeditText);
		final EditText portedittext = (EditText) addhostView.findViewById(R.id.newporteditText);
		final CheckBox wifilockcheckbox = (CheckBox) addhostView.findViewById(R.id.wifilockcheckBox);
		ListView foundHostsListView = (ListView) addhostView.findViewById(R.id.foundlistView);
		ArrayList<DesktopHost> as = new ArrayList<DesktopHost>();
		
		//as.add(new DesktopHost("Hans", "192.168.55.66", 34553, 51674));
		final DiscoveredHostsAdapter dha = new DiscoveredHostsAdapter(getApplicationContext(), as);
		
		responseserver = new DiscoveryTask(dha);
		
		final String wifi = getWifiSSID();
		wifilockcheckbox.setText("only on "+wifi);		
		
		foundHostsListView.setAdapter(dha);
		foundHostsListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				DesktopHost desk = dha.getItem(position);
				
	        	if (wifilockcheckbox.isChecked()) {
	        		authenticationmanager.pairWithHost(desk.ip, desk.port, wifi, new OnPairCallback());
	        	}
	        	else {
	        		authenticationmanager.pairWithHost(desk.ip, desk.port, "", new OnPairCallback());
	        	}
	        	responseserver.stopServer();
			}
		});
		
		alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String ip = ipedittext.getText().toString();
				int port = Integer.parseInt(portedittext.getText().toString());   
		        
	        	if (wifilockcheckbox.isChecked()) {
	        		authenticationmanager.pairWithHost(ip, port, wifi, new OnPairCallback());
	        	}
	        	else {
	        		authenticationmanager.pairWithHost(ip, port, "", new OnPairCallback());
	        	}
	        	
	        	responseserver.cancel(true);
				dialog.cancel();					
			}
		});
		
		alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				responseserver.stopServer();
				dialog.cancel();				
			}
		});	
		
	    responseserver.execute();
		
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();		
	}
	
	// Edit a Host
	private void showEditHostDialog(final long id, String ip, int port, String wifi) {
		LayoutInflater li = getLayoutInflater();
		View addhostView = li.inflate(R.layout.add_host_dialog, null);
		
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setView(addhostView);
		alertDialogBuilder.setTitle("Edit Desktop");
		
		alertDialogBuilder.setCancelable(true);
		
		final EditText ipedittext = (EditText) addhostView.findViewById(R.id.newipeditText);
		final EditText portedittext = (EditText) addhostView.findViewById(R.id.newporteditText);
		final CheckBox wifilockcheckbox = (CheckBox) addhostView.findViewById(R.id.wifilockcheckBox);
		final String currentwifi;
		ipedittext.setText(ip);
		portedittext.setText(""+port);
		
		if (wifi.equals("")) {
			wifilockcheckbox.setChecked(false);
			currentwifi = getWifiSSID();
			wifilockcheckbox.setText("only on "+currentwifi);
		}
		else {
			wifilockcheckbox.setChecked(true);
			currentwifi = wifi;
			wifilockcheckbox.setText("only on "+currentwifi);
		}		
		
		alertDialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String ip = ipedittext.getText().toString();
				int port = Integer.parseInt(portedittext.getText().toString());	
				
	        	if (wifilockcheckbox.isChecked()) {
					// update DB
					dbhelper.updateHost(id, ip, port, currentwifi);	
	        	}
	        	else {
					// update DB
					dbhelper.updateHost(id, ip, port, "");	
	        	}    
				
	        	loadHostlist();
				
				dialog.cancel();					
			}
		});
		
		alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();				
			}
		});
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();	
	}
		
	// get current SSID of connected Wifi
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
    
    public class DiscoveredHostsAdapter extends ArrayAdapter<DesktopHost> {

		public DiscoveredHostsAdapter(Context context, 
				ArrayList<DesktopHost> objects) {
			super(context,R.layout.host_list_row_share,objects);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			DesktopHost desktop = getItem(position);
			
	        if (convertView == null) {
	           convertView = LayoutInflater.from(getContext()).inflate(R.layout.host_list_row_share, parent, false);
	        }
	        // Lookup view for data population
	        TextView tvName = (TextView) convertView.findViewById(R.id.hostnametextView);
	        TextView tvip = (TextView) convertView.findViewById(R.id.hostiptextView);
	        TextView tvid = (TextView) convertView.findViewById(R.id.hostidtextView);
	        TextView tvwifi = (TextView) convertView.findViewById(R.id.wifitextView);
	        // Populate the data into the template view using the data object
	        tvName.setText(desktop.name);
	        tvip.setText(desktop.ip);
	        tvid.setText(""+desktop.uuid);
	        tvwifi.setText("");
			
			return convertView;
		}    	
    }
    
    private class DiscoveryTask extends AsyncTask<Void, Void, Void> {
		private DatagramSocket serverSocketUDP;
		private boolean isStopped = false;
		private DiscoveredHostsAdapter dha;

		public DiscoveryTask(DiscoveredHostsAdapter dha) {
			this.dha = dha;
		}

		@Override
		protected Void doInBackground(Void... params) {
			Log.d("Discovery: ", "start UDP Server");	
			String data = "0::deskcon";
			try {		
				// Send "Ask for Hosts" Broadcast
				serverSocketUDP = new DatagramSocket(5108); 
				serverSocketUDP.setBroadcast(true);
				
				InetAddress local = getBroadcast();			
				
				// has a broadcast address been found
				if (local == null) { 
					Log.d("Discovery: ", "no Broadcast Address found");
					return null; 
				}
				
				DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), local, 5108);
				serverSocketUDP.send(packet);
				
			} catch (Exception e) {
				e.printStackTrace();
				Log.d("Discovery: ", "could not start");	
				return null;
			} 
			
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			MulticastLock lock = wifi.createMulticastLock("net.screenfreeze.deskcon");
			lock.acquire();
			
			// Receive responses from desktop hosts
			while (!isStopped) {
				byte[] receiveData = new byte[128];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				
				try {
					serverSocketUDP.receive(receivePacket);
				} catch (Exception e) {}
				
                final InetAddress address = receivePacket.getAddress();
               
                String msg = new String(receiveData, 0, receivePacket.getLength());
				
				// Check for valid msg
				boolean isvalismsg = msg.split("::").length == 3;
				
				// Process msg if it is not our own broadcast msg
				if (!msg.equals(data) && !isStopped && isvalismsg) {
					
					final DesktopHost desktop = handleReceiveUdp(msg, address);               
	                Log.d("udp from: ", "msg "+msg+"  "+address);
	                
	                runOnUiThread(new Runnable() {
						
						@Override
						public void run() {
							dha.add(desktop);
						}
					});   
				}    
			}
			lock.release();
			
			return null;
		}
		
		private DesktopHost handleReceiveUdp(String msg, InetAddress address) {
			String[] split = msg.split("::");
			long uuid = Long.parseLong(split[0]);
			String hostname = split[1];
			int port = Integer.parseInt(split[2]);
			
			return new DesktopHost(hostname, ""+address.getHostAddress(), port, uuid);
		}
		
		// force server stop
		private void stopServer() {
			isStopped = true;
			try {
				serverSocketUDP.close();
			}
			catch (Exception e) {}
		}
    	
    }

    public class HostsAdapter extends CursorAdapter {

		@SuppressWarnings("deprecation")
		public HostsAdapter(Context context, Cursor c) {
			super(context, c);
		}

		@Override
		public void bindView(View view, final Context context, Cursor cursor) {
			TextView idtv = (TextView) view.findViewById(R.id.hostidtextView);
			TextView nametv = (TextView) view.findViewById(R.id.hostnametextView);
			TextView iptv = (TextView) view.findViewById(R.id.hostiptextView);
			TextView wifitv = (TextView) view.findViewById(R.id.wifitextView);
			ImageView deleteiv = (ImageView) view.findViewById(R.id.deletehostimageView);
			final long id = cursor.getLong(0);
			
			nametv.setText(cursor.getString(1));
			idtv.setText(String.valueOf(cursor.getLong(4)));
			iptv.setText(cursor.getString(2));
			wifitv.setText(cursor.getString(6));
					
			deleteiv.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					showRemoveHostDialog(context, id);
				}
			});			
		}
		
		// remove Host Dialog
		private void showRemoveHostDialog(Context context, final long id) {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
			alertDialogBuilder.setTitle("Remove Desktop");
			alertDialogBuilder.setMessage("You want to remove\n"+id+" ?");			
			alertDialogBuilder.setCancelable(true);
			alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dbhelper.removeHost(id);
					authenticationmanager.deleteCertificate(id);
					loadHostlist();			
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

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
		    LayoutInflater inflater = LayoutInflater.from(context);

		    View v = inflater.inflate(R.layout.host_list_row, parent, false);
		    
			return v;
		}
    	
    }    
    
    public interface OnPairListener{
        void onPairingCompleted();
    }
    
    public class OnPairCallback implements OnPairListener{
        @Override
        public void onPairingCompleted() {
            loadHostlist();
        }
    }
    
    public class DesktopHost {
        public String name;
        public String ip;
        public int port;
        public long uuid;

        public DesktopHost(String name, String ip, int port, long uuid) {
           this.name = name;
           this.ip = ip;
           this.port = port;
           this.uuid = uuid;
        }    	
    }
    
    public static InetAddress getBroadcast(){
    InetAddress found_bcast_address=null;
     System.setProperty("java.net.preferIPv4Stack", "true"); 
        try
        {
          Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();
          while (niEnum.hasMoreElements())
          {
            NetworkInterface ni = niEnum.nextElement();
            if(!ni.isLoopback()){
                for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses())
                {
                  found_bcast_address = interfaceAddress.getBroadcast();               
                }
            }
          }
        }
        catch (SocketException e)
        {
          e.printStackTrace();
        }

        return found_bcast_address;
    }
    
    public InetAddress getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress;
                    }
                }
            }
        } catch (SocketException ex) {}
        return null;
    }
}


