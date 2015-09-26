package apps.bunch.im.archer;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.thalmic.myo.Arm;

/**
 * Physics engine to handle arrow being shot, and return distance traveled by the arrow
 */
public class PhysicsEngine {

    private static final String LOG_TAG = "PhysicsEngine";

    public static final double mass = 500; //kg
    public static final double gravity = 9.81;

    /**
     * @param force force that arrow is exerting in Newtons
     * @return acceleration of the arrow in m/s^2
     */
    private static double acceleration(double force) {
        return force / mass;
    }

    /**
     * @param acceleration acceleration of the arrow as it leaves the bow in m/s^2
     * @return velocity at which the arrow leaves the bow in m/s
     */
    private static double velocity(double acceleration) {
        return Math.sqrt((double) 2 * acceleration);
    }

    /**
     * @param velocity velocity at which the arrow leaves the bow in m/s
     * @param radians  angle at which the arrow leaves the bow
     * @return time that arrow is in the air in s
     */
    private static double time(double velocity, double radians) {
        return (velocity * Math.sin(radians)) / gravity;
    }

    /**
     * @param time     time arrow is in the air in s
     * @param velocity velocity that has when leaving the bow in m/s
     * @param radians  angle at which the arrow leaves the bow in radians
     * @return distance, the distance an object travels in time at velocity in m
     */
    private static double distance(double time, double velocity, double radians) {
        return velocity * Math.cos(radians) * time;
    }

    /**
     * @param force       force of arrow's motion in s
     * @param orientation orientation array of the phone (y,p,r)
     * @return distanceTraveled, the distance traveled by an arrow who's force, mass, and
     * angle (in radians) are given as inputs in m
     */
    private static double distanceTraveled(double force, float[] orientation) {

        double acceleration = acceleration(force);
        double velocity = velocity(acceleration);
        double arrowAngle = arrowAngle(orientation);
        double time = time(velocity, arrowAngle);

        Log.d(LOG_TAG, "Distance accel: " + Double.toString(acceleration));
        Log.d(LOG_TAG, "Distance vel: " + Double.toString(velocity));
        Log.d(LOG_TAG, "Distance arrowAngle: " + Double.toString(arrowAngle));
        Log.d(LOG_TAG, "Distance time: " + Double.toString(time));

        double distance = distance(time, velocity, arrowAngle);

        Log.d(LOG_TAG, "Distance traveled: " + Double.toString(distance));

        return distance;

    }

    //azimuth, pitch, roll,
    //this is for angle of launch (shoulder tilt)

    /**
     * @param angles angles vector that contains azimuth, pitch and roll
     * @return the pitch, the angle at which the arrow leaves the bow in radians
     */
    private static double arrowAngle(float[] angles) {
        return angles[1];
    }

    public static LatLng arrowFlightLatLng(LatLng source, double force, float[] orientation) {
        return SphericalUtil.computeOffset(source, distanceTraveled(force, orientation), Math.toDegrees(orientation[0]));
    }

}
