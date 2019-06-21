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
    private String mClientAddress;
    private ConnectionUpdater mConnUpdater = new ConnectionUpdater (){
            @Override
            public void connectionUpdate (String address, int state){
                if (1 == state){
                    mClientAddress = address;
                }
            }
    };

    private int mMtu = 20;
    private int mConnInterval;
    private int mDataSize;

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
     * Set up the connection with default testing values
     */
    public void prepare() {
        mGattClient.start();
    }

    /**
     * Set up the connection with the provided parameters
     *
     * @param mtu - the maximum transmission unit to be used by LL.
     * @param interval - the connection interval to be used.
     * @param dataSize - the amount of data to send in each packet.
     * @return true if the values are valid, false otherwise
     */
    public boolean prepare(int mtu, int interval, int dataSize){
         if(!setMtu (mtu) && setConnInterval(interval) && setDataSize(dataSize)){
             return false;
         }
         else {
             //do things -- maybe
         }

         return true;
    }

    /**
     * Close connections and release resources
     */
    public void cleanup () {

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
     */
    public void requestRawTimestamps () {

    }

    /**
     * Request the throughput fom the benchmark. Calling this during the
     * test will affect the results.
     */
    public void requestThroughput () {

    }

    /**
     * Request the loss rate from the benchmark. Calling this during the
     * test will affect the results.
     */
    public void requestLossRate () {

    }

    /**
     * Match the incoming message to the appropriate callback
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
     * TODO: include valid span
     * TODO: implement
     *
     * @param mtu - the maximum transmission unit to use. Values can span
     * @return true if mtu is a valid value, false otherwise
     */
    private boolean setMtu (int mtu) {
        return false;
    }

    /**
     * Request a connection interval change from the GATT layer. Return
     * immediately. Completion of operation communicated through callback
     *
     * TODO: specify values
     *
     * @param connInterval - the connection interval to use. Possible values
     *                     are
     * @return true if connInterval is a valid value, false otherwise
     */
    private boolean setConnInterval (int connInterval) {
        return false;
    }

    /**
     * 
     * @param connInterval
     * @return
     */
    private int intervalToPriority (int connInterval) {
        int priority = 0;
        switch(connInterval){

        }
    }

    /**
     * Set the size of the (random) data to be used for each interaction.
     * Returns immediately.
     *
     * @param dataSize - the size of data to use. Values can range from
     *                 1 to MTU.
     * @return true if data size is valid, false otherwise
     */
    private boolean setDataSize (int dataSize) {
        return false;
    }


}
