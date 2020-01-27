package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.SaveToFileRunnable;
import edu.nd.cse.benchmarkcommon.BluetoothRestarter;
import edu.nd.cse.benchmarkcommon.BenchmarkProfile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.widget.TextView;
import android.app.Activity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;
import android.os.Build;

import java.io.File;
import java.sql.Timestamp;
import java.util.Date;
import java.text.SimpleDateFormat;


/**
 * Activity that runs the client side of the BLE benchmark
 */
public class BenchmarkClient extends Activity{
    private static final String TAG = BenchmarkProfileClient.class.getSimpleName();

    private BluetoothRestarter mBTRestarter = new BluetoothRestarter(this);

    /* UI elements */

    private TextView mUpdates;

    /* Profile related things */
    private BenchmarkProfileClient mBenchmarkClient;

    /* Default parameters */
    private final int DEFAULT_MTU = 23;
    private final int DEFAULT_DATA_SIZE = 20;
    private final int DEFAULT_COMM_METHOD = BenchmarkProfile.WRITE_REQ;
    private final int DEFAULT_CONN_INTERVAL = 0;
    private final int DEFAULT_DURATION = 10000;
    private final int DEFAULT_DURATION_IS_TIME  = 1;

    private Thread mWriteStartupLatencyThread = null;
    private Thread mWritePayloadLatencyThread = null;
    private Thread mWriteOpLatencyThread = null;
    private Thread mWriteJitterThread = null;

    private String mServerID = new String ("?");
    private long mStartupLatency = 0;

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


    /**
     * Write the recorded start-up latency to a file
     *
     * @param mtu the link layer maximum transmission unit used for the test
     * @param comm_method the method of communication used for the test
     * @param latencyStartup the reported start-up latency
     */
    private void writeStartupLatencyToFile(String clientID, String serverID,
                                    int mtu, String comm_method,
                                    long latencyStartup) {
        String timeSuffix = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File file = new File(this.getExternalFilesDir(null), "latency_startup-" + timeSuffix + ".csv");
        StringBuilder out = new StringBuilder("client_device_id, server_device_id, phone_vendor, bt_version, bt_vendor, mtu, comm_method, latency_startup\n");
        out.append(clientID + ", " + serverID + ", unknown, unknown, unknown, " + String.valueOf(mtu)
                + ", " + comm_method + "," + String.valueOf(latencyStartup) +
                "\n");

        mWriteStartupLatencyThread = new Thread(new SaveToFileRunnable(file, out.toString().getBytes(), false));
        mWriteStartupLatencyThread.start();
    }

    /**
     * Write the recorded payload latency to a file
     *
     * @param mtu the link layer maximum transmission unit used for the test
     * @param comm_method the method of communication used for the test
     * @param latencyPayload
     */
    private void writePayloadLatencyToFile ( String clientID, String serverID,
                                            int mtu, String comm_method,
                                            long latencyPayload) {
        String timeSuffix = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File file = new File(this.getExternalFilesDir(null), "latency_payload-" + timeSuffix + ".csv");
        StringBuilder out = new StringBuilder("client_device_id, server_device_id, phone_vendor, bt_version, bt_vendor, mtu, comm_method, latency_payload\n");
        out.append(clientID + ", " + serverID + ", unknown, unknown, unknown, " + String.valueOf(mtu)
                + ", " + comm_method + "," + String.valueOf(latencyPayload) +
                "\n");

        mWritePayloadLatencyThread = new Thread(new SaveToFileRunnable(file, out.toString().getBytes(), false));
        mWritePayloadLatencyThread.start();
    }

    /**
     * Write all of the recorded op latencies to a file
     *
     * @param mtu the link layer maximum transmission unit used for the test
     * @param comm_method the method of communication used for the test
     * @param opLatency the times from the initiation of an op to its return
     */
    private void writeOpLatencyToFile (     String clientID, String serverID,
                                            int mtu, String comm_method,
                                            long [] opLatency) {
        String timeSuffix = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File file = new File(this.getExternalFilesDir(null), "latency_op_return-" + timeSuffix + ".csv");
        StringBuilder out = new StringBuilder("client_device_id, server_device_id, phone_vendor, bt_version, bt_vendor, mtu, comm_method, latency_op_return\n");

        for (long time : opLatency) {
            out.append(clientID + ", " + serverID + ", unknown, unknown, unknown, " + String.valueOf(mtu)
                    + ", " + comm_method + "," + String.valueOf(time) +
                    "\n");
        }
        mWriteOpLatencyThread = new Thread(new SaveToFileRunnable(file, out.toString().getBytes(), false));
        mWriteOpLatencyThread.start();
    }

    /**
     * Write all of the recorded jitter measurements to a file
     *
     * @param mtu the link layer maximum transmission unit used for the test
     * @param comm_method the method of communication used for the test
     * @param jitter the timestamps (since the start) of consecutive messages
     */
    private void writeJitterToFile (   String clientID, String serverID,
                                       int mtu, String comm_method,
                                       long [] jitter) {
        String timeSuffix = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        File file = new File(this.getExternalFilesDir(null), "jitter-" + timeSuffix + ".csv");
        StringBuilder out = new StringBuilder("client_device_id, server_device_id, phone_vendor, bt_version, bt_vendor, mtu, comm_method, jitter\n");

        for (long time : jitter) {
            out.append(clientID + ", " + serverID + ", unknown, unknown, unknown, " + String.valueOf(mtu)
                    + ", " + comm_method + "," + String.valueOf(time) +
                    "\n");
        }
        mWriteJitterThread = new Thread(new SaveToFileRunnable(file, out.toString().getBytes(), false));
        mWriteJitterThread.start();
    }

    /**
     * Determine the appropriate string to return given the integer
     * representation of the communication method. For UI purposes.
     *
     * @param commMethod the communication method as defined in BenchmarkProfile
     * @return the appropriate name for the comm method
     */
    private String getCommMethodString (int commMethod) {
        String retStr;
        switch(commMethod) {
            case BenchmarkProfile.WRITE_REQ:
                retStr = BenchmarkProfile.WRITE_REQ_STR;
                break;
            case BenchmarkProfile.WRITE_CMD:
                retStr = BenchmarkProfile.WRITE_CMD_STR;
                break;
            case BenchmarkProfile.READ:
                retStr = BenchmarkProfile.READ_STR;
                break;
            case BenchmarkProfile.NOTIFY:
                retStr = BenchmarkProfile.NOTIFY_STR;
                break;
            default:
                retStr = "unknown";
                break;
        }

        return retStr;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();

    }

    /**
     * Get this parameters from the intent, set up the callbacks, and run the
     * benchmark
     */
    private void runBenchmark(){
        Bundle receiveBundle = this.getIntent().getExtras();
        if (null == receiveBundle) {
            receiveBundle = new Bundle();
        }
        final int dataSize = receiveBundle.getInt("dataSize", DEFAULT_DATA_SIZE);
        final int connInterval = receiveBundle.getInt("connInterval", DEFAULT_CONN_INTERVAL);
        final int mtu = receiveBundle.getInt("mtu", DEFAULT_MTU);
        final int duration = receiveBundle.getInt("duration", DEFAULT_DURATION);
        final int durationIsTime = receiveBundle.getInt("durationIsTime", DEFAULT_DURATION_IS_TIME);
        final int commMethod = receiveBundle.getInt("commMethod", DEFAULT_COMM_METHOD);


        mUpdates = (TextView) findViewById(R.id.updates);

        writeUpdate("Parameters:");
        //Here we append a text version of all of the parameters
        writeUpdate("\tMethod: " + getCommMethodString(commMethod));
        writeUpdate("\tMTU: " + String.valueOf(mtu));
        writeUpdate("\tData Size: " + String.valueOf(dataSize));
        writeUpdate("\tConn Interval: " + String.valueOf(connInterval));
        writeUpdate("\tDuration: " + String.valueOf(duration) + (1 == durationIsTime? " ms" : " bytes"));
        writeUpdate("\tClient ID: " + Build.DISPLAY);
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
                writeUpdate("Benchmark ended at: " + ts);

                mBenchmarkClient.requestServerID();
                mBenchmarkClient.requestLatencyMeasurements();
            }

            @Override
            public void onStartupLatencyAvailable (final long startLatency){
                writeUpdate("Start-up latency: " + startLatency);
                mStartupLatency = startLatency;
            }

            @Override
            public void onBytesSentAvailable (long bytesSent) {
                writeUpdate("Sent " + bytesSent + " bytes sent");
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
            public void onLatencyMeasurementsAvailable (final long [] clientMeasurements,
                                                        final long [] serverMeasurements) {
                writeUpdate(clientMeasurements.length + " client measurements available");
                writeUpdate(serverMeasurements.length + " server measurements available");
                mBenchmarkClient.requestThroughput();
                writeUpdate("Writing results to file...");
                writeStartupLatencyToFile (Build.DISPLAY, mServerID, mtu, getCommMethodString(commMethod), mStartupLatency);
                writePayloadLatencyToFile(Build.DISPLAY, mServerID, mtu, getCommMethodString(commMethod), serverMeasurements[serverMeasurements.length - 1]);
                writeOpLatencyToFile(Build.DISPLAY,mServerID, mtu, getCommMethodString(commMethod), clientMeasurements);
                writeJitterToFile(Build.DISPLAY,mServerID, mtu, getCommMethodString(commMethod), serverMeasurements);

                mCloseHandler.postDelayed(new Runnable() {
                    public void run() {
                        /* Wait for writes to be done and then exit */
                        try {
                            mWriteStartupLatencyThread.join();
                            mWritePayloadLatencyThread.join();
                            mWriteOpLatencyThread.join();
                            mWriteJitterThread.join();
                        } catch (InterruptedException e){
                            //meh
                            Log.w(TAG, "writes were interrupted");
                        }

                        mBenchmarkClient.cleanup();

                        int pid = android.os.Process.myPid();
                        android.os.Process.killProcess(pid);
                    }
                }, 5000);


            }

            @Override
            public void onServerIDAvailable(String id) {
                writeUpdate("Server ID: " + id);
                mServerID = id;
            }
        });

        mBenchmarkClient.prepare(mtu, connInterval, dataSize, commMethod);
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