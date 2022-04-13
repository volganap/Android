package ru.volganap.nikolay.kids_monitor_ably;

import androidx.fragment.app.FragmentActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, KM_Constants {
    LatLng currentPos, lastPos;
    private GoogleMap mMap;
    private LatLngBounds.Builder builder;
    private SharedPreferences sharedPrefs;
    private Boolean kidMarkersAreReady = false;
    private Boolean parenttMarkerIsReady = false;
    int i;
    float  mid_zoom = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "MapsActivity: onCreate ");
        sharedPrefs = getSharedPreferences(PREF_ACTIVITY, MODE_PRIVATE);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        String dataAsJson = getIntent().getStringExtra("loc_data");
        ArrayList<Map<String, String>> data = new Gson().fromJson(dataAsJson, ArrayList.class);

        mMap = googleMap;
        if (Build.VERSION.SDK_INT > 20) {
            int m_type = Integer.parseInt(sharedPrefs.getString(MAP_TYPE, "0"));
           mMap.setMapType(m_type);
        } else mMap.setMapType(1);

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        builder = new LatLngBounds.Builder();

        mMap.setOnMyLocationChangeListener(location -> {
                currentPos = new LatLng(location.getLatitude(), location.getLongitude());
                Log.d(LOG_TAG, "MapsActivity - setOnMyLocationChangeListener fired");
                builder.include(currentPos);

                for (i=0; i < data.size(); i++) {
                    Double latitude = Double.parseDouble(data.get(i).get(ATTRIBUTE_NAME_LAT));
                    Double longitude = Double.parseDouble(data.get(i).get(ATTRIBUTE_NAME_LONG));
                    if (i==0){
                        lastPos = new LatLng(latitude, longitude);
                    }
                    builder.include(new LatLng(latitude, longitude));
                }

                mMap.setOnMyLocationChangeListener(null);
                parenttMarkerIsReady = true;
                defineGroupZoom();
                //mid_zoom = mMap.getCameraPosition().zoom;
        });

        //Show cluster of Markers
        i = data.size();
        mMap.setOnCameraIdleListener(() -> {
                Log.d(LOG_TAG, "MapsActivity: onCameraIdle(), i=" + i);
                if (i > 0) {
                    setMarker(i-1, data.get(i-1));
                    i--;
                } else {
                    mMap.setOnCameraIdleListener(null);
                    kidMarkersAreReady = true;
                    defineGroupZoom();
                }
        });

        mMap.setOnMyLocationButtonClickListener(() -> {
                if (currentPos != null && lastPos != null) {
                    showDistance(currentPos, lastPos);
                }
                return true;
            }
        );
    }

    public void defineGroupZoom() {
        if (parenttMarkerIsReady && kidMarkersAreReady) {
            LatLngBounds bounds = builder.build();
            Log.d(LOG_TAG, "MapsActivity - setOnCameraIdleListener works, the last marker is set up");
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int padding = (int) (width * 0.20);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
        }
    }

    public void showDistance(LatLng latLng1, LatLng latLng2) {
        float [] distance = new float[1];
        Location.distanceBetween(latLng2.latitude, latLng2.longitude, latLng1.latitude, latLng1.longitude, distance);
        PolylineOptions polylineOptions = new PolylineOptions()
                .add(latLng2)
                .add(latLng1)
                .color(Color.MAGENTA)
                .clickable(true)
                .width(4);
        mMap.addPolyline(polylineOptions);
        String s = "Дистанция по прямой - " + Math.round(distance[0]) + "  метров";
        Double midLatitude = (latLng2.latitude + latLng1.latitude)/2;
        Double midLongitude = (latLng2.longitude + latLng1.longitude)/2;
        LatLng midLatLng = new LatLng(midLatitude, midLongitude);

        Marker distMarker = mMap.addMarker(new MarkerOptions()
                .position(midLatLng)
                .title(s)
                .alpha(0.0f)
                .zIndex(3)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .anchor(0.0f, 0.0f)
        );
        distMarker.showInfoWindow();
    }

    public void setMarker (int i, Map<String, String> element) {
        int zoomCurrent = Integer.parseInt(sharedPrefs.getString(MARKER_SCALE, "15"));
        int cameraDelay = Integer.parseInt(sharedPrefs.getString(MARKER_DELAY, "600"));
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.defaultMarker();
        if (i==0) {
            bitmapDescriptor = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        }
        Double latitude = Double.parseDouble(element.get(ATTRIBUTE_NAME_LAT));
        Double longitude = Double.parseDouble(element.get(ATTRIBUTE_NAME_LONG));
        LatLng marker_pos = new LatLng(latitude, longitude);
        String snippet_data = "Дата: " + element.get(ATTRIBUTE_NAME_DATE) + "\n" + "Точность: " + element.get(ATTRIBUTE_NAME_ACCU) + "\n"
                + "АКБ: " + element.get(ATTRIBUTE_NAME_BATT);

        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(marker_pos)
                .title(Integer.toString(i))
                .snippet(snippet_data)
                .icon(bitmapDescriptor)
        );

        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                Context context = getApplicationContext();
                LinearLayout info = new LinearLayout(context);
                info.setOrientation(LinearLayout.VERTICAL);
                TextView title = new TextView(context);
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());
                TextView snippet = new TextView(context);
                snippet.setTextColor(Color.GRAY);
                snippet.setText(marker.getSnippet());
                info.addView(title);
                info.addView(snippet);
                return info;
            }
        });

        marker.showInfoWindow();

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(marker_pos)
                .zoom(zoomCurrent)
                //.zoom(mid_zoom)
                .bearing(0)
                .tilt(0)
                .build();

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), cameraDelay, null);
    }
}

//mMap.getUiSettings().setMapToolbarEnabled(true);
//mMap.getUiSettings().setAllGesturesEnabled(true);
//mMap.getUiSettings().setMyLocationButtonEnabled(true);
//CameraUpdate cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition);
//mMap.moveCamera(CameraUpdateFactory.newLatLng(marker_pos));
//mMap.animateCamera(cameraUpdate);
//mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude , longitude) , 17));