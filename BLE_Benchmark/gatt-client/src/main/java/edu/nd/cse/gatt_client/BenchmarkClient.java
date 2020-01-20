package edu.nd.cse.gatt_client;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.widget.TextView;
import android.app.Activity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Activity that runs the client side of the BLE benchmark
 */
public class BenchmarkClient extends Activity{
    /* UI elements */

    private TextView mUpdates;

    /* Profile related things */
    private BenchmarkProfileClient mBenchmarkClient;

    /* Default parameters */
    private final int DEFAULT_MTU = 20;
    private final int DEFAULT_DATA_SIZE = 20;
    private final String DEFAULT_COMM_METHOD = "write_req";
    private final int DEFAULT_CONN_INTERVAL = 40;
    private final int DEFAULT_DURATION = 10000;
    private final int DEFAULT_DURATION_IS_TIME  = 1;


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
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();

        Bundle receiveBundle = this.getIntent().getExtras();
        if (null == receiveBundle) {
            receiveBundle = new Bundle();
        }
        final int dataSize = receiveBundle.getInt("dataSize", DEFAULT_DATA_SIZE);
        final int connInterval = receiveBundle.getInt("connInterval", DEFAULT_CONN_INTERVAL);
        final int mtu = receiveBundle.getInt("mtu", DEFAULT_MTU);
        final int duration = receiveBundle.getInt("duration", DEFAULT_DURATION);
        final int durationIsTime = receiveBundle.getInt("durationIsTime", DEFAULT_DURATION_IS_TIME);
        final String commMethod = receiveBundle.getString("commMethod", DEFAULT_COMM_METHOD);


        mUpdates = (TextView) findViewById(R.id.updates);

        writeUpdate("Parameters:");
        //Here we would append a text version of all of the parameters
        writeUpdate("\tMethod: " + commMethod);
        writeUpdate("\tMTU: " + String.valueOf(mtu));
        writeUpdate("\tData Size: " + String.valueOf(dataSize));
        writeUpdate("\tConn Interval: " + String.valueOf(connInterval));
        writeUpdate("\tDuration: " + String.valueOf(duration) + (1 == durationIsTime? "ms" : "bytes"));
        writeUpdate("----------------------------");

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mBenchmarkClient = new BenchmarkProfileClient (this, new BenchmarkProfileClientCallback () {
            @Override
            public void onBenchmarkStart () {
                Timestamp ts = new Timestamp(new Date().getTime());
                writeUpdate("Benchmark started at: " + ts);
            }

            @Override
            public void onBenchmarkComplete (){
                Timestamp ts = new Timestamp(new Date().getTime());
                writeUpdate("Benchmark completed at: " + ts);

                mBenchmarkClient.requestThroughput();
            }

            @Override
            public void onRawDataAvailable (String [] data){
                //nothing
            }

            @Override
            public void onThroughputAvailable (final float throughput){
                writeUpdate("Throughput: " + throughput);
            }

            @Override
            public void onLossRateAvailable (final float lossRate){
                writeUpdate("Loss Rate: " + lossRate);
            }

            @Override
            public void onBenchmarkError (final int code, final String details) {
                writeUpdate("Error " + code + ": " + details);
            }

            @Override
            public void onLatencyMeasurementsAvailable (final long [] measurements) {
                writeUpdate(measurements.length + " measurements available");
            }
        });

        mBenchmarkClient.prepare(mtu, connInterval, dataSize);
        mBenchmarkClient.beginBenchmark(duration, 1 == durationIsTime);
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onStop(){
        super.onStop();
        mBenchmarkClient.cleanup();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}