package ru.volganap.nikolay.kids_monitor_ably;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.Log;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;

public class FindGeoPos implements KM_Constants {
    private static FindGeoPos mInstance;
    public static final long UPDATE_INTERVAL = 1;    // 1 seconds
    public static final long FASTEST_UPDATE_INTERVAL = UPDATE_INTERVAL / 2;
    public static final long MAX_WAIT_TIME = UPDATE_INTERVAL * 2;
    public static final int MIN__LOCATION_TIME_DEFAULT = 15;

    private static FusedLocationProviderClient mFusedLocationClient;
    private static LocationRequest locationRequest;
    private static LocationCallback locationCallback;
    private static Context context;
    private static String sender;
    private static String kid_phone;
    private static int max_loc_time;
    static String message = null;

    public static FindGeoPos getInstance(Context context, String sender, String kid_phone, int max_loc_time) {
        if (mInstance == null) {
            mInstance = new FindGeoPos(context, sender, kid_phone, max_loc_time);
        }
        return mInstance;
    }

    private FindGeoPos(Context context, String sender, String kid_phone, int max_loc_time) {
        this.context = context;
        this.sender = sender;
        this.kid_phone = kid_phone;
        if (max_loc_time < MIN__LOCATION_TIME_DEFAULT) this.max_loc_time = MIN__LOCATION_TIME_DEFAULT;
            else this.max_loc_time = max_loc_time;
        getGeoPosition();
    }

    @SuppressLint("MissingPermission")
    public static void getGeoPosition() {
        // Define LocationRequest
        Log.d(LOG_TAG, "FindGeoPos: getGeoPosition started");

        Handler handler = new Handler();
        Runnable taskRunnable = new Runnable() {
            public void run() {
                Log.d(LOG_TAG, "FindGeoPos: Handler is working");
                if (message == null) {
                    Log.d(LOG_TAG, "FindGeoPos: ACTION_FROM_GEOPOS: No Location sent");
                    Intent intent = new Intent();
                    intent.setAction(ACTION_FROM_GEOPOS);
                    intent.putExtra(SENDER, sender);
                    intent.putExtra(MESSAGE, NO_LOCATION_FOUND_STATE + " ,Battery: " + new CheckForSM().batteryLevel(context));
                    context.sendBroadcast(intent);
                }
                if (mFusedLocationClient != null) {
                    mFusedLocationClient.removeLocationUpdates(locationCallback);
                }
                mInstance = null;
            }
        };
        handler.postDelayed(taskRunnable, max_loc_time * 1000);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        createLocationRequest();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                String latitude, longitude, accuracy;
                if (locationResult == null) {
                    Log.d(LOG_TAG, "FindGeoPos: locationCallback: onLocationResult - NULL");
                } else {
                    Log.d(LOG_TAG, "FindGeoPos: " + locationResult.toString());
                    latitude = String.valueOf(locationResult.getLastLocation().getLatitude());
                    longitude = String.valueOf(locationResult.getLastLocation().getLongitude());
                    accuracy = String.valueOf(Math.round(locationResult.getLastLocation().getAccuracy()));
                    long timestamp = locationResult.getLastLocation().getTime();
                    String time = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(timestamp);
                    //message = latitude + REG_SIGN + longitude + REG_SIGN + accuracy + REG_SIGN + time + REG_SIGN + batteryLevel()+ REG_SIGN + kid_phone;
                    message = latitude + REG_SIGN + longitude + REG_SIGN + accuracy + REG_SIGN + time + REG_SIGN
                            + new CheckForSM().batteryLevel(context)+ REG_SIGN + kid_phone;

                    // Save Location to the Server
                    new OkHttpRequest().serverGetback(context, sender, message, "");
                    if (mFusedLocationClient != null) {
                        mFusedLocationClient.removeLocationUpdates(locationCallback);
                        Log.d(LOG_TAG, "FindGeoPos: RemoveLocationUpdates");
                    }
                    handler.removeCallbacks(taskRunnable);
                }
                mInstance = null;
            }
        };
        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    protected static void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
        locationRequest.setMaxWaitTime(MAX_WAIT_TIME);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    /*public static String batteryLevel() {
        Intent intent  = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int    level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int    scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int    percent = (level*100)/scale;
        return String.valueOf(percent) + "%";
    } */
}

//public static final float SMALLEST_DISPLACEMENT = 1.0F;
//locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
//locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//locationRequest.setSmallestDisplacement(Utils.SMALLEST_DISPLACEMENT);