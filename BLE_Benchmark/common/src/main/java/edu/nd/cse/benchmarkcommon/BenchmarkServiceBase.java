package edu.nd.cse.benchmarkcommon;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;


public class BenchmarkServiceBase extends BenchmarkService implements Runnable, ConnectionUpdaterIFace {

    private static final String TAG = BenchmarkServiceBase.class.getSimpleName();

    protected int mRole = 0;
    protected int mRequiredConnections = 1;
    protected List<String> mConnections = new ArrayList<String>();
    protected BenchmarkServiceCallback mCB = null;

    /* Multi-connection transmit policy */
    private static final int MAX_PACKETS_BUFFERED = 64;
    private int mCurrentConnection = 0;
    private int mPacketsBuffered = 0;
    private int mTargetPPCE = MAX_PACKETS_BUFFERED;


    /* Time recording variables */
    protected final int MAX_RECORDS = 16000;
    protected final int SENDER_LATENCY = 0;
    protected final int RECEIVER_LATENCY = 1;
    protected final int OP_LATENCY = 2;
    protected final int TIMING_TYPES = 3;

    //breakdown of times based on time types and associated device
    protected Map<Key, List<Long>> mLatency = new HashMap<Key, List<Long>>();
    protected Map<Key, Integer> mLatencyIndex = new HashMap<Key, Integer>();
    protected Map<Key, Long>  mStartTS = new HashMap<Key, Long>(); //timestamp from when we're told to start timing

    protected long mStartScanning = 0;
    protected long mLatencyStartup = 0;


    /* Benchmark-related variables*/
    protected long mBenchmarkStart = 0; //nanoseconds
    protected long mBenchmarkDuration = 0;
    protected boolean mBenchmarkDurationIsTime;
    protected long mBenchmarkBytesSent = 0;
    protected long mBytesReceived = 0;
    protected long mPacketsReceived = 0;

    protected int mConnectionsReady = 0;

    protected boolean mBenchmarkStarted = false;
    protected Handler mBenchmarkHandler = new Handler();
    protected boolean mRun;


    /* performance parameters */
    //TODO: later change these to maps so that different connections can have different params
    protected int mMtu = 20;
    protected boolean mMtuState;
    protected int mConnInterval = 0; //balanced
    protected boolean mConnIntervalState;
    protected int mDataSize = 20;
    protected boolean mDataSizeState;
    protected int mCommMethod = BenchmarkService.WRITE_REQ;
    protected boolean mCommMethodState;

    public BenchmarkServiceBase (int role){
        mRole = role;
    }

    /**
     * Prepare the benchmark with some parameters that are relevant for both
     * client and server
     *
     * @param requiredConnections - the number of connections to establish
     *                            before starting the benchmark
     * @param targetPPCE - the number of packets we want to try to send each CE
     */
    public void prepare(int requiredConnections, int targetPPCE){
        mRequiredConnections = requiredConnections;
        mTargetPPCE = targetPPCE;
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
    @Override
    public void run() {
        int packetSize = mDataSize;
        if (!mBenchmarkDurationIsTime &&
                packetSize + mBenchmarkBytesSent > mBenchmarkDuration){

            packetSize = Math.toIntExact(mBenchmarkDuration - mBenchmarkBytesSent);
        }
        byte [] b = new byte[packetSize];
        new Random().nextBytes(b);
        //TODO: multi-connection send policy

        if (mPacketsBuffered == mTargetPPCE) {
            //once we've buffered a sufficient number of packets for this connection
            //move onto the next connection
            mCurrentConnection = (mCurrentConnection + 1) % mConnections.size();
            mPacketsBuffered = 0;
        }

        GattData data = new GattData(mConnections.get(mCurrentConnection), BenchmarkService.TEST_CHAR, b);
        ++mPacketsBuffered;

        //only mark the bytes sent when we are buffering for the final connection
        if (mCurrentConnection == mConnections.size() - 1){
            mBenchmarkBytesSent += packetSize;
        }


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

        if (!mBenchmarkStarted) {
            mCB.onBenchmarkStart();
            mBenchmarkStarted = true;
        }

        if (null != data && null != data.mBuffer) {
            mBytesReceived += data.mBuffer.length;
            mPacketsReceived += 1;
            if (!timerStarted(RECEIVER_LATENCY, data.mAddress)) {
                startTiming(RECEIVER_LATENCY, data.mAddress);
            } else {
                recordTimeDiff(RECEIVER_LATENCY, data.mAddress);
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

        if (!timerStarted(SENDER_LATENCY, data.mAddress)) {
            startTiming(SENDER_LATENCY, data.mAddress);
        } else {
            recordTimeDiff(SENDER_LATENCY, data.mAddress);
        }

        return null;
    }

    /**
     * Grab the time stamp so that we can track the duration of an event
     *
     * @param timeType - int to distinguish different timing events
     * @param address - the address of the device for this connection
     */
    protected void startTiming(int timeType, String address) {
        mStartTS.put(new Key(timeType, address), SystemClock.elapsedRealtimeNanos());
        mLatency.put(new Key(timeType, address), new ArrayList<Long> ());
        mLatencyIndex.put(new Key(timeType, address), 0);
    }

    /**
     * Convenience function to determing whether timer has been started
     *
     * @param timeType - int to distinguish different timing events
     * @param address - the address of the device for this connection
     * @return true if the timer has been started, false otherwise
     */
    protected boolean timerStarted(int timeType, String address) {
        return !(null == mStartTS.get(new Key(timeType, address)) );
    }

    /**
     * Record the time difference from when startTiming() was called. If we
     * have filled the array then write the array to a file (if the file has
     * been set).
     *
     * @param timeType - int to distinguish different timing events
     * @param address - the address of the device for this connection
     */
    protected void recordTimeDiff(int timeType, String address) {
        long ts = SystemClock.elapsedRealtimeNanos();
        long diff = 0;

        if (0 == mStartTS.get(new Key(timeType, address))) {
            Log.w (TAG, "Tried to record time stamp when timing not started");

        } else {
            diff = ts - mStartTS.get(new Key(timeType, address));

            mLatency.get(new Key(timeType, address)).add(diff);
            //Log.d(TAG, "recording time difff: " +  mTimeDiffs[mDiffsIndex]);

            mLatencyIndex.put(new Key(timeType, address), mLatencyIndex.get(new Key(timeType, address)) + 1);

        }

    }

    /**
     * Record the curren time to appropriate list
     *
     * @param timeType - int to distinguish different timing events
     * @param address - the address of the device for this connection
     */
    protected void recordTime(int timeType, String address ) {
        if (null == mLatency.get(new Key(timeType, address))) {
            mLatency.put(new Key(timeType, address), new ArrayList<Long> ());
            mLatencyIndex.put(new Key(timeType, address), 0);
        }
        mLatency.get(new Key(timeType, address)).add(SystemClock.elapsedRealtimeNanos());
        mLatencyIndex.put(new Key(timeType, address), mLatencyIndex.get(new Key(timeType, address)) + 1);
    }

    /**
     * Record the time provided to the appropriate array
     *
     * @param timeType - int to distinguish different timing events
     * @param address - the address of the device for this connection
     * @param time - time to record
     */
    protected void recordTime(int timeType, String address, long time) {
        if (null == mLatency.get(new Key(timeType, address))) {
            mLatency.put(new Key(timeType, address), new ArrayList<Long> ());
            mLatencyIndex.put(new Key(timeType, address), 0);

        }
        mLatency.get(new Key(timeType, address)).add(time);
        mLatencyIndex.put(new Key(timeType, address), mLatencyIndex.get(new Key(timeType, address)) + 1);
    }

    /******************************************************************************************
    ConnectionUpdater IFace implementation
     ******************************************************************************************/
    public void commMethodUpdate(String address, int method){
        //do nothing
    }

    public void mtuUpdate(String address, int mtu){
        //do nothing
    }

    public void connIntervalUpdate (String address, int interval){
        //do nothing
    }

    /**
     * Add and remove connected devices from list
     *
     * @param address - address of device
     * @param state - new state of device
     */
    public void connectionUpdate (String address, int state){
        if (1 == state) {
            mConnections.add(address);
        } else {
            mConnections.remove(address);
            --mConnectionsReady;
        }
    }

}
