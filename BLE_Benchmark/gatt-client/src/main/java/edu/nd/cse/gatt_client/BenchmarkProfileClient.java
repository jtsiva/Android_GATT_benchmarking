package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.BenchmarkProfileClientCallback;

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
public class BenchmarkProfileClient {
    private static final String TAG = BenchmarkProfileServer.class.getSimpleName();

    /**
     * Ready the profile
     * @param context - the application context
     * @param cb - callback defined by the application to handle interactions
     */
    public BenchmarkProfileClient (Context context, BenchmarkProfileClientCallback cb) {

    }

    /**
     * Set up the connection with default testing values
     */
    public void prepare() {

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
