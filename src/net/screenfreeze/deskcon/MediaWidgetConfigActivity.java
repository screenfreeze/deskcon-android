package net.screenfreeze.deskcon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.TextView;

public class MediaWidgetConfigActivity extends Activity {
	
	private DesktopHostsDBHelper dbhelper;	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.widget_conf_layout);
		
		dbhelper = new DesktopHostsDBHelper(this);
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		final int mAppWidgetId;
		
		if (extras != null) {
		    mAppWidgetId = extras.getInt(
		            AppWidgetManager.EXTRA_APPWIDGET_ID, 
		            AppWidgetManager.INVALID_APPWIDGET_ID);    
		}
		else {
			mAppWidgetId = 0;
		}		
    	
		// get All Hosts
    	Cursor cursor = dbhelper.getAllCursor();
    	int hostcount = cursor.getCount();    	
    	
    	if (hostcount == 0) { // Don't create Widget when no Desktops are registered
    		cursor.close();
    		finish();
    	}
    	else {
			ListView hostslistview = (ListView) findViewById(R.id.widgethostslistView);
			final Spinner playerspinner = (Spinner) findViewById(R.id.playerspinner);

			final ArrayAdapter<CharSequence> playeradapter = ArrayAdapter.createFromResource(this,
			        R.array.playerarray, android.R.layout.simple_spinner_item);
			playeradapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			playerspinner.setAdapter(playeradapter);
			final HostsAdapter hostadapter = new HostsAdapter(this, cursor);
			
			hostslistview.setAdapter(hostadapter);
			hostslistview.setOnItemClickListener(new OnItemClickListener() {
	
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int pos, long id) {
					Cursor itemcursor = (Cursor) parent.getItemAtPosition(pos);
					String hostname = itemcursor.getString(1);
					Long uuid = itemcursor.getLong(0);
					
					itemcursor.close();
					int ipos = playerspinner.getSelectedItemPosition();
					String player = playeradapter.getItem(ipos).toString();
					complete(mAppWidgetId, hostname, uuid, player);
				}
			});
    	}
		
		dbhelper.close();		
	}
	
	@SuppressLint("CommitPrefEdits")
	private void complete(int mAppWidgetId, String hostname, long uuid, String player) {		
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
		RemoteViews remoteViews = new RemoteViews(this.getPackageName(),
				R.layout.media_widget);	
		
		// Build Widget
		remoteViews.setTextViewText(R.id.widgethosttextView, hostname);
		remoteViews.setTextViewText(R.id.widgetplayertextView, player);
        // Register an onClickListener
        Intent intent = new Intent(getApplicationContext(), MediaWidgetProvider.class);
        intent.setAction("PLAY");
        intent.putExtra("uuid", uuid);
        intent.putExtra("player", player);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mAppWidgetId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
            0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.PlayButton, pendingIntent);
        
        intent.setAction("NEXT");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.NextButton, pendingIntent);
        
        intent.setAction("PREVIOUS");
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(),
                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.PrevButton, pendingIntent);
		
		appWidgetManager.updateAppWidget(mAppWidgetId, remoteViews);

		// store widget preferences
		SharedPreferences prefs = getSharedPreferences("widget_preferences", 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong("uuid"+mAppWidgetId, uuid);
        edit.putString("hostname"+mAppWidgetId, hostname);
        edit.putString("player"+mAppWidgetId, player);
        edit.commit();
		
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
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
			
			nametv.setText(cursor.getString(1));
			idtv.setText(String.valueOf(cursor.getLong(4)));
			iptv.setText(cursor.getString(2));
			wifitv.setText(cursor.getString(6));					
		}
		
		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
		    LayoutInflater inflater = LayoutInflater.from(context);

		    View v = inflater.inflate(R.layout.host_list_row_share, parent, false);
		    
			return v;
		}    	
    }	

}
