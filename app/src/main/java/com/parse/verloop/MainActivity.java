package com.parse.verloop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap.CancelableCallback;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;

public class MainActivity extends FragmentActivity implements LocationListener,
    GooglePlayServicesClient.ConnectionCallbacks,
    GooglePlayServicesClient.OnConnectionFailedListener {

  private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
  private static final int MILLISECONDS_PER_SECOND = 1000;
  private static final int UPDATE_INTERVAL_IN_SECONDS = 5;
  private static final int FAST_CEILING_IN_SECONDS = 1;
  private static final long UPDATE_INTERVAL_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
      * UPDATE_INTERVAL_IN_SECONDS;
  private static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
      * FAST_CEILING_IN_SECONDS;

  private static final float METERS_PER_FEET = 0.3048f;
  private static final int METERS_PER_KILOMETER = 1000;
  private static final double OFFSET_CALCULATION_INIT_DIFF = 1.0;
  private static final float OFFSET_CALCULATION_ACCURACY = 0.01f;
  private static final int MAX_POST_SEARCH_RESULTS = 20;
  private static final int MAX_POST_SEARCH_DISTANCE = 10;

  // Map fragment
  private SupportMapFragment mapFragment;
  private Circle mapCircle;
  private float radius;
  private float lastRadius;
  private final Map<String, Marker> mapMarkers = new HashMap<String, Marker>();
  private int mostRecentMapUpdate;
  private boolean hasSetUpInitialLocation;
  private String selectedPostObjectId;
  private Location lastLocation;
  private Location currentLocation;

  private LocationRequest locationRequest;
  private LocationClient locationClient;
  private ParseQueryAdapter<VerloopPost> postsQueryAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    radius = Application.getSearchDistance();
    lastRadius = radius;
    setContentView(R.layout.activity_main);

    locationRequest = LocationRequest.create();
    locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    locationRequest.setFastestInterval(FAST_INTERVAL_CEILING_IN_MILLISECONDS);
    locationClient = new LocationClient(this, this, this);

    ParseQueryAdapter.QueryFactory<VerloopPost> factory =
        new ParseQueryAdapter.QueryFactory<VerloopPost>() {
          public ParseQuery<VerloopPost> create() {
            Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
            ParseQuery<VerloopPost> query = VerloopPost.getQuery();
            query.include("user");
            query.orderByDescending("createdAt");
            query.whereWithinKilometers("location", geoPointFromLocation(myLoc), radius
                * METERS_PER_FEET / METERS_PER_KILOMETER);
            query.setLimit(MAX_POST_SEARCH_RESULTS);
            return query;
          }
        };

    postsQueryAdapter = new ParseQueryAdapter<VerloopPost>(this, factory) {
      @Override
      public View getItemView(VerloopPost post, View view, ViewGroup parent) {
        if (view == null) {
          view = View.inflate(getContext(), R.layout.verloop_post_item, null);
        }
        TextView titleView = (TextView) view.findViewById(R.id.title_view);
        TextView contentView = (TextView) view.findViewById(R.id.content_view);
        TextView usernameView = (TextView) view.findViewById(R.id.username_view);
        titleView.setText(post.getTitle());
        contentView.setText(post.getText());
        usernameView.setText("Posted by: " + post.getUser().getUsername());
        return view;
      }
    };

    postsQueryAdapter.setAutoload(false);
    postsQueryAdapter.setPaginationEnabled(false);

    ListView postsListView = (ListView) findViewById(R.id.posts_listview);
    postsListView.setAdapter(postsQueryAdapter);
    postsListView.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final VerloopPost item = postsQueryAdapter.getItem(position);
        selectedPostObjectId = item.getObjectId();
        mapFragment.getMap().animateCamera(
            CameraUpdateFactory.newLatLng(new LatLng(item.getLocation().getLatitude(), item
                .getLocation().getLongitude())), new CancelableCallback() {
              public void onFinish() {
                Marker marker = mapMarkers.get(item.getObjectId());
                if (marker != null) {
                  marker.showInfoWindow();
                }
              }

              public void onCancel() {
              }
            });
        Marker marker = mapMarkers.get(item.getObjectId());
        if (marker != null) {
          marker.showInfoWindow();
        }
      }
    });

    mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
    mapFragment.getMap().setMyLocationEnabled(true);
    mapFragment.getMap().setOnCameraChangeListener(new OnCameraChangeListener() {
      public void onCameraChange(CameraPosition position) {
        doMapQuery();
      }
    });

    Button postButton = (Button) findViewById(R.id.post_button);
    postButton.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
        if (myLoc == null) {
          Toast.makeText(MainActivity.this,
              "Please try again after your location appears on the map.", Toast.LENGTH_LONG).show();
          return;
        }

        Intent intent = new Intent(MainActivity.this, PostActivity.class);
        intent.putExtra(Application.INTENT_EXTRA_LOCATION, myLoc);
        startActivity(intent);
      }
    });
  }

  @Override
  public void onStop() {
    if (locationClient.isConnected()) {
      stopPeriodicUpdates();
    }

    locationClient.disconnect();
    super.onStop();
  }

  /*
   * Called when the Activity is restarted, even before it becomes visible.
   */
  @Override
  public void onStart() {
    super.onStart();
    locationClient.connect();
  }

  @Override
  protected void onResume() {
    super.onResume();

    Application.getConfigHelper().fetchConfigIfNeeded();
    radius = Application.getSearchDistance();
    if (lastLocation != null) {
      LatLng myLatLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
      if (lastRadius != radius) {
        updateZoom(myLatLng);
      }
      updateCircle(myLatLng);
    }
    lastRadius = radius;
    doMapQuery();
    doListQuery();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    switch (requestCode) {

      case CONNECTION_FAILURE_RESOLUTION_REQUEST:

        switch (resultCode) {
          case Activity.RESULT_OK:
            if (Application.APPDEBUG) {
              Log.d(Application.APPTAG, "Connected to Google Play services");
            }

            break;
          default:
            if (Application.APPDEBUG) {
              Log.d(Application.APPTAG, "Could not connect to Google Play services");
            }
            break;
        }

      default:
        if (Application.APPDEBUG) {
          Log.d(Application.APPTAG, "Unknown request code received for the activity");
        }
        break;
    }
  }

  private boolean servicesConnected() {
    int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

    if (ConnectionResult.SUCCESS == resultCode) {
      if (Application.APPDEBUG) {
        Log.d(Application.APPTAG, "Google play services available");
      }
      return true;
    } else {
      Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
      if (dialog != null) {
        ErrorDialogFragment errorFragment = new ErrorDialogFragment();
        errorFragment.setDialog(dialog);
        errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
      }
      return false;
    }
  }

  public void onConnected(Bundle bundle) {
    if (Application.APPDEBUG) {
      Log.d("Connected loc services", Application.APPTAG);
    }
    currentLocation = getLocation();
    startPeriodicUpdates();
  }

  public void onDisconnected() {
    if (Application.APPDEBUG) {
      Log.d("Disconnected loc serv", Application.APPTAG);
    }
  }

  public void onConnectionFailed(ConnectionResult connectionResult) {
    if (connectionResult.hasResolution()) {
      try {
        connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
      } catch (IntentSender.SendIntentException e) {
        if (Application.APPDEBUG) {
          Log.d(Application.APPTAG, "An error occurred when connecting to location services.", e);
        }
      }
    } else {
      showErrorDialog(connectionResult.getErrorCode());
    }
  }

  /*
   * Report location updates to the UI.
   */
  public void onLocationChanged(Location location) {
    currentLocation = location;
    if (lastLocation != null
        && geoPointFromLocation(location)
        .distanceInKilometersTo(geoPointFromLocation(lastLocation)) < 0.01) {
      return;
    }
    lastLocation = location;
    LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
    if (!hasSetUpInitialLocation) {
      updateZoom(myLatLng);
      hasSetUpInitialLocation = true;
    }
    updateCircle(myLatLng);
    doMapQuery();
    doListQuery();
  }

  private void startPeriodicUpdates() {
    locationClient.requestLocationUpdates(locationRequest, this);
  }

  private void stopPeriodicUpdates() {
    locationClient.removeLocationUpdates(this);
  }

  private Location getLocation() {
    if (servicesConnected()) {
      return locationClient.getLastLocation();
    } else {
      return null;
    }
  }

  private void doListQuery() {
    Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
    if (myLoc != null) {
      postsQueryAdapter.loadObjects();
    }
  }

  private void doMapQuery() {
    final int myUpdateNumber = ++mostRecentMapUpdate;
    Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
    if (myLoc == null) {
      cleanUpMarkers(new HashSet<String>());
      return;
    }
    final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);
    ParseQuery<VerloopPost> mapQuery = VerloopPost.getQuery();
    mapQuery.whereWithinKilometers("location", myPoint, MAX_POST_SEARCH_DISTANCE);
    mapQuery.include("user");
    mapQuery.orderByDescending("createdAt");
    mapQuery.setLimit(MAX_POST_SEARCH_RESULTS);
    mapQuery.findInBackground(new FindCallback<VerloopPost>() {
      @Override
      public void done(List<VerloopPost> objects, ParseException e) {
        if (e != null) {
          if (Application.APPDEBUG) {
            Log.d(Application.APPTAG, "An error occurred while querying for map posts.", e);
          }
          return;
        }

        if (myUpdateNumber != mostRecentMapUpdate) {
          return;
        }
        Set<String> toKeep = new HashSet<String>();
        for (VerloopPost post : objects) {
          toKeep.add(post.getObjectId());
          Marker oldMarker = mapMarkers.get(post.getObjectId());
          MarkerOptions markerOpts =
              new MarkerOptions().position(new LatLng(post.getLocation().getLatitude(), post
                  .getLocation().getLongitude()));
          if (post.getLocation().distanceInKilometersTo(myPoint) > radius * METERS_PER_FEET
              / METERS_PER_KILOMETER) {
            if (oldMarker != null) {
              if (oldMarker.getSnippet() == null) {
                continue;
              } else {
                oldMarker.remove();
              }
            }

            markerOpts =
                markerOpts.title(getResources().getString(R.string.post_out_of_range)).icon(
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
          } else {
            if (oldMarker != null) {
              if (oldMarker.getSnippet() != null) {
                continue;
              } else {
                oldMarker.remove();
              }
            }
            markerOpts =
                markerOpts.title(post.getTitle()).snippet("Posted by: " + post.getUser().getUsername())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
          }
          Marker marker = mapFragment.getMap().addMarker(markerOpts);
          mapMarkers.put(post.getObjectId(), marker);
          if (post.getObjectId().equals(selectedPostObjectId)) {
            marker.showInfoWindow();
            selectedPostObjectId = null;
          }
        }
        cleanUpMarkers(toKeep);
      }
    });
  }

  private void cleanUpMarkers(Set<String> markersToKeep) {
    for (String objId : new HashSet<String>(mapMarkers.keySet())) {
      if (!markersToKeep.contains(objId)) {
        Marker marker = mapMarkers.get(objId);
        marker.remove();
        mapMarkers.get(objId).remove();
        mapMarkers.remove(objId);
      }
    }
  }

  private ParseGeoPoint geoPointFromLocation(Location loc) {
    return new ParseGeoPoint(loc.getLatitude(), loc.getLongitude());
  }

  private void updateCircle(LatLng myLatLng) {
    if (mapCircle == null) {
      mapCircle =
          mapFragment.getMap().addCircle(
              new CircleOptions().center(myLatLng).radius(radius * METERS_PER_FEET));
      int baseColor = Color.DKGRAY;
      mapCircle.setStrokeColor(baseColor);
      mapCircle.setStrokeWidth(2);
      mapCircle.setFillColor(Color.argb(50, Color.red(baseColor), Color.green(baseColor),
          Color.blue(baseColor)));
    }
    mapCircle.setCenter(myLatLng);
    mapCircle.setRadius(radius * METERS_PER_FEET);
  }

  private void updateZoom(LatLng myLatLng) {
    LatLngBounds bounds = calculateBoundsWithCenter(myLatLng);
    mapFragment.getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 5));
  }

  private double calculateLatLngOffset(LatLng myLatLng, boolean bLatOffset) {
    double latLngOffset = OFFSET_CALCULATION_INIT_DIFF;
    float desiredOffsetInMeters = radius * METERS_PER_FEET;
    float[] distance = new float[1];
    boolean foundMax = false;
    double foundMinDiff = 0;
    do {
      if (bLatOffset) {
        Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude
            + latLngOffset, myLatLng.longitude, distance);
      } else {
        Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude,
            myLatLng.longitude + latLngOffset, distance);
      }
      float distanceDiff = distance[0] - desiredOffsetInMeters;
      if (distanceDiff < 0) {
        if (!foundMax) {
          foundMinDiff = latLngOffset;
          latLngOffset *= 2;
        } else {
          double tmp = latLngOffset;
          latLngOffset += (latLngOffset - foundMinDiff) / 2;
          foundMinDiff = tmp;
        }
      } else {
        latLngOffset -= (latLngOffset - foundMinDiff) / 2;
        foundMax = true;
      }
    } while (Math.abs(distance[0] - desiredOffsetInMeters) > OFFSET_CALCULATION_ACCURACY);
    return latLngOffset;
  }

  LatLngBounds calculateBoundsWithCenter(LatLng myLatLng) {
    LatLngBounds.Builder builder = LatLngBounds.builder();

    double lngDifference = calculateLatLngOffset(myLatLng, false);
    LatLng east = new LatLng(myLatLng.latitude, myLatLng.longitude + lngDifference);
    builder.include(east);
    LatLng west = new LatLng(myLatLng.latitude, myLatLng.longitude - lngDifference);
    builder.include(west);

    double latDifference = calculateLatLngOffset(myLatLng, true);
    LatLng north = new LatLng(myLatLng.latitude + latDifference, myLatLng.longitude);
    builder.include(north);
    LatLng south = new LatLng(myLatLng.latitude - latDifference, myLatLng.longitude);
    builder.include(south);

    return builder.build();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);

    menu.findItem(R.id.action_settings).setOnMenuItemClickListener(new OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem item) {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        return true;
      }
    });
    return true;
  }

  private void showErrorDialog(int errorCode) {
    Dialog errorDialog =
        GooglePlayServicesUtil.getErrorDialog(errorCode, this,
            CONNECTION_FAILURE_RESOLUTION_REQUEST);

    if (errorDialog != null) {
      ErrorDialogFragment errorFragment = new ErrorDialogFragment();
      errorFragment.setDialog(errorDialog);
      errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
    }
  }

  public static class ErrorDialogFragment extends DialogFragment {
    private Dialog mDialog;

    public ErrorDialogFragment() {
      super();
      mDialog = null;
    }

    public void setDialog(Dialog dialog) {
      mDialog = dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      return mDialog;
    }
  }
}
