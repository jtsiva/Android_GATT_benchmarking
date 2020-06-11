package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.BenchmarkService;
import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.ConnectionUpdater;
import edu.nd.cse.benchmarkcommon.GattData;

import android.os.Handler;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * This class implements the behavior of the client-side interactions
 * with the benchmark profile
 *
 * The client side exposes the following functionality to the application:
 * - start the profile
 * - begin benchmarking on connect
 * - stop the profile
 * - stop scanning after connect
 * - start benchmarking
 * - stop benchmarking
 * - set MTU
 * - set connection priority (interval)
 * - set data size
 * - get sending side timestamps
 * - get receiver side timestamps
 * - get loss rate
 * - get throughput
 *
 * Assumes that only ONE connection will be made. The majority of the
 * interaction with the profile is asynchronous--fitting with how the
 * GATT communication works and how UI interactions work
 */
public class BenchmarkServiceClient extends BenchmarkServiceBase implements CharacteristicHandler{
    private static final String TAG = BenchmarkServiceClient.class.getSimpleName();

    private GattClient mGattClient;
    private BenchmarkServiceClientCallback mCB;
    private String mServerAddress = null;


    private Handler mPrepHandler = new Handler();
    


    /**
     * Ready the profile
     * @param context - the application context
     * @param cb - callback defined by the application to handle interactions
     */
    public BenchmarkServiceClient(Context context, BenchmarkServiceClientCallback cb) {
        mGattClient = new GattClient(context, BenchmarkService.BENCHMARK_SERVICE,
                                        this, mConnUpdater);
        mCB = cb;
        super(BenchmarkService.CLIENT);
    }



    /**
     * Set up the connection with default testing values.
     */
    public void prepare() {
        this.prepare(mMtu, mConnInterval, mDataSize, mCommMethod);
    }

    /**
     * Set up the connection with the provided parameters
     *
     * @param mtu - the maximum transmission unit to be used by LL.
     * @param interval - the connection interval to be used.
     * @param dataSize - the amount of data to send in each packet.
     */
    public void prepare(int mtu, int interval, int dataSize, int commMethod){
        Log.d(TAG, "preparing...");
        mStartScanning = SystemClock.elapsedRealtimeNanos ();
        mGattClient.start(); // will scan and connect to first device
        mMtu = mtu;
        mConnInterval = interval;
        mDataSize = dataSize;
        mCommMethod = commMethod;
    }

    /**
     * Close connections and release resources
     */
    public void cleanup () {
        mGattClient.stop();
    }

    /**
     * Start benchmarking for the default duration of 10 seconds
     */
    public void beginBenchmark () {
        beginBenchmark(10000, true);
    }

    /**
     * Start benchmarking for the indicated duration. Forces some
     * synchonicity here by waiting for preparation code to be
     * complete before actually beginning the benchmark. This is
     * is transparent to application as this function returns
     * immediately. The duration can either be the duration in
     * ms or in bytes (octets) sent
     *
     * @param duration - duration (in ms or bytes) to run the
     *                 benchmark
     */
    public void beginBenchmark (long duration, boolean durationIsTime) {
        mRun = true;
        mBenchmarkDurationIsTime = durationIsTime;
        if (mBenchmarkDurationIsTime) {
            mBenchmarkDuration = duration * 1000000; //ms to ns
        } else {
            mBenchmarkDuration = duration;
        }

        Log.d(TAG, "Check if ready to start");
        mPrepHandler.post(readyToStartBenchmark);
    }

    /**
     * Check if our connection parameters are set up. If they are then
     * go start the benchmark. Otherwise, wait 10 ms and check again
     */
    private Runnable readyToStartBenchmark = new Runnable() {
        @Override
        public void run() {
           // Log.d(TAG, "mtustate: " +  mMtuState + " connIntervalState: " + mConnIntervalState + " dataSizeState: " +  mDataSizeState);
            if (mMtuState && mConnIntervalState && mDataSizeState && mCommMethodState) {
                Log.d(TAG, "Ready to start benchmark");

                //kick off benchmark
                if (mCommMethod == BenchmarkService.NOTIFY) {
                    //to start the server-side sending using notify, we send the duration
                    //of the benchmark in bytes
                    mGattClient.handleCharacteristic(
                            new GattData(mServerAddress,
                            BenchmarkService.TEST_CHAR,
                            ByteBuffer.allocate(Long.BYTES).putLong(mBenchmarkDuration).array()));
                } else {
                    mCB.onBenchmarkStart();
                    mBenchmarkHandler.post(this.goTest);
                }

                mBenchmarkStart = SystemClock.elapsedRealtimeNanos ();
            } else {
                //check back later
                if (mRun) {
                    mPrepHandler.postDelayed(this, 10);
                }
            }
        }
    };

    /**
     * Send the data using the GattClient
     * @param data - the data to send
     * @return a response object (null for our use case)
     */
    @Override
    protected GattData handleTestCharSend (GattData data) {
        super(data);
        mGattClient.handleCharacteristic(data);
        return null;
    }


    /**
     * End the benchmark now. Benchmark should not be considered ended until
     * callback is called
     */
    public void endBenchmark () {
        mRun = false;
    }

    /**
     * Request the raw timestamps from the benchmark. Calling this during
     * the test will affect the results.
     *
     * TODO: implement as advanced functionality -- need netstring parser
     */
    public void requestRawTimestamps () {

    }

    /**
     * Request the throughput fom the benchmark. Calling this during the
     * test will affect the results.
     */
    public void requestThroughput () {
        Log.d(TAG, "Requesting throughput");
        long bps = 0;
        int lastIndex = mServerLatencyIndex - 1 ;
        if (0 <= lastIndex) {
            bps = (mBenchmarkBytesSent * 8 * 1000000000) / mServerLatency[lastIndex];
        }
        mCB.onThroughputAvailable(bps);
    }

    /**
     * Request the latency measurements from the server
     *
     */
    public void requestLatencyMeasurements () {
        mGattClient.handleCharacteristic(new GattData(mServerAddress,
                BenchmarkService.LATENCY_CHAR,
                null));
    }

    /**
     * Request the server's ID (useful for data logging)
     */
    public void requestServerID () {
        mGattClient.handleCharacteristic(new GattData(mServerAddress,
                BenchmarkService.ID_CHAR,
                null));
    }

    /**
     * Match the incoming message to the appropriate callback
     *
     *
     * @param data - the gatt data from the gatt layer
     */
    @Override
    public GattData handleCharacteristic (GattData data) {
        if (BenchmarkService.LATENCY_CHAR.equals(data.mCharID)) {
            long measurement = getLong(data);
            //Log.d(TAG, "measurement: " + measurement);

            if (-1 != measurement) {
                recordTime(RECEIVER_LATENCY, measurement);
            } else {
                //pass copies of the arrays up through the callback
                //we don't want to pass our array references!

                long [] opLatency = new long [mLatencyIndex[OP_LATENCY]];
                long [] receiverLatency = new long [mLatencyIndex[RECEIVER_LATENCY]];
                System.arraycopy(mLatency[OP_LATENCY], 0, opLatency, 0, opLatency.length);
                System.arraycopy(mLatency[RECEIVER_LATENCY], 0, receiverLatency, 0, receiverLatency.length);
                mCB.onLatencyMeasurementsAvailable(opLatency, receiverLatency);
            }
        }else if(BenchmarkService.TEST_CHAR.equals(data.mCharID)
                && BenchmarkService.NOTIFY != mCommMethod){
            //Gatt layer needs to time the operations, so it passes up
            //the operation latency through the test characteristic
            //This makes it easy for the GATT layer to time different
            //things (according to the comm method for example) and let
            //the profile client manage the times
            recordTime(OP_LATENCY, this.getLong(data));

            data.mBuffer = null;
        } else if(BenchmarkService.TEST_CHAR.equals(data.mCharID)
                && BenchmarkService.NOTIFY == mCommMethod){
            handleTestCharReceive(data);

        }else if(BenchmarkService.ID_CHAR.equals(data.mCharID)){
            mCB.onServerIDAvailable(new String(data.mBuffer));
        } else{ //we can't handle this so return null
            data = null;
        }

        return data;
    }

    /**
     * Request MTU change from GATT layer. Return immediately. Completion
     * of operation communicated through callback.
     *
     * @param mtu - the maximum transmission unit to use.
     */
    private void setMtu (int mtu) {
        mMtuState = false; //unsure if okay right now
        mGattClient.mtuUpdate(mServerAddress, mtu);

    }

    /**
     * Request a connection interval change from the GATT layer. Return
     * immediately. Completion of operation communicated through callback
     *
     * @param connInterval - the connection interval (in ms) to use.
     */
    private void setConnInterval (int connInterval) {
        mConnIntervalState = false; //unsure if okay right now
        mGattClient.connIntervalUpdate(mServerAddress, connInterval);
    }



    /**
     * Set the size of the (random) data to be used for each interaction.
     *
     * @param dataSize - the size of data to use. Values can range from
     *                 1 to MTU.
     */
    private void setDataSize (int dataSize) {
        if (0 < dataSize && dataSize <= mMtu) {
            mDataSizeState = true;
        } else {
            mDataSizeState = false;
        }
    }

    /**
     * Set the method of communication to be used by the gatt layer
     * for the benchmark
     *
     * @param commMethod - the method defined in BenchmarkService
     */
    private void setCommMethod (int commMethod){
        mCommMethod = commMethod;
        mGattClient.commMethodUpdate(mServerAddress, commMethod);
    }

    /**
     * Connection updater callback that is passed to Gatt Client.
     */
    private ConnectionUpdater mConnUpdater = new ConnectionUpdater (){
        @Override
        public void connectionUpdate (String address, int state){
            if (1 == state){
                Log.d(TAG, "Connected");
                mLatencyStartup = SystemClock.elapsedRealtimeNanos () - mStartScanning;
                mCB.onStartupLatencyAvailable (mLatencyStartup);

                mServerAddress = address;

                setMtu (mMtu);
            }
        }

        @Override
        public void commMethodUpdate(String address, int method){
            if (method == mCommMethod) {
                //success
                Log.d(TAG, "comm method updated!");
                mCommMethodState = true;
            }
            else{
                //failure
                mCB.onBenchmarkError(BenchmarkServiceClientCallback.SET_COMM_METHOD_ERROR
                        , "set comm method to " + mCommMethod + ", but there was an error");
            }
        }

        @Override
        public void mtuUpdate(String address, int mtu){
            if (0 == mtu) {
                mCB.onBenchmarkError(BenchmarkServiceClientCallback.SET_MTU_ERROR
                        , "set MTU to " + mMtu + ", but there was an error");
            }
            else {
                if (mtu == mMtu) {
                    Log.d(TAG, "mtu updated!");
                } else {
                    Log.i(TAG, "MTU negotiated to: " + mtu);
                    mMtu = mtu;
                }

                mMtuState = true;
            }

            setConnInterval(mConnInterval);
            setDataSize(mDataSize);

        }

        @Override
        public void connIntervalUpdate (String address, int interval){
            if (interval != mConnInterval) {
                mCB.onBenchmarkError(BenchmarkServiceClientCallback.SET_CONN_INTERVAL_ERROR
                        , "set connInterval to " + mConnInterval
                                + ", but there was an error");
            }
            else {
                Log.d(TAG, "Connection interval updated");
                mConnIntervalState = true;
            }

            setCommMethod(mCommMethod);
        }
    };


}
