package net.screenfreeze.deskcon;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

public class EventBroadcastReceiver extends BroadcastReceiver{
	private static boolean ring=false;
    private static boolean callReceived=false;    
    private static String callerId;
    private static SharedPreferences sharedPrefs;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d("Event: ", "received");
		Bundle extras = intent.getExtras();
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		// permissions
		boolean send_messages = sharedPrefs.getBoolean("send_messages", false);
		boolean send_calls = sharedPrefs.getBoolean("send_calls", false);
		
		// Event Power status
		if (extras == null) {
			startStatusUpdateService(context);
		}      
		//Event is phonecall
		else if (extras.getString(TelephonyManager.EXTRA_STATE) != null) {
			if (send_calls) handleCall(context, extras);
		}      
		// Event is SMS received
		else if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
			if (send_messages) handleSMS(context, extras);
		}
		// just update status
		else {
			startStatusUpdateService(context);
		}
	}
	
	private void handleCall(Context context, Bundle data) {
		Log.d("Event: ", "handle call");
		String callstate = data.getString(TelephonyManager.EXTRA_STATE);
		// is ringing
	  	if (callstate.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
	  		String number = data.getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
	  		ring = true;
	  		callerId = number;
	  		
	  	    // get Contact Name if present	    	    
	  	    if (getContactName(context, number) != null) {
	  	    	callerId = getContactName(context, number);
	  	    }
	  	    startServiceCommand(context, "CALL", callerId);
	  	}
	  	// end ringing
	  	if (callstate.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
	             callReceived=true;
	      }
	  	// is idle again
	  	if (callstate.equals(TelephonyManager.EXTRA_STATE_IDLE)){
	        // If phone was ringing(ring=true) and not received,then it is a missed call
	         if(ring==true&&callReceived==false){
	      	   startServiceCommand(context, "MIS_CALL", callerId);
	         }
	         //reset values
	         callReceived = false;
	         ring = false;
	    }	    
	}
	
	private void handleSMS(Context context, Bundle data) {
		Log.d("Event: ", "handle sms");
	  	Object[] pdus = (Object[]) data.get("pdus");
	    SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[0]);	    	    
	    String number = sms.getOriginatingAddress();
	    String contactname = "";
	    JSONObject jobject = new JSONObject();
	    
	    // get Contact Name if present	    	    
	    if (getContactName(context, number) != null) {
	    	contactname = getContactName(context, number);
	    }
	    
        //create json object to send	            
        try {        
        	jobject.put("name", contactname);
        	jobject.put("number", number);
			jobject.put("message", sms.getMessageBody());
		} catch (JSONException e) {
			e.printStackTrace();
		}
        
        startServiceCommand(context, "SMS", jobject.toString());		
	}
	
	//send specific Command
	private void startServiceCommand(Context context, String cmd, String mess) {			
		Intent i = new Intent(context, StatusUpdateService.class);
		i.putExtra("commandtype", cmd);
		i.putExtra("message", mess);

		context.startService(i);
	}
	
	//start without infos, the Service will retrive current stats
	private void startStatusUpdateService(Context context) {			
    	Intent i = new Intent(context, StatusUpdateService.class);
    	i.putExtra("dummy", true); // jast to have Extras on Intent for now
    	context.startService(i);
	}
	
	public static String getContactName(Context context, String phoneNumber) {
	    ContentResolver cr = context.getContentResolver();
	    Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
	    Cursor cursor = cr.query(uri, new String[]{PhoneLookup.DISPLAY_NAME}, null, null, null);
	    if (cursor == null) {
	        return null;
	    }
	    String contactName = null;
	    if(cursor.moveToFirst()) {
	        contactName = cursor.getString(cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME));
	    }

	    if(cursor != null && !cursor.isClosed()) {
	        cursor.close();
	    }

	    return contactName;
	}
}
