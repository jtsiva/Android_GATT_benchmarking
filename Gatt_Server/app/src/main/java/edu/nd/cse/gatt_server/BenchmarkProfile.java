package edu.nd.cse.gatt_server;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.SystemClock;
import android.text.TextUtils;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.util.UUID;

/**
* Implementation of our benchmark profile which will be used with
* a corresponding benchmarking client to run BLE benchmarking tests
* */
public class BenchmarkProfile {
    private static final String TAG = BenchmarkProfile.class.getSimpleName();

    private String [] mTimeDiffs; //array to hold the delta between packet ends
    private long mStartTS = 0; //timestamp from when we're told to start timing
    private int mDiffsIndex = 0;
    private final int MAX_DIFFS = 1000;
    private File mTimeDiffsFile = null;
    private Thread bgThread = null;


    public static final UUID BENCHMARK_SERVICE = UUID.fromString("00000001-0000-1000-8000-00805F9B34FB");

    //This could be used to report the results of the benchmark to the client
    //NOT USED AS OF NOW
    public static UUID QUERY_CHAR = UUID.fromString("00000002-0000-1000-8000-00805F9B34FB");

    //The below characterstics will change based what communication method is used

    //GATT client will write to this characteristic
    public static UUID W_CHAR   = UUID.fromString("00000003-0000-1000-8000-00805F9B34FB");

    /**
     * Initialize the time diffs array
     */
    public BenchmarkProfile (){
        mTimeDiffs = new String[MAX_DIFFS];
    }

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Current Time Service.
     */
    public static BluetoothGattService createBenchmarkService() {
        BluetoothGattService service = new BluetoothGattService(BENCHMARK_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic writeChar = new BluetoothGattCharacteristic(W_CHAR,
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic (writeChar);

        return service;
    }

    /**
     * Set the output file for writing the time diffs
     * @param file - the output file
     */
    public void setTimeStampFile (File file) {
        mTimeDiffsFile = file;
        bgThread = new Thread(new SaveToFileRunnable(mTimeDiffsFile, "inter-packet_time\n".getBytes(), false));
        bgThread.start();
    }

    /**
     * Grab the time stamp so that we can track the duration of an event
     */
    public void startTiming() {
        mStartTS = SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Record the time difference from when startTiming() was called. If we
     * have filled the array then write the array to a file (if the file has
     * been set).
     */
    public void recordTimeDiff() {
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
     * Use to write any data collected to a file. This is a clean up function
     * that is intended to be called before the application closes.
     */
    public void finish() {
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
