package net.screenfreeze.deskcon;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DesktopHostsDBHelper extends SQLiteOpenHelper {

	// Database Version
    private static final int DATABASE_VERSION = 2;
    // Database Name
    private static final String DATABASE_NAME = "hosts.db";
    
    private static final String TABLE = "desktophosts";
    
    // Table Columns names
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_IP = "ip";
    private static final String COLUMN_PORT = "port";
    private static final String COLUMN_UUID = "uuid";
    private static final String COLUMN_WIFI = "wifi";
    private static final String COLUMN_FINGERPRINT = "fingerprint";
    
    
	public DesktopHostsDBHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
        // SQL statement to create book table
        String CREATE_BOOK_TABLE = "CREATE TABLE desktophosts ( " +
                COLUMN_ID+" INTEGER PRIMARY KEY AUTOINCREMENT, " + 
                COLUMN_NAME+" TEXT, "+
                COLUMN_IP+" TEXT NOT NULL, "+
                COLUMN_PORT+" INTEGER NOT NULL, "+
                COLUMN_UUID+" INTEGER NOT NULL, "+
                COLUMN_FINGERPRINT+" VARCHAR, "+
                COLUMN_WIFI+" TEXT )";
 
        // create books table
        db.execSQL(CREATE_BOOK_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newversion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS desktophosts");
 
        // create fresh table
        this.onCreate(db);
	}
	
	public void addHost(long uuid, String name, String ip, int port, 
			String wifi, String mac, String fingerprint) {
		SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        //values.put(COLUMN_ID, id);
        values.put(COLUMN_NAME, name);
        values.put(COLUMN_UUID, uuid);
        values.put(COLUMN_IP, ip);
        values.put(COLUMN_PORT, port);
        values.put(COLUMN_WIFI, wifi);
        values.put(COLUMN_FINGERPRINT, fingerprint);
        
		db.insert("desktophosts", null, values);
		Log.d("DB add: ", "added host");
	}
	
	public void removeHost(long id) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		String sql = "DELETE FROM " + TABLE + " WHERE " +COLUMN_ID+ "=" +id;
		
		Log.d("DB Delete: ", "delete by id");
		db.execSQL(sql);
	}
	
	public void updateHost(long id, String ip, int port, String wifi) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
        values.put(COLUMN_IP, ip);
        values.put(COLUMN_PORT, port);
        values.put(COLUMN_WIFI, wifi);
		
		db.update("desktophosts", values, COLUMN_ID+ "="+id, null);
		
		Log.d("DB Update: ", "update by id");
	}
	
	public void clearDB() {
		SQLiteDatabase db = this.getWritableDatabase();
		String sql = "DELETE FROM " + TABLE;
		
		Log.d("DB Clear: ", "delete all entrys");
		db.execSQL(sql);
	}
	
	public Cursor getAllCursor() {
		SQLiteDatabase db = this.getReadableDatabase();
		String sql = "SELECT * FROM " + TABLE;
		Cursor cursor = db.rawQuery(sql, null);
		cursor.moveToFirst();
		return cursor;
	}
	
	public Cursor getHostByIdCursor(long id) {
		SQLiteDatabase db = this.getReadableDatabase();
		String sql = "SELECT * FROM " + TABLE 
				+ " WHERE " + COLUMN_ID + "="+id;
		Cursor cursor = db.rawQuery(sql, null);
		cursor.moveToFirst();
		return cursor;
	}
	
	public Cursor getHostsOnWifiCursor(String ssid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String sql = "SELECT * FROM " + TABLE 
					+ " WHERE " + COLUMN_WIFI + "='"+ssid+"'"
					+ " OR " + COLUMN_WIFI + "=''";
		Cursor cursor = db.rawQuery(sql, null);
		cursor.moveToFirst();
		return cursor;
	}
}
