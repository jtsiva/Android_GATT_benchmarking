package edu.nd.cse.gatt_server;

import edu.nd.cse.benchmarkcommon.BenchmarkProfile;
import edu.nd.cse.benchmarkcommon.SaveToFileRunnable;
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
* */
public class BenchmarkProfileServer extends BenchmarkProfile {
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
     */
    public BenchmarkProfileServer(Context context){
        File path = context.getExternalFilesDir(null);
        setTimeStampFile(new File(path, "gatt_cap.txt"));

        mTimeDiffs = new String[MAX_DIFFS];

        mGattServer = new GattServer (context, createBenchmarkService());

    }

    /**
     *
     */
    public void start() {
        mGattServer.start();
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
     * Use to write any data collected to a file. This is a clean up function
     * that is intended to be called before the application closes.
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
