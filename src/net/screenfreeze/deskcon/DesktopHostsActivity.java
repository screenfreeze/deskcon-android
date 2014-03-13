package net.screenfreeze.deskcon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
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
		final String wifi = getWifiSSID();
		wifilockcheckbox.setText("only on "+wifi);		
		
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
			idtv.setText(String.valueOf(id));
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
}
