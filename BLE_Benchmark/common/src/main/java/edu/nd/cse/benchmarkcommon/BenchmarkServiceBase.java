package edu.nd.cse.benchmarkcommon;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Random;

import BenchmarkService;

public class BenchmarkServiceBase extends BenchmarkService {

    protected int mRole = 0;

    /* Time recording variables */
    protected final int MAX_RECORDS = 16000;
    protected final int SENDER_LATENCY = 0;
    protected final int RECEIVER_LATENCY = 1;
    protected final int OP_LATENCY = 2;
    protected final int TIMING_TYPES = 3;

    protected long [][] mLatency = new long[TIMING_TYPES][MAX_RECORDS];
    protected int [] mLatencyIndex = new int [TIMING_TYPES];
    protected int [] mStartTS = new int [TIMING_TYPES]; //timestamp from when we're told to start timing

    protected long mStartScanning = 0;
    protected long mLatencyStartup = 0;


    /* Benchmark-related variables*/
    protected long mBenchmarkStart = 0; //nanoseconds
    protected long mBenchmarkDuration = 0;
    protected boolean mBenchmarkDurationIsTime;
    protected long mBenchmarkBytesSent = 0;
    protected long mBytesReceived = 0;
    protected long mPacketsReceived = 0;

    protected boolean mBenchmarkStarted = false;
    protected Handler mBenchmarkHandler = new Handler();
    protected boolean mRun;


    /* performance parameters */
    private int mMtu = 20;
    private boolean mMtuState;
    private int mConnInterval = 0; //balanced
    private boolean mConnIntervalState;
    private int mDataSize = 20;
    private boolean mDataSizeState;
    private int mCommMethod = BenchmarkService.WRITE_REQ;
    private boolean mCommMethodState;

    public void BenchmarkServiceBase (int role){
        mRole = role;

        //initialize for use later
        for (int i = 0; i < TIMING_TYPES; i++){
            mLatencyIndex[i] = 0;
            mStartTS[i] = 0;
        }

    }


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
    protected Runnable goTest = new Runnable () {
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

            this.handleTestCharSend(data);


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

    /**
     *
     * @param data
     * @return
     */
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

        if (BenchmarkService.CLIENT == mRole && BenchmarkService.NOTIFY != mCommMethod){
            recordTime(OP_LATENCY, this.getLong(data));
        } else if (BenchmarkService.SERVER == mRole && BenchmarkService.NOTIFY == mCommMethod) {
            if ()
        }

        if (!mBenchmarkStarted) {
            mCB.onBenchmarkStart();
            mBenchmarkStarted = true;
        }

        if (null != data && null != data.mBuffer) {
            mBytesReceived += data.mBuffer.length;
            mPacketsReceived += 1;
            if (!timerStarted(RECEIVER_LATENCY)) {
                startTiming(RECEIVER_LATENCY);
            } else {
                recordTimeDiff(RECEIVER_LATENCY);
            }

            response = data;
            response.mBuffer = null;
        } else {
            Log.w(TAG, "null data received!");
        }


        return response;
    }

    /**
     *
     * @param data - data to be sent
     * @return response object (
     */
    protected GattData handleTestCharSend(GattData data){
        //Now either one of the following
        //mGattClient.handleCharacteristic(data);
        //mGattServer.handleCharacteristic(data);

        if (!timerStarted(SENDER_LATENCY)) {
            startTiming(SENDER_LATENCY);
        } else {
            recordTimeDiff(SENDER_LATENCY);
        }

        return null;
    }

    /**
     * Grab the time stamp so that we can track the duration of an event
     *
     * @param timeType - int to distinguish different timing events
     */
    protected void startTiming(int timeType) {
        mStartTS[timeType] = SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Convenience function to determing whether timer has been started
     *
     * @param timeType - int to distinguish different timing events
     * @return true if the timer has been started, false otherwise
     */
    protected boolean timerStarted(int timeType) { return !(0 == mStartTS[timeType]); }

    /**
     * Record the time difference from when startTiming() was called. If we
     * have filled the array then write the array to a file (if the file has
     * been set).
     *
     * @param timeType - int to distinguish different timing events
     */
    protected void recordTimeDiff(int timeType) {
        long ts = SystemClock.elapsedRealtimeNanos();
        long diff = 0;

        if (0 == mStartTS[timeType]) {
            Log.w (TAG, "Tried to record time stamp when timing not started");

        } else {
            diff = ts - mStartTS;

            mLatency[timeType][mLatencyIndex[timeType]] = diff;
            //Log.d(TAG, "recording time difff: " +  mTimeDiffs[mDiffsIndex]);
            ++mLatencyIndex[timeType];

        }

    }

    /**
     * Record the curren time to appropriate list
     *
     * @param timeType - int to distinguish different timing events
     */
    protected void recordTime(int timeType) {
        mLatency[timeType][mLatencyIndex[timeType]] = SystemClock.elapsedRealtimeNanos();
        ++mLatencyIndex[timeType];
    }

    /**
     * Record the time provided to the appropriate array
     *
     * @param timeType - int to distinguish different timing events
     * @param time - time to record
     */
    protected void recordTime(int timeType, long time) {
        mLatency[timeType][mLatencyIndex[timeType]] = time;
        ++mLatencyIndex[timeType];
    }


}
