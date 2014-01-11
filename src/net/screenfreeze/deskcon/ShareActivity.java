package net.screenfreeze.deskcon;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ShareActivity extends Activity {
	private static String HOST;
	private static int PORT;
	private DesktopHostsDBHelper dbhelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	    final Intent intent = getIntent();	    
		dbhelper = new DesktopHostsDBHelper(this);		
    	String current_wifi = getWifiSSID();
    	
    	Cursor cursor = dbhelper.getHostsOnWifiCursor(current_wifi);
    	int hostcount = cursor.getCount();

    	// set Host and the port accordingly
    	if (hostcount == 0) {
    		this.finish();
    	}
    	else if (hostcount == 1) {
			HOST = cursor.getString(2);
			PORT = cursor.getInt(3);  		
			
    		cursor.close();
			startAHandler(intent);
    	}
    	else if (hostcount > 1) {
    		setContentView(R.layout.share_desktop_hosts);    		
    		    		
    		ListView hostslistview = (ListView) findViewById(R.id.sharehostslistView);
    		final HostsAdapter hostadapter = new HostsAdapter(this, cursor);
    		
    		hostslistview.setAdapter(hostadapter);
    		hostslistview.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int pos, long id) {
					Cursor itemcursor = (Cursor) parent.getItemAtPosition(pos);
					HOST = itemcursor.getString(2);
					PORT = itemcursor.getInt(3);

					itemcursor.close();
					startAHandler(intent);
				}
			});
    	}
    	dbhelper.close();
	}
	
	private void startAHandler(Intent intent) {		
	    String action = intent.getAction();
	    String type = intent.getType();
		
    	if (Intent.ACTION_SEND.equals(action) && type != null) {
	        if ("text/plain".equals(type)) {
	            handleSendText(intent); // Handle text being sent
	        } else {
	            handleSendFile(intent); // Handle single file being sent
	        }
	    }
	    this.finish();
	}
			
	private void handleSendText(Intent intent) {
	    String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
	    
	    if (sharedText != null) {
	    	Intent i = new Intent(this, StatusUpdateService.class);
	    	
	    	//Test if it is a URL
	    	if (testIsURL(sharedText)) {
	    		i.putExtra("commandtype", "URL");
	    		i.putExtra("message", sharedText);
	    		i.putExtra("host", HOST);
	    		i.putExtra("port", PORT);
	    	}
	    	else {
	    		i.putExtra("commandtype", "CLPBRD");
	    		i.putExtra("message", sharedText);
	    		i.putExtra("host", HOST);
	    		i.putExtra("port", PORT);
	    	}			
			startService(i);
	    }
	}

	private void handleSendFile(Intent intent) {
	    Uri fileUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
	    Toast FileNotFoundError = Toast.makeText(getApplicationContext(), 
	            		"File not found", Toast.LENGTH_SHORT);
	    
	    if (fileUri != null) {
	    	Log.d("FileUp: ", fileUri.toString());
	    	String path = getRealPathFromURI(fileUri);
	    	
	    	if (path == null) {	            
	            FileNotFoundError.show();
	    		return;
	    	}

			Intent i = new Intent(this, SendFilesService.class);
			i.putExtra("filepath", path);
    		i.putExtra("host", HOST);
    		i.putExtra("port", PORT);
			startService(i);
	    }
	}

	@SuppressWarnings("unused")
	private void handleSendMultipleImages(Intent intent) {
	    ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
	    if (imageUris != null) {
	        // Update UI to reflect multiple images being shared
	    }
	}
	
	public boolean testIsURL(String str) {
		boolean res = false;
		try {
		    new URL(str);
		    res = true;
		} catch (MalformedURLException e) {
		    res = false;
		}
		return res;
	}
	
	//  Error when picasa content!!!!!!!!!!!!!!!!!!!!!!!!!!
	public String getRealPathFromURI(Uri contentUri) {
		// is file Uri
		if (contentUri.getScheme().equals("file")){
			return contentUri.getPath();
		}
        // is content Uri
        String [] proj={MediaStore.Images.Media.DATA};
        Cursor cursor =getContentResolver().query( contentUri,
                        proj, // Which columns to return
                        null,       // WHERE clause; which rows to return (all rows)
                        null,       // WHERE clause selection arguments (none)
                        null); // Order-by clause (ascending by name)
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        
        String path = cursor.getString(column_index);

        return path;
	}
	
    private String getWifiSSID() {
    	String ssid = null;
    	WifiManager wifiManager = (WifiManager) getApplicationContext()
            		.getSystemService(Context.WIFI_SERVICE);
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            ssid = wifiInfo.getSSID();
        } catch (Exception a) {
        }
        
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
			TextView mactv = (TextView) view.findViewById(R.id.hostmactextView);
			TextView wifitv = (TextView) view.findViewById(R.id.wifitextView);
			ImageView deleteiv = (ImageView) view.findViewById(R.id.deletehostimageView);
			final long id = cursor.getLong(0);
			
			nametv.setText(cursor.getString(1));
			idtv.setText(String.valueOf(id));
			iptv.setText(cursor.getString(2));
			mactv.setText(cursor.getString(4));
			wifitv.setText(cursor.getString(6));
					
			deleteiv.setEnabled(false);
		}
		
		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
		    LayoutInflater inflater = LayoutInflater.from(context);

		    View v = inflater.inflate(R.layout.host_list_row, parent, false);
		    
			return v;
		}    	
    }	
}
