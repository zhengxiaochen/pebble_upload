package com.pebble.pebupload;


import java.io.File;
import java.io.FilenameFilter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;

public class MyService extends Service {	
	
	public static final String TAG = "MyService";
	Timer timer;	
	TimerTask timerTask;
    Timer timer_peb_app;
    TimerTask timerTask_peb_app;
	private static Boolean timerstatus=false;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss"); //Set the format of the .txt file name.
    private BroadcastReceiver updateReceiver;
    private long lastdata_time=0;
    private long current_time=0;

    //final Context context=getApplicationContext();
	@Override
	public void onCreate() {
		super.onCreate();
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        //***Create foreground notification to keep the service active and avoid been killed***
		Intent notiIntent = new Intent(this, MainActivity.class);
	    PendingIntent pendIntent = PendingIntent.getActivity(this, 0, notiIntent, 0);
		Notification notifi = new NotificationCompat.Builder(this)
			         .setContentTitle("Uploading")
			         .setContentText("Uploading Pebbledata...")
			         .setSmallIcon(R.drawable.ic_launcher)
			         .setContentIntent(pendIntent)
			         //.setLargeIcon(R.drawable.ic_launcher)
			         .build(); // available from API level 4 and onwards
		startForeground(2, notifi);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

        restartPebApp(); //set a timertask to restart the pebble app if it is not running
		//http://stackoverflow.com/questions/14364632/update-active-activity-ui-with-broadcastreceiver
		updateReceiver=new BroadcastReceiver() {
	        @Override
	        public void onReceive(Context context, Intent intent) {
				if (timerstatus==false){
					startTimer();
				}
	        }
	    };
	    IntentFilter updateIntentFilter=new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
	    registerReceiver(updateReceiver, updateIntentFilter);
		return super.onStartCommand(intent, flags, startId);

	}

    public void restartPebApp(){
        timer_peb_app =new Timer();
        System.out.println("restarttimer begin..");
        ini_restartPebApp_Task();
        timer_peb_app.schedule(timerTask_peb_app, 90000, 30000);
    }

    public void ini_restartPebApp_Task(){
        timerTask_peb_app = new TimerTask(){
            public void run(){
                //restart the pebble app
                final long lastdata=lastdata_time;
                current_time=System.currentTimeMillis()-lastdata;
                System.out.println("lastdata_time: "+lastdata+"time difference: "+Long.toString(current_time));

                //if there is no data coming even more time, try to restart the phone app to recover everything
                if (current_time>30000 && current_time< 300000 && lastdata>0){
                    RestartBroadcastMessage(current_time);
                }
            }
        };
    }

	//Send restart app message
	private void RestartBroadcastMessage(long time_gap) {

            Intent intent = new Intent("APP_RESTART_BROADCAST");
            intent.putExtra("TIMEGAP", time_gap);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		System.out.println(TAG + "onDestroy() executed");
	    timer.cancel();
	    //timer_peb_app.cancel();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


    //Timer for uploading data
	public void startTimer() {    
	    timerstatus=true;
	    timer = new Timer(); //set a new Timer    		    	
	    initializeTimerTask(); //initialize the TimerTask's job	    	
	    //schedule the timer, after the first 5000ms the TimerTask will run every 60000ms
	    timer.schedule(timerTask, 5000, 60000);     	
	}

	public void initializeTimerTask() {
		timerTask = new TimerTask() {
			public void run() {
				//***Get file list in the folder // stackoverflow.com/questions/8646984/how-to-list-files-in-an-android-directory
				String folderpath = Environment.getExternalStorageDirectory().getPath() + File.separator + "tmp" +  File.separator + "backup";
				try {
					//File file[] = f.listFiles();
					File filegz[] = findergz(folderpath);   //get all the .gz file
					if (filegz.length>0) {			// If there are .gz files, upload them

						for (int j = 0; j < filegz.length; j++) {
							String datapathgz = folderpath + File.separator + filegz[j].getName();
							new RetrieveFeedTask().execute(datapathgz);

							// prepare the UI information
							String Uistr = "Uploading file:" + filegz[j].getName();
							sendBroadcastMessage(Uistr);
						}
                        lastdata_time=System.currentTimeMillis();   //get the data coming time for restarting the pebble app
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.d("Files", e.getLocalizedMessage() );
				}
			}
		};
	}

	//find .gz file
	public File[] findergz( String dirName){
	  	File dir = new File(dirName);
	   	return dir.listFiles(new FilenameFilter() { 
	         public boolean accept(File dir, String filename)
	              { return filename.endsWith(".gz"); }
	   	} );
	}

	//Send UI information back to main activity
	public static final String ACTION_UI_BROADCAST="UI_TEXTVIEW_INFO",
			DATA_STRING="textview_string";

	private void sendBroadcastMessage(String UIstring) {
		if (UIstring != null) {
			Intent intent = new Intent(ACTION_UI_BROADCAST);
			intent.putExtra(DATA_STRING, UIstring);
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
	}


}

//创建同步线程 http://stackoverflow.com/questions/6343166/android-os-networkonmainthreadexception
class RetrieveFeedTask extends AsyncTask<String, Void, Void> {
	protected Void doInBackground(String... datapath) {

		String despath=datapath[0];
		if (despath!= null) {
			File gzfile = new File(despath);
			if (gzfile.exists()) {
				//http://stackoverflow.com/questions/2017414/post-multipart-request-with-android-sdk
				HttpClient client = new DefaultHttpClient();
				try {
					//HttpPost httpPost = new HttpPost("http://apiinas02.etsii.upm.es/pebble/carga_pebble.py"); //check the upload result here: http://138.100.82.184/tmp/
					HttpPost httpPost = new HttpPost("http://pebble.etsii.upm.es/pebble/carga_pebble.py");
					MultipartEntityBuilder builder = MultipartEntityBuilder
							.create();
					builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
					builder.addBinaryBody("file", gzfile,
							ContentType.create("application/x-gzip"),
							gzfile.getName());
					HttpEntity entity = builder.build();
					httpPost.setEntity(entity);
					//System.out.println("executing request for file" + gzfile.getName() + gzfile.length()+httpPost.getRequestLine());
					HttpResponse response = client.execute(httpPost);
					HttpEntity resEntity = response.getEntity();
					String result = EntityUtils.toString(resEntity);
					//System.out.println(result + gzfile.getName());
					//System.out.println(result);
					if (result.contains("OK")) {
						gzfile.delete();
						//gzfile.renameTo(bkpfile);
						//System.out.println(result + gzfile.getName());
					} else {
						System.out.println("upload failed!!  gzfile:"+ gzfile.getName() + " size:"+ gzfile.length());
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("upload exception!!!!  gzfile:"+ gzfile.getName() + " size:" + gzfile.length());
				} finally {
					client.getConnectionManager().shutdown();
				}
			} else {
				System.out.println("NO NEW FILE FOUND!");
			}
		}
		return null;
	}

}