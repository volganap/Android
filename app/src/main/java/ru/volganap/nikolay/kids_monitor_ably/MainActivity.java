package ru.volganap.nikolay.kids_monitor_ably;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements KM_Constants{

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public static final int PERMISSIONS_REQUEST_SEND_SMS_PARENT =0;
    public static final int PERMISSIONS_REQUEST_SEND_SMS_KID =2;
    public static final String REQUEST_NET ="net";
    public static final String REQUEST_SMS ="sms";
    public static final String REQUEST_SERVER ="server";
    public static String DEFAULT_SERVER_DATA_DELAY = "12";
    public static final String SERVER_SINGLE_REQUEST ="single";
    public static final String SERVER_MULTIPLE_REQUEST ="multiple";
    public static final String SOURCE_SERVER = ", источник: server";
    public static final String DATA_IS_READY ="Данные получены. ";
    public static final String DATA_REQUEST_PROCESSING =" Идет обработка запроса:...";
    public static final String NE_REQ_NOT_SENT ="Ошибка сети. Запрос не отправлен. Выберите в настройках <Отправлять запрос через СМС>";

    private SharedPreferences sharedPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
    BroadcastReceiver mainBroadcastReceiver;
    TextView tv_state;
    private Button bt_getplace, bt_start_sv, bt_send_setup, bt_get_markers, bt_get_data_server, bt_check_connection;
    private Boolean perms_granted = false;
    private Channel channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_state = findViewById(R.id.tv_state);
        bt_getplace = findViewById(R.id.bt_getplace);
        bt_send_setup = findViewById(R.id.bt_send_setup);
        bt_get_markers = findViewById(R.id.bt_get_markers);
        bt_get_data_server = findViewById(R.id.bt_get_data_server);
        bt_start_sv = findViewById(R.id.bt_start_sv);
        bt_check_connection = findViewById(R.id.bt_check_connection);

        //Init BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_FROM_BR);
        filter.addAction(ACTION_FROM_OKHTTP);
        mainBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    String sender = intent.getStringExtra(SENDER);
                    String message = intent.getStringExtra(MESSAGE);
                    String action = intent.getAction();
                    Log.d(LOG_TAG, "Service: Get back with Sender: " + sender + ", action: " + action + ", command: " + message);
                    replyRecieved (message);
                }
            }
        };
        registerReceiver(mainBroadcastReceiver, filter);

        try {
            String ably_key = getApplicationContext().getResources().getString(R.string.ably_key);
            AblyRealtime ablyRealtime = new AblyRealtime(ably_key);
            //AblyRealtime ablyRealtime = new AblyRealtime(ABLY_API_KEY);

            channel = ablyRealtime.channels.get(ABLY_ROOM);
            channel.subscribe(KID_PHONE, messages -> {
                    Log.d(LOG_TAG, "Main - Ably message received: " + messages.name + " - " + messages.data);
                    if ((messages.name).equals(KID_PHONE) && (sharedPrefs.getBoolean(PREF_USER, false))) {
                        String loc_mes = messages.data.toString();
                        //onEvent(new EventBus_Parent(loc_mes));
                        //EventBus.getDefault().postSticky(new EventBus_Parent(loc_mes));

                        Handler handler = new Handler(Looper.getMainLooper());
                        Runnable myRunnable = () -> {
                            try {
                                replyRecieved (loc_mes);
                            } catch (Exception e) {
                                Log.d(LOG_TAG, "Main: Handler: Ably_message EXCEPTION: " + e.toString());
                            }
                        };
                        handler.post(myRunnable);
                    }
            });

        } catch (AblyException e) {
            e.printStackTrace();
        }

        //Init Settings Preferences
        sharedPrefs = getSharedPreferences(PREF_ACTIVITY, MODE_PRIVATE);
        prefChangeListener = (sharedPreferences, key) -> {
            Log.d(LOG_TAG, "Main - prefChangeListener triggered on: " +key);
            tv_state.setText("");
                if (key.equals(PREF_USER)) {
                    Log.d(LOG_TAG, "On Preferences changed");
                    setButtons(perms_granted); //State of Buttons depend on user mode
                }
        };

        sharedPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener);
        setButtons(perms_granted);
        checkLocationPermissions(MY_PERMISSIONS_REQUEST_LOCATION);

        // Get Kid's Place Button Click
        bt_getplace.setOnClickListener(v ->  sendCommandToKid(COMMAND_GET_POSITION));

        // Send Kid's Setup
        bt_send_setup.setOnClickListener(v -> sendCommandToKid(sharedPrefs.getString(TIMER_DELAY, "0")));

        // Start*stop Service Button click
        bt_start_sv.setOnClickListener((View v) -> {
            if (isKidServiceRunning(KidService.class)) {
                 stopService(new Intent(getApplicationContext(), KidService.class));
            } else {
                 startService(new Intent(getApplicationContext(), KidService.class));
            }
            showServiceState();
        });
        // Send message to Server
        bt_get_data_server.setOnClickListener(v -> getBackWithServer(SERVER_SINGLE_REQUEST, ""));

        // Get Map Markers from the Server
        bt_get_markers.setOnClickListener(v -> getBackWithServer(SERVER_MULTIPLE_REQUEST, ""));

        // Check the connection to the Kid phone
        bt_check_connection.setOnClickListener(v -> sendCommandToKid(COMMAND_CHECK_IT));
    }

    // Send a command to the Kid
    private void sendCommandToKid(String rq_command) {
        String kid_phone = sharedPrefs.getString(KID_PHONE, "" );
        if (kid_phone == null) {
            Toast.makeText(getApplicationContext(), "Сначала выберите в настройках номер телефона ребенка", Toast.LENGTH_LONG).show();
            return;
        }
        String parent_phone = sharedPrefs.getString(PARENT_PHONE, "" );
        String rq_message = COMMAND_BASE + STA_SIGN + rq_command + STA_SIGN + parent_phone + STA_SIGN + kid_phone;
        String status_mes = DATA_REQUEST_PROCESSING + ". Команда: " + rq_message;
        refreshStatus(status_mes);
        String rq_mode = sharedPrefs.getString(PREF_REQUEST, "");
        switch (rq_mode) {
            case REQUEST_NET:
                try { // ABLY PUBLISH a message
                    channel.publish(PARENT_PHONE, rq_message, new CompletionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(LOG_TAG,"Main - onSuccess - Message sent: " + rq_command);
                        }
                        @Override
                        public void onError(ErrorInfo reason) {
                            Log.d(LOG_TAG,"Main - onError - Message not sent, error occurred: " + reason.message);
                            refreshStatus(NE_REQ_NOT_SENT);
                        }
                    });
                } catch (AblyException e) {
                    refreshStatus(NE_REQ_NOT_SENT);
                    e.printStackTrace();
                }
                break;
            case REQUEST_SERVER:
            case REQUEST_SMS:
                 sendSMSMessage(kid_phone, rq_message);

                 int server_delay = Integer.parseInt(sharedPrefs.getString(SERVER_DELAY_TITLE, DEFAULT_SERVER_DATA_DELAY));
                 if (rq_mode.equals(REQUEST_SERVER)) {   // get the location from server in N seconds
                    Handler handler = new Handler();
                    handler.postDelayed(()-> getBackWithServer(SERVER_SINGLE_REQUEST, ""), server_delay * 1000);                 }

                break;
        }
    }

    private void getBackWithServer(String command, String value) {// Get the last known location with OkHttp
        String user = PARENT_PHONE;
        new OkHttpRequest().serverGetback(getApplicationContext(), user, command, value);
        refreshStatus(DATA_REQUEST_PROCESSING);
    }

    private void setButtons (Boolean perms_mode) {
        Boolean mode = sharedPrefs.getBoolean(PREF_USER, false);
        if (perms_mode) {    //Permissions are GRANTED
            if (mode) {
                bt_start_sv.setVisibility(View.INVISIBLE);
                bt_getplace.setVisibility(View.VISIBLE);
                bt_send_setup.setVisibility(View.VISIBLE);
                bt_get_markers.setVisibility(View.VISIBLE);
                bt_get_data_server.setVisibility(View.VISIBLE);
                bt_check_connection.setVisibility(View.VISIBLE);
            } else {
                bt_start_sv.setVisibility(View.VISIBLE);
                bt_getplace.setVisibility(View.INVISIBLE);
                bt_send_setup.setVisibility(View.INVISIBLE);
                bt_get_markers.setVisibility(View.INVISIBLE);
                bt_get_data_server.setVisibility(View.INVISIBLE);
                bt_check_connection.setVisibility(View.INVISIBLE);
            }
        }   else {      //Permissions are DENIED
            bt_getplace.setVisibility(View.INVISIBLE);
            bt_start_sv.setVisibility(View.INVISIBLE);
            bt_send_setup.setVisibility(View.INVISIBLE);
            bt_get_markers.setVisibility(View.VISIBLE);
            bt_get_data_server.setVisibility(View.VISIBLE);
            bt_check_connection.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mymenu, menu);
        Log.d(LOG_TAG, "onCreateOptionsMenu: "+super.onCreateOptionsMenu(menu));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.set_item:
                startActivity(new Intent(this, PrefActivity.class));
                break;
            case R.id.serv_config_item:
                getBackWithServer(CHANGE_CONFIG_SERVER, sharedPrefs.getString(MARKER_MAX_NUMBER, ""));
                break;
            case R.id.version:
                Toast.makeText(getApplicationContext(), getApplicationContext().getResources().getString(R.string.version), Toast.LENGTH_LONG).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkSMSPermissions(int requestCode) {
        final int reqCode = requestCode;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            //permissions NOT granted . If permissions are NOT granted, ask for permissions
            Toast.makeText(getApplicationContext(), "Please enable permissions", Toast.LENGTH_LONG).show();
            Log.d(LOG_TAG, "SMS: PERMISSION_DENIED!");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                new AlertDialog.Builder(this)
                        .setTitle("Permissions request")
                        .setMessage("we need your permission for Read Phone State and SMS delivering")
                        .setPositiveButton("Ok, I agree", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE}, reqCode);
                            }
                        })
                        .create().show();
            } else { // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE}, reqCode);
            }
        } else {
            Log.d(LOG_TAG, "SMS: PERMISSION_GRANTED!");
            chooseFromTo(requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(LOG_TAG, "onRequestPermissionsResult "+ requestCode);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // permission was granted, yay! Do the contacts-related task you need to do.
            Log.d(LOG_TAG, "Level of onRequestPermissionsResult: Permission " + requestCode + " GRANTED: " + grantResults);
            chooseFromTo(requestCode);
        } else {
            Log.d(LOG_TAG, "Level of onRequestPermissionsResult: Permission " + requestCode + " DENIED ");
        }
    }

    private void checkLocationPermissions(int requestCode) {
        final int reqCode = requestCode;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //permissions NOT granted . If permissions are NOT granted, ask for permissions
            Toast.makeText(getApplicationContext(), "Please enable permissions", Toast.LENGTH_LONG).show();
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Permissions request")
                        .setMessage("we need your permission for location in order to show you this example")
                        .setPositiveButton("Ok, I agree", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, reqCode);
                            }
                        })
                        .create().show();
            } else { // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, requestCode);
            }
        } else { //
            Log.d(LOG_TAG, "checkLocationPermissions :Location is GRANTED");
            chooseFromTo(requestCode);
        }
    }

    private void chooseFromTo(int request_code) {
        switch (request_code) {
            case PERMISSIONS_REQUEST_SEND_SMS_PARENT:
                perms_granted = true;
                setButtons(perms_granted);
                break;
            case PERMISSIONS_REQUEST_SEND_SMS_KID:
                checkSMSPermissions(PERMISSIONS_REQUEST_SEND_SMS_PARENT);
                break;
            case MY_PERMISSIONS_REQUEST_LOCATION:
                checkSMSPermissions(PERMISSIONS_REQUEST_SEND_SMS_KID);
                break;
        }
    }

    //Perform actions after reply came from Kid or Server
    public void replyRecieved(String location_message){
        Log.d(LOG_TAG, "MainActivity: replyRecieved is worked, position is:  " + location_message);

        String [] complex_message = location_message.split(STA_SIGN);
        String status_state = complex_message[0];
        String source = SOURCE_SERVER;
        String loc_array []={""};
        showListLocations(null);
        switch (status_state) {
            // Data from Server
            case OK_STATE_PARENT:
                //Multiple records
                if (complex_message[1].startsWith("[")) {
                    GsonBuilder builder = new GsonBuilder();
                    Gson gson = builder.create();
                    loc_array = gson.fromJson(complex_message[1], String[].class);
                // Single record
                } else {
                    source = ", источник: " + complex_message[complex_message.length-1];
                    loc_array[0] = complex_message[1];
                }
                showListLocations(loc_array);
                break;
            case EMPTY_STORAGE_STATE:
            case NET_ERROR_STATE:
                refreshStatus(status_state + source);
                break;
            case CONFIG_SERVER_STATE:
                refreshStatus(status_state + complex_message[1] + source);
                break;
            //Data from Kid
            case OK_STATE_KID:
                source = ", источник: " + complex_message[complex_message.length-1];
                refreshStatus(DATA_IS_READY + source);
                loc_array[0] = complex_message[1];
                showListLocations(loc_array);
                break;
            case NET_ERROR_GOT_LOCATION_STATE:
                source = ", источник: " + complex_message[complex_message.length-1];
                refreshStatus(DATA_IS_READY + NET_ERROR_STATE + source);
                loc_array[0] = complex_message[1];
                showListLocations(loc_array);
                break;
            case TIMER_IS_SETUP_STATE:
                source = ", источник: " + complex_message[complex_message.length-1];
                refreshStatus(status_state + complex_message[1] + source);
                break;
            case NO_CHANGE_STATE:
            case NO_LOCATION_FOUND_STATE:
            case CONFIRM_CONNECTION:
            case LOCATION_IS_TURNED_OFF:
            default:
                source = ", источник: " + complex_message[complex_message.length-1];
                refreshStatus(status_state + source);
                break;
        }
    }

    public void showListLocations(String [] loc_array) {
        ListView lvMain = findViewById(R.id.listView);
        if (loc_array == null) {
            lvMain.setVisibility(View.INVISIBLE);
            return;
        } else {
            lvMain.setVisibility(View.VISIBLE);
        }
        String[] from = { ATTRIBUTE_NAME_LAT, ATTRIBUTE_NAME_LONG, ATTRIBUTE_NAME_DATE,
                ATTRIBUTE_NAME_ACCU, ATTRIBUTE_NAME_BATT };
        int[] to = { R.id.list_lat, R.id.list_long, R.id.list_date, R.id.list_accu, R.id.list_batt };
        ArrayList<String []> record = new ArrayList<>();
        int loc_array_length = loc_array.length;
        for(int i=0; i < loc_array_length; i++) {
            String [] helper_mes = loc_array[i].split(REG_SIGN);
            //Check for if the Data of chosen Kid's phone
            record.add(helper_mes);
        }

        if (loc_array_length <2) {
            refreshStatus(DATA_IS_READY + record.get(0)[5]);
        } else {
            refreshStatus(DATA_IS_READY + SOURCE_SERVER);
        }

        ArrayList<Map<String, String>> data = new ArrayList<>(loc_array_length);
        Map<String, String> m;
        for (int i = 0; i < loc_array_length; i++) {
            m = new HashMap<>();
            m.put(ATTRIBUTE_NAME_LAT, record.get(i)[0]);
            m.put(ATTRIBUTE_NAME_LONG, record.get(i)[1]);
            m.put(ATTRIBUTE_NAME_DATE, record.get(i)[3]);
            m.put(ATTRIBUTE_NAME_ACCU, record.get(i)[2]);
            m.put(ATTRIBUTE_NAME_BATT, record.get(i)[4]);
            data.add(m);
        }
        SimpleAdapter adapter = new SimpleAdapter(this, data, R.layout.list_item, from, to);
        lvMain.setAdapter(adapter);

        // Show markers on the map
        if (sharedPrefs.getBoolean(BROWSER_MODE, false)) {
            Intent maps_activity = new Intent(this, MapsActivity.class);
            String dataAsJson = new Gson().toJson(data);
            maps_activity.putExtra("loc_data", dataAsJson);
            startActivity(maps_activity);
        }
    }

    public void refreshStatus(String status) {
        tv_state.setText(status);
    }

    protected void sendSMSMessage (String phoneNo, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, message, null, null);
            Toast.makeText(getApplicationContext(), "SMS sent.", Toast.LENGTH_LONG).show();
            Log.d(LOG_TAG, "MainActivity: SMS sent to number: " + phoneNo + " with message: " + message);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),"Ошибка отправки запроса через СМС", Toast.LENGTH_LONG).show();
            e.printStackTrace();
            Log.d(LOG_TAG, "MainActivity: SMS failed. SMS Exception: " + e.toString());
        }
    }

    protected void showServiceState () {
        if (isKidServiceRunning(KidService.class)) {
            Log.d(LOG_TAG, "MainActivity: Service is running");
            bt_start_sv.setText(R.string.stop_sv);
            bt_start_sv.setTextColor(Color.RED);
        } else {
            Log.d(LOG_TAG, "MainActivity: Service is stopped");
            bt_start_sv.setText(R.string.start_sv);
            bt_start_sv.setTextColor(Color.BLACK);
        }
    }

    private boolean isKidServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "MainActivity: onResume ");
        showServiceState();
    }

    @Override
    public void onDestroy() {
        channel.unsubscribe();
        unregisterReceiver(mainBroadcastReceiver);
        Log.d(LOG_TAG, "MainActivity: onDestroy ");
        super.onDestroy();
    }

}