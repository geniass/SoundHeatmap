package geniass.soundmap;

import android.app.Activity;
import android.content.Context;
import android.location.*;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SoundMapActivity extends Activity implements MicrophoneInputListener, GpsStatus.Listener, LocationListener {

    String TAG = "SoundMapActivity";

    MicrophoneInput micInput;  // The micInput object provides real time audio.
    LocationManager locationManager;
    Location mCurrentLocation;

    GoogleMap mMap;

    HashMap<LatLng, Decibels> coordinateDecibelsHashMap = new HashMap<LatLng, Decibels>();

    TextView dbTextView;
    Button postButton;

    Criteria criteria;

    // The Google ASR input requirements state that audio input sensitivity
    // should be set such that 90 dB SPL at 1000 Hz yields RMS of 2500 for
    // 16-bit samples, i.e. 20 * log_10(2500 / mGain) = 90.
    double mGain = 2500.0 / Math.pow(10.0, 90.0 / 20.0);
    double mOffsetdB = 10;  // Offset for bar, i.e. 0 lit LEDs at 10 dB.
    // For displaying error in calibration.
    double mDifferenceFromNominal = 0.0;
    double mRmsSmoothed;  // Temporally filtered version of RMS.
    double mAlpha = 0.9;  // Coefficient of IIR smoothing filter for RMS.
    private int mSampleRate;  // The audio sampling rate to use.
    private int mAudioSource;  // The audio source to use.

    boolean mListenMicrophoneInput = false;

    @Override
    protected void onResume() {
        super.onResume();
        locationManager.addGpsStatusListener(this);
        locationManager.requestLocationUpdates(20, 0, criteria, this, null);    //blocking for now
        micInput.start();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeGpsStatusListener(this);
        locationManager.removeUpdates(this);
        micInput.stop();
        Log.d(TAG, "onPause");
        Log.d(TAG, String.valueOf(coordinateDecibelsHashMap.size()));
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        mMap.setMyLocationEnabled(true);

        dbTextView = (TextView) findViewById(R.id.textViewDb);
        postButton = (Button) findViewById(R.id.button_post);
        postButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("SoundmapJSON", getDecibelHashmapAsJson().toString());
                try {
                    makeJSONPostRequest("http://soundmap.herokuapp.com/data", getDecibelHashmapAsJson().toString()).toString();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("SoundmapJSON", e.getMessage());
                }

            }
        });

        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        micInput = new MicrophoneInput(this);

        //locationManager.requestSingleUpdate(criteria, this, null);
    }

    @Override
    public void processAudioFrame(short[] audioFrame) {
        // Compute the RMS value. (Note that this does not remove DC).
        double rms = 0;
        for (int i = 0; i < audioFrame.length; i++) {
            rms += audioFrame[i] * audioFrame[i];
        }
        rms = Math.sqrt(rms / audioFrame.length);

        // Compute a smoothed version for less flickering of the display.
        // da fuq?
        mRmsSmoothed = mRmsSmoothed * mAlpha + (1 - mAlpha) * rms;
        final double rmsdB = 20.0 * Math.log10(mGain * mRmsSmoothed);

        if (mCurrentLocation != null) {
            Log.d(TAG, "Lat: " + String.valueOf(mCurrentLocation.getLatitude()));
            Log.d(TAG, "Lon: " + String.valueOf(mCurrentLocation.getLongitude()));
            Log.d(TAG, "DB: " + String.valueOf(rms));
            Log.d(TAG, "\n");

            DecimalFormat df = new DecimalFormat("##.######");
            final LatLng coord = new LatLng(Double.parseDouble(df.format(mCurrentLocation.getLatitude())), Double.parseDouble(df.format(mCurrentLocation.getLongitude())));
            Log.d(TAG, coord.toString());
            Log.d(TAG, String.valueOf(coord.hashCode()));
            if (coordinateDecibelsHashMap.containsKey(coord)) {
                Log.d(TAG, "Found dB: " + String.valueOf(coordinateDecibelsHashMap.get(coord).getDb()));
                Decibels dB = (Decibels) coordinateDecibelsHashMap.get(coord);
                dB.addToAverage(rms);
                coordinateDecibelsHashMap.put(coord, dB);
            } else {
                coordinateDecibelsHashMap.put(coord, new Decibels(rms));
                final double final_rms = rms;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMap.addMarker(new MarkerOptions().position(coord).title("dB: " + String.valueOf(final_rms)));
                    }
                });
            }

        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dbTextView.setText(String.valueOf(coordinateDecibelsHashMap.size()));
            }
        });

        mCurrentLocation = null;
    }

    @Override
    public void onGpsStatusChanged(int i) {
        if (i == GpsStatus.GPS_EVENT_FIRST_FIX) {
            mListenMicrophoneInput = true;
            micInput.start();
        }
    }

    public JSONArray getDecibelHashmapAsJson() {
        JSONArray array = new JSONArray();

        Iterator iter = coordinateDecibelsHashMap.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry pair = (Map.Entry) iter.next();

            JSONObject coord = new JSONObject();
            try {
                coord.put("lat", ((LatLng) pair.getKey()).latitude);
                coord.put("lon", ((LatLng) pair.getKey()).longitude);
                coord.put("dB", ((Decibels) pair.getValue()).getDb());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            array.put(coord);
        }

        return array;
    }

    public HttpResponse makeJSONPostRequest(String path, String json) throws Exception {
        DefaultHttpClient http = new DefaultHttpClient();
        HttpPost post = new HttpPost(path);
        StringEntity se = new StringEntity(json);

        post.setEntity(se);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");

        ResponseHandler handler = new BasicResponseHandler();
        HttpResponse response = (HttpResponse) http.execute(post, handler);
        Log.d(TAG, response.toString());
        return response;
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onProviderEnabled(String s) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onProviderDisabled(String s) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
