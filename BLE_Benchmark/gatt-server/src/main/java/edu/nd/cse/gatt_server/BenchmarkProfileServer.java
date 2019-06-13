package edu.nd.cse.gatt_server;

import edu.nd.cse.benchmarkcommon.BenchmarkProfile;
import edu.nd.cse.benchmarkcommon.SaveToFileRunnable;
import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.GattData;


import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.content.Context;

import java.io.File;
import java.util.UUID;

/**
* Implementation of our benchmark profile server which will be used with
* a corresponding benchmarking client to run BLE benchmarking tests
*
* The server side exposes the following functionality to the application:
* - start the profile
*   - starts advertising the service and waits for connection
* - stop the profile
*   - stop advertising and shutdown the gatt server
* - stop advertising after connect (x)
*   - boolean on start function to indicate whether or not to continue
*     advertising after a connection has been made
* - log benchmark results to file
*   - boolean on constructor to indicate whether or not raw timestamps
 *    should be logged to a file. If not logged to file then the local
 *    buffer size is increased and treated like a circular buffer with
 *    a maximum number of entries of 10k (may need to experiment)
* */
public class BenchmarkProfileServer extends BenchmarkProfile
                                    implements CharacteristicHandler {
    private static final String TAG = BenchmarkProfileServer.class.getSimpleName();

    private String [] mTimeDiffs; //array to hold the delta between packet ends
    private long mStartTS = 0; //timestamp from when we're told to start timing
    private int mDiffsIndex = 0;
    private final int MAX_DIFFS = 1000;
    private File mTimeDiffsFile = null;
    private Thread bgThread = null;

    private GattServer mGattServer;

    /**
     * Initialize the time diffs array and gatt server
     * TODO: add boolean for logging and change logging procedure
     * @param context - application context
     * @param logToFile - whether to log the raw timestamps to a file
     *                  or store them in a circular buffer in mem
     */
    public BenchmarkProfileServer(Context context, boolean logToFile){
        File path = context.getExternalFilesDir(null);
        setTimeStampFile(new File(path, "gatt_cap.txt"));

        mTimeDiffs = new String[MAX_DIFFS];

        mGattServer = new GattServer (context, createBenchmarkService());

    }

    /**
     * Initialize and log to file by default
     * @param context
     */
    public BenchmarkProfileServer (Context context){
        this(context, true);
    }

    /**
     * Start the gatt server
     */
    public void start(boolean stopAdvertising) {
        mGattServer.start(stopAdvertising);
    }

    /**
     * Start the gatt server and don't stop advertising upon connection
     */
    public void start () {
        mGattServer.start(false);
    }

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     *
     */
    private BluetoothGattService createBenchmarkService() {
        BluetoothGattService service = new BluetoothGattService(BENCHMARK_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic writeChar = new BluetoothGattCharacteristic(BenchmarkProfile.TEST_CHAR,
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic (writeChar);

        return service;
    }

    /**
     *
     * @param data
     * @return
     */
    @Override
    public GattData handleCharacteristic (GattData data) {
        GattData response = null;
        if (BenchmarkProfile.TEST_CHAR.equals(data.mCharID))
        {
            response = handleTestCharacteristic(data);
        }
        else if (BenchmarkProfile.RAW_DATA_CHAR.equals(data.mCharID)){
            response = handleRawDataRequest();
        }
        else if (BenchmarkProfile.THROUGHPUT_CHAR.equals(data.mCharID)){
            response = handleThroughputRequest();
        }
        else if (BenchmarkProfile.LOSS_RATE_CHAR.equals(data.mCharID)){
            response = handleLossRateRequest();
        }

        return response;
    }

    /**
     *
     * @return
     */
    private GattData handleTestCharacteristic (GattData data){
        GattData response = null;

        return response;
    }

    /**
     *
     * @return
     */
    private GattData handleRawDataRequest () {
        GattData response = null;

        return response;
    }

    /**
     *
     * @return
     */
    private GattData handleThroughputRequest () {
        GattData response = null;

        return response;
    }

    /**
     *
     * @return
     */
    private GattData handleLossRateRequest () {
        GattData response = null;

        return response;
    }

    /**
     * Set the output file for writing the time diffs
     * @param file - the output file
     */
    private void setTimeStampFile (File file) {
        mTimeDiffsFile = file;
        bgThread = new Thread(new SaveToFileRunnable(mTimeDiffsFile, "inter-packet_time\n".getBytes(), false));
        bgThread.start();
    }

    /**
     * Grab the time stamp so that we can track the duration of an event
     */
    private void startTiming() {
        mStartTS = SystemClock.elapsedRealtimeNanos();
    }

    /**
     *
     * @return true if the timer has been started, false otherwise
     */
    private boolean timerStarted() { return !(0 == mStartTS); }

    /**
     * Record the time difference from when startTiming() was called. If we
     * have filled the array then write the array to a file (if the file has
     * been set).
     * TODO: alter to choose between circular buffer and file
     */
    private void recordTimeDiff() {
        long ts = SystemClock.elapsedRealtimeNanos();
        long diff = 0;

        if (0 == mStartTS) {
            Log.w (TAG, "Tried to record time stamp when timing not started");

        } else {
            diff = ts - mStartTS;

            mTimeDiffs[mDiffsIndex] = String.valueOf(diff);
            mDiffsIndex++;

            if (MAX_DIFFS == mDiffsIndex) {
                String out = TextUtils.join("\n", mTimeDiffs);
                if (null == mTimeDiffsFile) {
                    Log.w (TAG, "Trying to record times when no output file specified!");
                } else {
                    bgThread = new Thread(new SaveToFileRunnable(mTimeDiffsFile, out.getBytes(), true));
                    bgThread.start();
                }

                mDiffsIndex = 0;
            }
        }

    }

    /**
     * Stop the gatt server and write any data collected to a file. This is a
     * clean up function that is intended to be called before the application
     * closes.
     */
    public void stop() {
        mGattServer.stop();

        if (mDiffsIndex > 0) {
            String[] tmp = new String[mDiffsIndex];
            System.arraycopy(mTimeDiffs, 0, tmp, 0, mDiffsIndex);
            String out = TextUtils.join("\n", tmp);

            if (null == mTimeDiffsFile) {
                Log.w (TAG, "Trying to record times when no output file specified!");
            } else {
                bgThread = new Thread(new SaveToFileRunnable(mTimeDiffsFile, out.getBytes(), true));
                bgThread.start();
                try {
                    bgThread.join();
                } catch (InterruptedException ex) {
                    //nothing--move along
                }
            }
        }
    }

}
