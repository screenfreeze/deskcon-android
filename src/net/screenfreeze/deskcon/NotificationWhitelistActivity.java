package net.screenfreeze.deskcon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


public class NotificationWhitelistActivity extends Activity {
	private PackageManager packageManager = null;
	private List<ApplicationInfo> applist = null;
	private ApplicationAdapter listadaptor = null;
	private SharedPreferences sharedPrefs;
	private Editor sharedPrefsEditor;
	private static ArrayList<String> whitelist;
	private ListView applistview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.notification_whitelist);
		
		applistview = (ListView) findViewById(R.id.applistview);
		
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefsEditor = sharedPrefs.edit();
		packageManager = getPackageManager();
		
		String whiteliststr = sharedPrefs.getString("notification_whitelist", "");
		
		if (whiteliststr.equals("")) {
			whitelist = new ArrayList<String>();
		}
		else {
			whitelist = new ArrayList<String>(Arrays.<String>asList((whiteliststr.split(", "))));
		}
		
		new LoadApplications().execute();
	}
	
	@Override
	protected void onDestroy() {
		String whiteliststr = whitelist.toString();
		whiteliststr = whiteliststr.replace("[", "");
		whiteliststr = whiteliststr.replace("]", "");

		sharedPrefsEditor.putString("notification_whitelist", whiteliststr);
		sharedPrefsEditor.commit();
		super.onDestroy();
	}

	private List<ApplicationInfo> checkForLaunchIntent(List<ApplicationInfo> list) {
		ArrayList<ApplicationInfo> applist = new ArrayList<ApplicationInfo>();
		for (ApplicationInfo info : list) {
			try {
				if (null != packageManager.getLaunchIntentForPackage(info.packageName)) {
					applist.add(info);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return applist;
	}
	
	private class LoadApplications extends AsyncTask<Void, Void, Void> {
		private ProgressDialog progress = null;

		@Override
		protected Void doInBackground(Void... arg0) {
			applist = checkForLaunchIntent(packageManager.getInstalledApplications(PackageManager.GET_META_DATA));
			listadaptor = new ApplicationAdapter(getApplicationContext(),
					R.layout.app_list_row, applist);

			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			applistview.setAdapter(listadaptor);
			progress.dismiss();
			super.onPostExecute(result);
		}

		@Override
		protected void onPreExecute() {
			progress = ProgressDialog.show(NotificationWhitelistActivity.this, null,
					"Loading application info...");
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
		}
		 
	 }
	 
	public class ApplicationAdapter extends ArrayAdapter<ApplicationInfo> {
			private List<ApplicationInfo> appsList = null;
			private Context context;
			private PackageManager packageManager;

			public ApplicationAdapter(Context context, int textViewResourceId,
					List<ApplicationInfo> appsList) {
				super(context, textViewResourceId, appsList);
				this.context = context;
				this.appsList = appsList;
				packageManager = context.getPackageManager();
			}

			@Override
			public int getCount() {
				return ((null != appsList) ? appsList.size() : 0);
			}

			@Override
			public ApplicationInfo getItem(int position) {
				return ((null != appsList) ? appsList.get(position) : null);
			}

			@Override
			public long getItemId(int position) {
				return position;
			}

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = convertView;
				if (null == view) {
					LayoutInflater layoutInflater = (LayoutInflater) context
							.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = layoutInflater.inflate(R.layout.app_list_row, parent, false);
				}

				ApplicationInfo data = appsList.get(position);
				if (null != data) {
					TextView appName = (TextView) view.findViewById(R.id.app_name);
					TextView packageName = (TextView) view.findViewById(R.id.app_package);
					ImageView iconview = (ImageView) view.findViewById(R.id.app_icon);
					CheckBox check = (CheckBox) view.findViewById(R.id.checkBox);
					
					final String pname= data.packageName;
					
					appName.setText(data.loadLabel(packageManager));
					packageName.setText(data.packageName);
					iconview.setImageDrawable(data.loadIcon(packageManager));
					check.setChecked(whitelist.contains(pname));
					check.setOnClickListener(new OnClickListener() {
						
						@Override
						public void onClick(View view) {
							CheckBox checkbox = (CheckBox) view;
							if (checkbox.isChecked()) {
								whitelist.add(pname);
							}
							else {
								whitelist.remove(pname);
							}							
						}
					});
				}
				return view;
			}
		};
}
