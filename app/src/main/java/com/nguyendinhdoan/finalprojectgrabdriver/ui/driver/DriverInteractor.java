package com.nguyendinhdoan.finalprojectgrabdriver.ui.driver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DriverInteractor implements DriverContract.DriverToInteractor {

    private static final String TAG = "DriverInteractor";

    public static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    public static final int AUTOCOMPLETE_REQUEST_CODE = 2;

    private DriverContract.OnDriverListener listener;
    public static boolean mLocationPermissionGranted;
    private final LatLng mDefaultLocation = new LatLng(21.0055546, 105.8434628);


    public DriverInteractor(DriverContract.OnDriverListener listener) {
        this.listener = listener;
    }

    @Override
    public void getDeviceLocation(Context context) {
        try {
            if (mLocationPermissionGranted) {
                listener.showLoading();
                // get most recent location of the device
                Task locationResult = LocationServices.getFusedLocationProviderClient(context).getLastLocation();
                if (locationResult != null) {
                    locationResult.addOnCompleteListener(new OnCompleteListener() {
                        @Override
                        public void onComplete(@NonNull Task task) {
                            if (task.isSuccessful()) {
                                // current location of you
                                Location lastKnownLocation = (Location) task.getResult();
                                if (lastKnownLocation != null) {
                                    saveCurrentLocationInDatabase(lastKnownLocation);
                                }
                                listener.getDeviceLocationSuccess(lastKnownLocation);
                            } else {
                                // current location is null. set default location
                                String message = Objects.requireNonNull(task.getException()).getMessage();
                                listener.getDeviceLocationFailed(mDefaultLocation, message);
                                Log.e(TAG, message);
                            }
                            listener.hideLoading();
                        }
                    });
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void getLocationPermission(Context context) {
        if (ContextCompat.checkSelfPermission(context.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
    }

    @Override
    public void searchLocationWithAutoComplete(Activity activity) {
        List<Place.Field> fieldList = Arrays.asList(Place.Field.ID, Place.Field.NAME);
        // start autocomplete with intent
        Intent intentAutoComplete = new Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, fieldList
        ).setCountry("VN").setLocationRestriction(RectangularBounds.newInstance(new LatLng(21.008355, 105.746496),
                new LatLng(21.0468514, 105.9216884)))
                .build(activity);
        activity.startActivityForResult(intentAutoComplete, AUTOCOMPLETE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                Log.i(TAG, "Place: " + place.getName());
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                Status status = Autocomplete.getStatusFromIntent(data);
                Log.i(TAG, status.getStatusMessage());
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // the user canceled the operation
                Log.i(TAG, "Canceled");
            }
        }
    }

    private void saveCurrentLocationInDatabase(Location lastKnownLocation) {
        String driverId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        DatabaseReference drivers = FirebaseDatabase.getInstance().getReference("drivers/" + driverId);
        GeoFire geoFire = new GeoFire(drivers);

        // get latitude and longitude
        double latitude = lastKnownLocation.getLatitude();
        double longitude = lastKnownLocation.getLongitude();
        // save into database
        geoFire.setLocation("location", new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {
                if (error == null) {
                    Log.d(TAG, "save current location of driver successful");
                } else {
                    Log.e(TAG, "Error" + error);
                }
            }
        });
    }
}
