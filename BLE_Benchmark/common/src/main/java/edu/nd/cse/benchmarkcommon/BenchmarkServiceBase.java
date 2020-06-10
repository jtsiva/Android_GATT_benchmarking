package edu.nd.cse.benchmarkcommon;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Random;

import BenchmarkService;

public class BenchmarkServiceBase extends BenchmarkService {

    private long mStartTS = 0; //timestamp from when we're told to start timing

    private boolean mBenchmarkStarted = false;

    private Handler mBenchmarkHandler = new Handler();

    private boolean mRun;


    /* Benchmark-related variables*/
    private long mBenchmarkStart = 0; //nanoseconds
    private long mBenchmarkDuration = 0;
    private boolean mBenchmarkDurationIsTime;
    private long mBenchmarkBytesSent = 0;
    private long mBytesReceived = 0;
    private long mPacketsReceived = 0;

    private long mStartScanning = 0;
    private long mLatencyStartup = 0;
    private long mSenderLatency[] = new long[16000]; //TODO: clear up time records since we need 2 at most
    private long mReceiverLatency[] = new long[16000];
    private int mLatencyIndex = 0;
    private int mReceiverLatencyIndex = 0;


    /* performance parameters */
    private int mMtu = 20;
    private boolean mMtuState;
    private int mConnInterval = 0; //balanced
    private boolean mConnIntervalState;
    private int mDataSize = 20;
    private boolean mDataSizeState;
    private int mCommMethod = BenchmarkService.WRITE_REQ;
    private boolean mCommMethodState;


    /**
     * Create some random bytes and pass them off to the Gatt layer directed
     * at the test device. Schedule to do this again after conn interval ms if
     * this will not exceed the benchmark duration. Call onBenchmarkComplete
     * when done.
     *
     * Data to be sent is a pseudo-random collection of bits as suggested by
     * RFC4814 (https://tools.ietf.org/html/rfc4814#section-3). Since the data
     * to be sent *could* be encoded or compressed, it is imperative to not
     * just test using alpha-numeric characters.
     */
    private Runnable goTest = new Runnable () {
        @Override
        public void run() {
            int packetSize = mDataSize;
            if (!mBenchmarkDurationIsTime &&
                    packetSize + mBenchmarkBytesSent > mBenchmarkDuration){

                packetSize = Math.toIntExact(mBenchmarkDuration - mBenchmarkBytesSent);
            }
            byte [] b = new byte[packetSize];
            new Random().nextBytes(b);
            GattData data = new GattData(mServerAddress, BenchmarkService.TEST_CHAR, b);
            mBenchmarkBytesSent += packetSize;

            handleTestCharSend(data);


            long now = SystemClock.elapsedRealtimeNanos ();

            if (mBenchmarkDurationIsTime) {
                // if one more interval will not exceed the duration then post delayed
                if ((now - mBenchmarkStart) + (mConnInterval * 1000000) < mBenchmarkDuration
                        && mRun) {
                    mBenchmarkHandler.postDelayed(this, mConnInterval);
                }
                else {
                    mCB.onBenchmarkComplete();
                    mCB.onBytesSentAvailable(mBenchmarkBytesSent);
                }
            } else {
                if (mBenchmarkBytesSent < mBenchmarkDuration) {
                    mBenchmarkHandler.postDelayed(this, mConnInterval);
                }
                else {
                    mCB.onBenchmarkComplete();
                    mCB.onBytesSentAvailable(mBenchmarkBytesSent);
                }
            }

        }
    };


    protected long getLong (GattData data) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(data.mBuffer);
        buffer.flip();//need flip

        return buffer.getLong();
    }

    /**
     * Since the data will be junk, just count the number of bytes received,
     * increment the number of packets, and record the time in the case of
     * writes. If we are using notifications, then we will be sent the byte
     * duration to use.
     *
     * @return data with sender address, char uuid, and null buffer
     */
    protected GattData handleTestCharReceive(GattData data) {
        GattData response = null;

        if (!mBenchmarkStarted) {
            mCB.onBenchmarkStart();
            mBenchmarkStarted = true;
        }

        if (null != data && null != data.mBuffer) {
            mBytesReceived += data.mBuffer.length;
            mPacketsReceived += 1;
            if (!timerStarted()) {
                startTiming();
            } else {
                recordTimeDiff();
            }

            response = data;
            response.mBuffer = null;
        } else {
            Log.w(TAG, "null data received!");
        }


        return response;
    }

    protected GattData handleTestCharSend(GattData data){
        //Now either one of the following
        //mGattClient.handleCharacteristic(data);
        //mGattServer.handleCharacteristic(data);

        //record time stamp?
    }

    /**
     * Grab the time stamp so that we can track the duration of an event
     *
     * TODO: expand to allow timing multiple events
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

            mReceiverLatency[mReceiverLatencyIndex] = diff;
            //Log.d(TAG, "recording time difff: " +  mTimeDiffs[mDiffsIndex]);
            ++mReceiverLatencyIndexd;

        }

    }


}
