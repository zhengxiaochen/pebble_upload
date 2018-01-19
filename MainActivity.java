package com.pebble.pebupload;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.support.v4.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.widget.Toast;
import android.app.AlarmManager;
import android.app.PendingIntent;



public class MainActivity extends Activity {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss"); //Set the format of the .txt file name.           
    TextView textView1 = null;
    private static Boolean WIFI_UPLAOD=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);    
        //System.out.println("onCreate...");
        setContentView(R.layout.activity_main);          
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0")); 
        //start MyService **background service**           
        Intent startIntent = new Intent(this, MyService.class);
        startService(startIntent); 
        //System.out.println("startService...");

        startIntent.putExtra("WIFI_UPLOAD", WIFI_UPLAOD); //WIFI_UPLOAD: true:only upload under wifi connection; false: all condition
        startService(startIntent);
    }   
    
    @Override
    protected void onResume() {    	
        super.onResume();    
        //System.out.println("onResume...");
        textView1 = (TextView) findViewById(R.id.textView7);	//Pebble ID field

        //***Receive textview information from MyService***
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String UIString = intent.getStringExtra(MyService.DATA_STRING);
                        textView1.setText(UIString);
                    }
                }, new IntentFilter(MyService.ACTION_UI_BROADCAST)
        );

        //***Restart the application when no data coming for a long period
        //http://blog.scriptico.com/01/how-to-restart-android-application/
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        long timegap=intent.getLongExtra("TIMEGAP", 0);
                        if (timegap>60000){
                            //finish();
                            restartApp(300);
                        }
                    }
                }, new IntentFilter("APP_RESTART_BROADCAST")
        );

    }

    public void restartApp(int delay) {
        PendingIntent intent = PendingIntent.getActivity(this.getBaseContext(), 0, new Intent(getIntent()), getIntent().getFlags());
        AlarmManager manager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        manager.set(AlarmManager.RTC, System.currentTimeMillis() + delay, intent);
        //System.exit(0);
        finish();
    }

    @Override
    protected void onPause() {
    	super.onPause();        	 
    }
    
    //TODO onExit unregisterReceiver(mDataLogReceiver);
    @Override
    protected void onDestroy() {
    	super.onDestroy();  
    }
        
    @Override
    public void finish (){  
    	//*********Stop MyService***
    	Intent stopIntent = new Intent(this, MyService.class);  
        stopService(stopIntent); 
        //System.out.println("service is stopped!");
        System.exit(0);        
    }        
 
	//****Double click back key to exit activity***	 
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if(keyCode == KeyEvent.KEYCODE_BACK)
        {  
            exitBy2Click();		//调用双击退出函数
        }
		return false;
	}	
	private static Boolean isExit = false;
	private void exitBy2Click() {
		Timer tExit = null;
		if (isExit == false) {
			isExit = true; // 准备退出
			Toast.makeText(this, "Click again to exit", Toast.LENGTH_SHORT).show();
			tExit = new Timer();
			tExit.schedule(new TimerTask() {
				@Override
				public void run() {
					isExit = false; // 取消退出
				}
			}, 2000); // 如果2秒钟内没有按下返回键，则启动定时器取消掉刚才执行的任务
		} else {
			finish();
			System.exit(0);
		}
	}

}






