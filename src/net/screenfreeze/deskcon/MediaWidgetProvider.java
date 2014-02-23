package net.screenfreeze.deskcon;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

public class MediaWidgetProvider extends AppWidgetProvider {

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		
	    // Get all ids
	    ComponentName thisWidget = new ComponentName(context,
	        MediaWidgetProvider.class);
	    int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
	    SharedPreferences prefs = context.getSharedPreferences("widget_preferences", 0);
	    
	    for (int widgetId : allWidgetIds) {	
	    	// load widget preferences
	    	String hostname = prefs.getString("hostname"+widgetId, "Host");
	    	Long uuid = prefs.getLong("uuid"+widgetId, 0);
	    	String player = prefs.getString("player"+widgetId, "Default");
	    	
	    	// Build Widget
	        RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
		            R.layout.media_widget);
			remoteViews.setTextViewText(R.id.widgethosttextView, hostname);
			remoteViews.setTextViewText(R.id.widgetplayertextView, player);
	        // Register an onClickListener
	        Intent intent = new Intent(context, MediaWidgetProvider.class);
	        intent.setAction("PLAY");
	        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetId);
	        intent.putExtra("uuid", uuid);
	        intent.putExtra("player", player);

	        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
	            0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	        remoteViews.setOnClickPendingIntent(R.id.PlayButton, pendingIntent);
	        
	        intent.setAction("NEXT");
	        pendingIntent = PendingIntent.getBroadcast(context,
	                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	        remoteViews.setOnClickPendingIntent(R.id.NextButton, pendingIntent);
	        
	        intent.setAction("PREVIOUS");
	        pendingIntent = PendingIntent.getBroadcast(context,
	                0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	        remoteViews.setOnClickPendingIntent(R.id.PrevButton, pendingIntent);
	        
	        appWidgetManager.updateAppWidget(widgetId, remoteViews);	    	
	    }
	    
	}

	@Override
	public void onReceive(Context context, Intent intent) {		
		String command = intent.getAction();
		
		if (!intent.hasExtra("uuid")) {
			super.onReceive(context, intent);  // other Broadcasts
		}
		
		Log.d("Media: ", "control");
		Long uuid = intent.getLongExtra("uuid", 0);
		String player = intent.getStringExtra("player");
		DesktopHostsDBHelper dbhelper = new DesktopHostsDBHelper(context);
		
		Cursor cursor = dbhelper.getHostByIdCursor(uuid);
		if (cursor.getCount() < 1) { return; }
		
		String HOST = cursor.getString(2);
		int PORT = cursor.getInt(3);
		
		cursor.close();
		dbhelper.close();		
		
		sendCommand(context, HOST, PORT, command+"::"+player);		
		
		super.onReceive(context, intent);
	}
	
	private void sendCommand(Context context, String HOST, int PORT, String cmd) {
		Intent i = new Intent(context, StatusUpdateService.class);
		i.putExtra("commandtype", "MEDIACTRL");
		i.putExtra("message", cmd);
		i.putExtra("host", HOST);
		i.putExtra("port", PORT);
		context.startService(i);
	}
}
