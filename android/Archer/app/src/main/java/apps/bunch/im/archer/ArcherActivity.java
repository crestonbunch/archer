package apps.bunch.im.archer;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.VelocityTracker;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.Vector3;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.xml.transform.Result;

public class ArcherActivity extends Activity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public static String LOG_TAG = "ArcherActivity";
    public static String STATE_RESOLVING_KEY = "StateResolvingKey";
    public static String TARGET_LATITUDE_KEY = "TargetLatitudeKey";
    public static String TARGET_LONGITUDE_KEY = "TargetLongitudeKey";
    public static String HIT_LATITUDE_KEY = "HitLatitudeKey";
    public static String HIT_LONGITUDE_KEY = "HitLongitudeKey";
    public static double ACCEL_UNIT_CONVERSION = (0.98 / 1000); // convert to meters
    private static final int REQUEST_RESOLVE_ERROR = 1001;

    public enum State {
        WAITING, PULLING, FLYING
    }

    private double mTargetLong;
    private double mTargetLat;
    private double mHitLong;
    private double mHitLat;

    private TextView mStateView;
    private TextView mOrientation;
    private TextView mMyoAcceleration;
    private TextView mMyoOrientation;
    private TextView mGravity;
    private TextView mLinearAcceleration;
    private boolean mMyoConnected = false;
    private boolean mMyoSynced = false;
    private State mState;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGeomagnetic;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private Location mCurrentLocation;
    private boolean mResolvingError = false;

    private Vector3 mLastAcceleration;
    private long mlastAccelTime;
    private List<Vector3> mAccelValues;
    private List<Long> mAccelTimes;

    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {

        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            // Set the text color of the text view to cyan when a Myo connects.
            Log.d(LOG_TAG, "Myo connected.");
            mMyoConnected = true;
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            // Set the text color of the text view to red when a Myo disconnects.
            Log.d(LOG_TAG, "Myo disconnected.");
            mMyoConnected = false;
        }

        // onArmSync() is called whenever Myo has recognized a Sync Gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmSync(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            Log.d(LOG_TAG, getString(myo.getArm() == Arm.LEFT ?
                    R.string.arm_left : R.string.arm_right));
            mMyoSynced = true;
        }

        // onArmUnsync() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmUnsync(Myo myo, long timestamp) {
            Log.d(LOG_TAG, "Myo unsynced.");
            mMyoSynced = false;
        }

        // onUnlock() is called whenever a synced Myo has been unlocked. Under the standard locking
        // policy, that means poses will now be delivered to the listener.
        @Override
        public void onUnlock(Myo myo, long timestamp) {
            Log.d(LOG_TAG, "Myo unlocked.");
        }

        // onLock() is called whenever a synced Myo has been locked. Under the standard locking
        // policy, that means poses will no longer be delivered to the listener.
        @Override
        public void onLock(Myo myo, long timestamp) {
            Log.d(LOG_TAG, "Myo locked");
        }

        @Override
        public void onAccelerometerData(Myo myo, long timestamp, Vector3 accel) {
            //Log.d(LOG_TAG, "Acceleration: " + accel.toString());
            mMyoAcceleration.setText(String.format("Myo Accel (x,y,z) => %.5f, %.5f, %.5f", accel.x(), accel.y(), accel.z()));
            mLastAcceleration = accel;
            mlastAccelTime = timestamp;
        }

        @Override
        public void onGyroscopeData(Myo myo, long timestamp, Vector3 gyro) {
            super.onGyroscopeData(myo, timestamp, gyro);
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {
            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (myo.getXDirection() == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }
            Vector3 g = new Vector3(
                    2 * (rotation.x() * rotation.z() - rotation.w() * rotation.y()),
                    2 * (rotation.w() * rotation.x() + rotation.y() * rotation.z()),
                    rotation.w() * rotation.w() - rotation.x() * rotation.x()
                            - rotation.y() * rotation.y() + rotation.z() * rotation.z()
            );
            /*
            // Next, we apply a rotation to the text view using the roll, pitch, and yaw.
            mTextView.setRotation(roll);
            mTextView.setRotationX(pitch);
            mTextView.setRotationY(yaw);
            */
            mMyoOrientation.setText(
                String.format(
                    "Myo Orient (y,p,r) => %.2f, %.2f, %.2f",
                    yaw, pitch, roll
                )
            );
            mGravity.setText(
                    String.format(
                            "Gravity (x,y,z) => %.4f, %.4f, %.4f", g.x(), g.y(), g.z()
                    )
            );

            Vector3 accel = calcLinearAccel(g, mLastAcceleration);
            mLinearAcceleration.setText(
                    String.format(
                            "LinAccel => (x,y,z): %.4f, %.4f, %.4f",
                            accel.x(), accel.y(), accel.z()
                    )
            );

            if (mState == State.PULLING) {
                mAccelValues.add(accel);
                mAccelTimes.add(mlastAccelTime);
            }
        }

        private Vector3 calcLinearAccel(Vector3 g, Vector3 accel) {
            if (g != null && accel != null) {
                //Vector3 linear = new Vector3(g);
                //linear.subtract(accel);
                //linear.multiply(ACCEL_UNIT_CONVERSION);
                Vector3 linear = new Vector3(accel);
                linear.subtract(g);
                linear.multiply(ACCEL_UNIT_CONVERSION);
                return linear;
            }
            return new Vector3(0,0,0);
        }


        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
            switch (pose) {
                case UNKNOWN:
                    Log.i(LOG_TAG, "Unknown pose.");
                    break;
                case REST:
                    Log.i(LOG_TAG, "Rest pose.");
                    if (mState == State.PULLING) {
                        setStateFlying();
                    }
                    break;
                case DOUBLE_TAP:
                    Log.i(LOG_TAG, "Double tap pose.");
                    if (mState == State.PULLING) {
                        setStateFlying();
                    }
                    break;
                case FIST:
                    Log.i(LOG_TAG, "Fist pose.");
                    if (mState == State.WAITING) {
                        setStatePulling();
                    }
                    break;
                case WAVE_IN:
                    Log.i(LOG_TAG, "Wave in.");
                    if (mState == State.PULLING) {
                        setStateFlying();
                    }
                    break;
                case WAVE_OUT:
                    Log.i(LOG_TAG, "Wave out.");
                    if (mState == State.PULLING) {
                        setStateFlying();
                    }
                    break;
                case FINGERS_SPREAD:
                    Log.i(LOG_TAG, "Fingers spread.");
                    if (mState == State.PULLING) {
                        setStateFlying();
                    }
                    break;
            }

            if (pose != Pose.UNKNOWN && pose != Pose.REST) {
                // Tell the Myo to stay unlocked until told otherwise. We do that here so you can
                // hold the poses without the Myo becoming locked.
                myo.unlock(Myo.UnlockType.HOLD);

                // Notify the Myo that the pose has resulted in an action, in this case changing
                // the text on the screen. The Myo will vibrate.
                myo.notifyUserAction();
            } else {
                // Tell the Myo to stay unlocked only for a short period. This allows the Myo to
                // stay unlocked while poses are being performed, but lock after inactivity.
                myo.unlock(Myo.UnlockType.TIMED);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archer);

        mStateView = (TextView) findViewById(R.id.state);
        mOrientation = (TextView) findViewById(R.id.orientation);
        mMyoAcceleration = (TextView) findViewById(R.id.myo_acceleration);
        mMyoOrientation = (TextView) findViewById(R.id.myo_orientation);
        mGravity = (TextView) findViewById(R.id.gravity);
        mLinearAcceleration = (TextView) findViewById(R.id.linear_accel);

        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGeomagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // Create a GoogleApiClient instance
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // get this data from the intent
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            mTargetLong = intent.getDoubleExtra(ResultMapActivity.TARGET_LONGITUDE, 0.0);
            mTargetLat = intent.getDoubleExtra(ResultMapActivity.TARGET_LATITUDE, 0.0);
        }

        if (!mMyoConnected) {
            // automatically show connect dialog
            onScanActionSelected();
        }

        // begin in the waiting state
        setStateWaiting();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {  // more about this later
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGeomagnetic, SensorManager.SENSOR_DELAY_NORMAL);
        if (mState == State.FLYING) {
            setStateWaiting();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                gravity = event.values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomagnetic = event.values;
                break;
        }
        if (gravity != null && geomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] foo = new float[3];
                SensorManager.getOrientation(R, foo);
                mOrientation.setText(String.format("Orientation (y,p,r) => %.2f, %.2f, %.2f", foo[0], foo[1], foo[2]));
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_archer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        if (R.id.action_scan == id) {
            onScanActionSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }

    private void onScanActionSelected() {
        // Launch the ScanActivity to scan for Myos to connect to.
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    private void setStateFlying() {
        Log.i(LOG_TAG, "Changing state to flying.");
        mState = State.FLYING;
        mStateView.setText(getString(R.string.state_flying));
        double d = calcDistance();
        Log.i(LOG_TAG, "Distance: " + Double.toString(d));
        showMap();
    }

    private void setStatePulling() {
        Log.i(LOG_TAG, "Changing state to pulling.");
        mState = State.PULLING;
        mStateView.setText(getString(R.string.state_pulling));
    }

    private void setStateWaiting() {
        Log.i(LOG_TAG, "Changing state to waiting.");
        mState = State.WAITING;
        mStateView.setText(getString(R.string.state_waiting));
        mAccelValues = new ArrayList<>();
        mAccelTimes = new ArrayList<>();
    }

    private void showMap() {

        Log.i(LOG_TAG, "Target: (" + Double.toString(mTargetLat) + ", "
                + Double.toString(mTargetLong) + ")");
        Intent intent = new Intent(this, ResultMapActivity.class);
        intent.putExtra(ResultMapActivity.TARGET_LATITUDE, mTargetLat);
        intent.putExtra(ResultMapActivity.TARGET_LONGITUDE, mTargetLong);
        intent.putExtra(ResultMapActivity.SOURCE_LATITUDE, mCurrentLocation.getLatitude());
        intent.putExtra(ResultMapActivity.SOURCE_LONGITUDE, mCurrentLocation.getLongitude());
        /* Commented out until force, distanceDrawn orientation, and myo are set up */
        // force made up = 100
        // distance drawn made up = 10
        // orientation made up = [0.8, -1.4, 0.26]
        float[] orientationInvent = new float[3];
        orientationInvent[0] = (float) 0.8;
        orientationInvent[1] = (float) -1.4;
        orientationInvent[2] = (float) 0.26;
        mHitLat = PhysicsEngine.arrowFlightLatitude(mCurrentLocation.getLatitude(), 100, PhysicsEngine.mass, orientationInvent, Arm.RIGHT);  //changed myo.getArm() to Arm.RIGHT
        mHitLong = PhysicsEngine.arrowFlightLongitude(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), 100, PhysicsEngine.mass, orientationInvent, Arm.RIGHT);
        intent.putExtra(ResultMapActivity.HIT_LATITUDE, mHitLat);
        intent.putExtra(ResultMapActivity.HIT_LONGITUDE, mHitLong);
        startActivity(intent);
    }


    @Override
    public void onConnected(Bundle bundle) {
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        Log.i(LOG_TAG, mCurrentLocation.toString());
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GoogleApiAvailability.getErrorDialog()
            // TODO: handle error
            Log.e(LOG_TAG, "Error connecting to Google API");
            mResolvingError = true;
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(STATE_RESOLVING_KEY, mResolvingError);
        savedInstanceState.putDouble(TARGET_LATITUDE_KEY, mTargetLat);
        savedInstanceState.putDouble(TARGET_LONGITUDE_KEY, mTargetLong);
        super.onSaveInstanceState(savedInstanceState);
    }
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.keySet().contains(STATE_RESOLVING_KEY)) {
            mResolvingError = savedInstanceState.getBoolean(STATE_RESOLVING_KEY);
        }
        if (savedInstanceState.keySet().contains(TARGET_LATITUDE_KEY)) {
            mTargetLat = savedInstanceState.getDouble(TARGET_LATITUDE_KEY);
        }
        if (savedInstanceState.keySet().contains(TARGET_LONGITUDE_KEY)) {
            mTargetLong = savedInstanceState.getDouble(TARGET_LONGITUDE_KEY);
        }
    }

    private double calcDistance() {
        List<Vector3> velValues = new ArrayList<>();
        List<Long> velTimes = new ArrayList<>();
        for (int i = 1; i < mAccelValues.size(); i++) {
            Vector3 a = mAccelValues.get(i);
            Log.d(LOG_TAG, Long.toString(mAccelTimes.get(i)) + ", " + a.toString());
            long dt = mAccelTimes.get(i) - mAccelTimes.get(i - 1);
            Vector3 v = new Vector3(a.x()*dt, a.y()*dt, a.z()*dt);
            velValues.add(v);
            velTimes.add(mAccelTimes.get(i));
        }
        Vector3 finalPos = new Vector3(0,0,0);
        for (int i = 1; i < velValues.size(); i++) {
            Vector3 v = velValues.get(i);
            long dt = velTimes.get(i) - velTimes.get(i - 1);
            Vector3 p = new Vector3(v.x()*dt, v.y()*dt, v.z()*dt);
            finalPos.add(p);
        }
        velValues = new ArrayList<>();
        velTimes = new ArrayList<>();
        List<Vector3> mAverageAccel = movingAverage(mAccelValues, 3);
        for (int i = 1; i < mAverageAccel.size(); i++) {
            Vector3 a = mAverageAccel.get(i);
            //Log.d(LOG_TAG, Long.toString(mAccelTimes.get(i)) + ", " + a.toString());
            long dt = mAccelTimes.get(i) - mAccelTimes.get(i - 1);
            Vector3 v = new Vector3(a.x()*dt, a.y()*dt, a.z()*dt);
            velValues.add(v);
            velTimes.add(mAccelTimes.get(i));
        }
        Vector3 avgFinalPos = new Vector3(0,0,0);
        for (int i = 1; i < velValues.size(); i++) {
            Vector3 v = velValues.get(i);
            long dt = velTimes.get(i) - velTimes.get(i - 1);
            Vector3 p = new Vector3(v.x()*dt, v.y()*dt, v.z()*dt);
            avgFinalPos.add(p);
        }
        Log.d(LOG_TAG, "Average Distance: " + Double.toString(avgFinalPos.length()));
        return finalPos.length();
    }

    private List<Vector3> movingAverage(List<Vector3> accels, int sampleSize) {
        List<Vector3> result = new ArrayList<>(accels.size());
        if (accels.size() >= sampleSize) {
            for (int i = 0; i < accels.size(); i++) {
                int count = 0;
                Vector3 avg = new Vector3(accels.get(i));
                for (int j = i; j < i + sampleSize; j++) {
                    if ((j >= accels.size())) {
                        break;
                    }
                    avg.add(accels.get(i));
                    count++;
                }
                avg.divide((double) count);
                result.add(avg);
            }
            for (Vector3 current: result) {
                Log.d(LOG_TAG, current.toString());
            }
            return result;
        }
        return accels;
    }
}
