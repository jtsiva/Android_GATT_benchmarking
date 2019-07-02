package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.BenchmarkProfile;
import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.ConnectionUpdater;
import edu.nd.cse.benchmarkcommon.GattData;

import android.content.Context;

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
 * - get raw timestamps
 * - get throughput
 * - get loss rate
 *
 * Assumes that only ONE connection will be made. The majority of the
 * interaction with the profile is asynchronous--fitting with how the
 * GATT communication works and how UI interactions work
 */
public class BenchmarkProfileClient extends BenchmarkProfile implements CharacteristicHandler{
    private static final String TAG = BenchmarkProfileClient.class.getSimpleName();

    private GattClient mGattClient;
    private BenchmarkProfileClientCallback mCB;
    private String mServerAddress = null;

    private long mBenchmarkStart = null;
    private long mBenchmarkDuration = null;


    //performance parameters
    private int mMtu = 20;
    private boolean mMtuState;
    private int mConnInterval = 40; //balanced
    private boolean mConnIntervalState;
    private int mDataSize = 20;
    private boolean mDataSizeState;


    /**
     * Ready the profile
     * @param context - the application context
     * @param cb - callback defined by the application to handle interactions
     */
    public BenchmarkProfileClient (Context context, BenchmarkProfileClientCallback cb) {
        mGattClient = new GattClient(context, BenchmarkProfile.BENCHMARK_SERVICE,
                                        this, mConnUpdater);
        mCB = cb;
    }



    /**
     * Set up the connection with default testing values.
     */
    public void prepare() {
        return this.prepare(mMtu, mConnInterval, mDataSize);
    }

    /**
     * Set up the connection with the provided parameters
     *
     * @param mtu - the maximum transmission unit to be used by LL.
     * @param interval - the connection interval to be used.
     * @param dataSize - the amount of data to send in each packet.
     */
    public void prepare(int mtu, int interval, int dataSize){
        mGattClient.start(); // will scan and connect to first device
        mMtu = mtu;
        mConnInterval = interval;
        mDataSize = dataSize;
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
        beginBenchmark(10000);
    }

    /**
     * Start benchmarking for the indicated duration. Forces some
     * synchonicity here by waiting for preparation code to be
     * complete before actually beginning the benchmark. This is
     * is transparent to application as this function returns
     * immediately.
     *
     * @param durationMS - duration (in ms) to run the benchmark
     */
    public void beginBenchmark (long durationMS) {

    }

    /**
     * End the benchmark now. Benchmark should not be considered ended until
     * callback is called
     */
    public void endBenchmark () {

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
     *
     * TODO: implement as minimum functionality
     */
    public void requestThroughput () {
        //mGattClient.handleCharacteristic();
    }

    /**
     * Request the loss rate from the benchmark. Calling this during the
     * test will affect the results.
     *
     * TODO: implement as basic functionality
     */
    public void requestLossRate () {

    }

    /**
     * Match the incoming message to the appropriate callback
     *
     * TODO: implement handling for raw data
     *
     * @param data - the gatt data from the gatt layer
     */
    @Override
    public GattData handleCharacteristic (GattData data) {
        if (BenchmarkProfile.THROUGHPUT_CHAR.equals(data.mCharID)) {
            mCB.onThroughputAvailable(Float.parseFloat(new String(data.mBuffer)));
            data.mBuffer = null;
        }
        else if(BenchmarkProfile.LOSS_RATE_CHAR.equals(data.mCharID)) {
            mCB.onLossRateAvailable(Float.parseFloat(new String(data.mBuffer)));
            data.mBuffer = null;
        }
        else { //we can't handle this so return null
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
        mGattClient.connIntervalUpdate(mServerAddress, connInterval)
    }



    /**
     * Set the size of the (random) data to be used for each interaction.
     *
     * @param dataSize - the size of data to use. Values can range from
     *                 1 to MTU.
     */
    private void setDataSize (int dataSize) {
        if (0 < dataSize && dataSize <= mMtu && mMtuState) {
            mDataSizeState = true;
        }

        mDataSizeState = false;
    }

    /**
     * Connection updater callback that is passed to Gatt Client.
     */
    private ConnectionUpdater mConnUpdater = new ConnectionUpdater (){
        @Override
        public void connectionUpdate (String address, int state){
            if (1 == state){
                mServerAddress = address;

                setMtu (mMtu);
                setConnInterval(mConnInterval);
                setDataSize(mDataSize);
            }
        }

        @Override
        public void mtuUpdate(String address, int mtu){
            if (mtu != mMtu) {
                mCB.BenchmarkError(BenchmarkProfileClientCallback.SET_MTU_ERROR
                        , "set MTU to " + mMtu + ", but there was an error");
            }
            else {
                mMtuState = true;
            }
        }

        @Override
        public void connIntervalUpdate (String address, int interval){
            if (interval != mConnInterval) {
                mCB.BenchmarkError(BenchmarkProfileClientCallback.SET_CONN_INTERVAL_ERROR
                        , "set connInterval to " + mConnInterval
                                + ", but there was an error");
            }
            else {
                mConnIntervalState = true;
            }
        }
    };


}
