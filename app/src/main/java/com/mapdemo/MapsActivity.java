package com.mapdemo;

import android.*;
import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback , GoogleMap.OnMapClickListener , GoogleMap.OnMarkerClickListener,
        GoogleApiClient.OnConnectionFailedListener , GoogleApiClient.ConnectionCallbacks, LocationListener, ResultCallback<Status> {

    private static final int REQ_PERMISSION = 5;
    private static TextView textLong;
    private MapFragment mapFragment;
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;
    private LocationRequest locationRequest;
    private Button button;
    public Marker locationMarker;
    public Marker geoFenceMarker;

    private PendingIntent geoFencePendingIntent;
    private final int GEOFENCE_REQ_CODE = 0;
    private static final long GEO_DURATION = 60 * 60 * 1000;
    private static final String GEOFENCE_REQ_ID = "My Geofence";
    private static final float GEOFENCE_RADIUS = 500.0f; // in meters
    private final int UPDATE_INTERVAL =   1 * 60 * 1000; // 3 minutes
    private final int FASTEST_INTERVAL = 30 * 1000;  // 30 secs
    private int count = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        textLong = (TextView) findViewById(R.id.lon);
        button = (Button) findViewById(R.id.button_location);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getLastKnownLocation();
                startGeofence();
            }
        });
        // initialize GoogleMaps
        initGMaps();
        // create GoogleApiClient
        createGoogleApi();
        if (getIntent() != null && getIntent().getExtras() != null && getIntent().getExtras().containsKey("msg"))
        {
            String msg = getIntent().getStringExtra("msg");
           textLong.setText(msg);
        }

    }

    private void getLastKnownLocation()
    {
        if (checkPermission())
        {
          lastLocation =   LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (lastLocation != null)
            {
                writeLastLocation();
                startLocationUpdates();
            }
            else
            {
                startLocationUpdates();
            }
        }
        else
        {
            askPermission();
        }

    }

    private void writeLastLocation()
    {
        writeActualLocation(lastLocation);
    }
    private void startLocationUpdates()
    {
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        .setInterval(UPDATE_INTERVAL)
        .setSmallestDisplacement(0.01f)
        .setFastestInterval(FASTEST_INTERVAL);
        if (checkPermission() && googleApiClient.isConnected())
        {

            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        }
        else {

            Toast.makeText(MapsActivity.this, "not connect", Toast.LENGTH_SHORT).show();
        }

    }

    private void askPermission()
    {
        ActivityCompat.requestPermissions(
                this,
                new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                REQ_PERMISSION
        );
    }

    private boolean checkPermission()
    {

        // Ask for permission if it wasn't granted yet
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED );
    }

    private void createGoogleApi()
    {
        if (googleApiClient == null)
        {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (googleApiClient != null)
        {

            googleApiClient.connect();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (googleApiClient != null && googleApiClient.isConnected())
        googleApiClient.disconnect();
    }

    private void initGMaps()
    {
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        map = googleMap;
        map.setOnMapClickListener(this);
        map.setOnMarkerClickListener(this);
    }

    @Override
    public void onMapClick(LatLng latLng)
    {
        markerForGeofence(latLng);
    }

    private void markerForGeofence(LatLng latLng) {
        String title  = latLng.latitude + ", " + latLng.longitude;
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE));
        markerOptions.position(latLng);
        markerOptions.title(title);
        if (map != null)
        {
            if (geoFenceMarker != null)
                geoFenceMarker.remove();
            geoFenceMarker = map.addMarker(markerOptions);
            startGeofence();
        }


    }

    private void markerLocation(LatLng latLng) {
        String title = latLng.latitude + ", " + latLng.longitude;
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        markerOptions.title(title);
        if (map != null)
        {
            // Remove the anterior marker
            if ( locationMarker != null )
                locationMarker.remove();
            locationMarker = map.addMarker(markerOptions);
            float zoom = 14f;
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            map.animateCamera(cameraUpdate);
        }

    }

    @Override
    public boolean onMarkerClick(Marker marker)
    {
        return false;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        Toast.makeText(MapsActivity.this, "location update", Toast.LENGTH_SHORT).show();
        lastLocation = location;
        textLong.setText("Location Update Count:" + count);
        count++;
        writeActualLocation(location);
    }

    private void writeActualLocation(Location location) {
        markerLocation(new LatLng(location.getLatitude(), location.getLongitude()));
      //  textLong.setText( "Lat: " + location.getLatitude() + "Long: " + location.getLongitude() );

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case REQ_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    getLastKnownLocation();
                }
                else
                {
                    permissionsDenied();
                }
                break;
        }
    }

    private void permissionsDenied()
    {
        finish();
    }

    // Create a Geofence
    private Geofence createGeofence(LatLng latLng, float radius ) {

        return new Geofence.Builder()
        .setRequestId(GEOFENCE_REQ_ID)
        .setCircularRegion(latLng.latitude, latLng.longitude, radius)
        .setExpirationDuration(GEO_DURATION)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    // Create a Geofence Request
    private GeofencingRequest createGeofenceRequest(Geofence geofence ) {

        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    private PendingIntent createGeofencePendingIntent() {
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;
        Intent intent =new Intent( this, GeofenceTrasitionService.class);
        return PendingIntent.getService(this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Add the created GeofenceRequest to the device's monitoring list
    private void addGeofence(GeofencingRequest request) {
        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(googleApiClient, request, createGeofencePendingIntent()).setResultCallback(this);

     }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i("MapsActivity", "onResult: " + status);
        if ( status.isSuccess() ) {
            drawGeofence();
        } else {
            // inform about fail
        }
    }

    // Draw Geofence circle on GoogleMap
    private Circle geoFenceLimits;
    private void drawGeofence() {
        if (geoFenceLimits != null)
            geoFenceLimits.remove();

        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(geoFenceMarker.getPosition());
        circleOptions.strokeColor(Color.argb(50, 70,70,70));
        circleOptions.fillColor(Color.argb(100, 150,150,150));
        circleOptions.radius(GEOFENCE_RADIUS );
        geoFenceLimits = map.addCircle(circleOptions);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case R.id.button_location:
                startGeofence();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Start Geofence creation process
    private void startGeofence() {
        if (geoFenceMarker != null)
        {
            Geofence geofence = createGeofence(geoFenceMarker.getPosition(), GEOFENCE_RADIUS);
            GeofencingRequest geofencingRequest = createGeofenceRequest(geofence);
            addGeofence(geofencingRequest);
        }
        else
        {
            Log.i("MapsActivity", "startGeofence: ");
        }
    }

    public static Intent makeNotificationIntent(Context applicationContext, final String msg) {
           return new Intent(applicationContext,MapsActivity.class);
    }


}
