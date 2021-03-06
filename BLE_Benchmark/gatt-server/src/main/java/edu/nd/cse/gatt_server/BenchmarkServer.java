package edu.nd.cse.gatt_server;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;
import android.os.Handler;

import java.util.Date;
import java.sql.Timestamp;

import edu.nd.cse.benchmarkcommon.BluetoothRestarter;

/**
 * Activity that runs the gatt server. Receives operating parameters
 * and passes to server.
 */
public class BenchmarkServer extends Activity{

    private static final String TAG = BenchmarkServer.class.getSimpleName();

    private BluetoothRestarter mBTRestarter = new BluetoothRestarter(this);

    /* UI elements */
    private TextView mUpdates;

    /* Benchmarking Profile Server */
    private BenchmarkProfileServer mBenchmarkServer;

    private Handler mCloseHandler = new Handler();

    /**
     * Convenience method to write text to the screen
     * @param text - the text to write to the screen
     */
    private void writeUpdate(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUpdates.append(text);
                mUpdates.append("\n");
            }
        });
    }

    /**
     * Convenience method to determine if the user has granted this application
     * the necessary permissions to be able to run.
     *
     * @param context app context
     * @param permissions the permissions to check
     * @return true if we have the permissions, false otherwise
     */
    private static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Method that checks for permissions and if app has not been given one of
     * the permissions, ask the user to grant the permission.
     */
    private void checkPermissions(){
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

        if(!hasPermissions(this, PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {
            mBTRestarter.restart(new BluetoothRestarter.RestartListener (){
                @Override
                public void onRestartComplete() {
                    runBenchmark();
                }
            });
        }
    }

    /**
     * Catch the result of the permissions request. Allows us to wait until the
     * permissions are granted.
     *
     * @param requestCode original code from request
     * @param permissions location coarse and fine
     * @param grantResults results of permissions request
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        boolean allGranted = true;
        for (int result : grantResults) {
            allGranted &= (result == PackageManager.PERMISSION_GRANTED);
        }

        if (!allGranted) {
            Log.e(TAG, "Not all permissions granted!!!");
        } else {
            mBTRestarter.restart(new BluetoothRestarter.RestartListener (){
                @Override
                public void onRestartComplete() {
                    runBenchmark();
                }
            });
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();

    }

    /**
     * Start the server and prepare to run the benchmark
     */
    public void runBenchmark () {
        mUpdates = (TextView) findViewById(R.id.updates);
        writeUpdate("Parameters:\n");
        //Here we would append a text version of all of the parameters

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //start profile
        mBenchmarkServer = new BenchmarkProfileServer(this, new BenchmarkProfileServerCallback () {
            @Override
            public void onBenchmarkStart () {
                Timestamp ts = new Timestamp(new Date().getTime());
                writeUpdate("Benchmark started at: " + ts);
            }

            @Override
            public void onBenchmarkComplete (){

                Timestamp ts = new Timestamp(new Date().getTime());
                writeUpdate("Benchmark completed at: " + ts);

                mCloseHandler.postDelayed(new Runnable() {
                    public void run() {
                        mBenchmarkServer.stop();
                        int pid = android.os.Process.myPid();
                        android.os.Process.killProcess(pid);
                    }
                }, 5000);

            }

            @Override
            public void onBenchmarkError (final int code, final String details) {
                writeUpdate("Error " + code + ": " + details);

            }
        });

        Log.i(TAG, "Starting benchmark server...");
        mBenchmarkServer.start();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        mBenchmarkServer.stop();
    }
}
