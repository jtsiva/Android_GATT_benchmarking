package edu.nd.cse.gatt_server;

import edu.nd.cse.benchmarkcommon.BenchmarkService;
import edu.nd.cse.benchmarkcommon.BenchmarkServiceBase;
import edu.nd.cse.benchmarkcommon.Key;
import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.GattData;
import edu.nd.cse.benchmarkcommon.ConnectionUpdater;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import android.util.Log;
import android.content.Context;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;
import java.nio.ByteBuffer;


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
* */
public class BenchmarkServiceServer extends BenchmarkServiceBase
                                    implements CharacteristicHandler {
    private static final String TAG = BenchmarkServiceServer.class.getSimpleName();

    private final int MAX_CLIENTS = 6; //seems high enough....

    private GattServer mGattServer;
    private BenchmarkServiceServerCallback mCB;

    private Map<String, Integer> mSentMeasurements = new HashMap<String, Integer>();


    /**
     * Initialize the time diffs array and gatt server
     *
     * @param context - application context
     * @param cb - callback for communicating with upper layers
     */
    public BenchmarkServiceServer(Context context,
                                  BenchmarkServiceServerCallback cb){
        super(BenchmarkService.SERVER);

        mCB = cb;

        mGattServer = new GattServer (context, createBenchmarkService());
        mGattServer.setCharacteristicHandler(this);
        mGattServer.setConnectionUpdateCallback(this);

    }

    @Override
    public void commMethodUpdate(String address, int commMethod) {mCommMethod = commMethod; }

    @Override
    public void mtuUpdate(String address,int mtu) {
        mMtu = mtu;
    }

    @Override
    public void connIntervalUpdate (String address,int interval){
        mConnInterval = interval;
    }

    @Override
    public void connectionUpdate (String address, int state){
        super.connectionUpdate(address, state);

        if (0 == state) {
            if (mConnections.isEmpty()){
                mCB.onBenchmarkComplete();
            }

        }
    }


    /**
     * Start the gatt server
     */
    public void start(boolean stopAdvertising) {
        mGattServer.start(stopAdvertising);
    }

    /**
     * Start the gatt server and stop advertising upon connection
     */
    public void start () {
        mGattServer.start(true);
    }

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * {@link BluetoothGattServer}
     */
    private BluetoothGattService createBenchmarkService() {
        BluetoothGattService service = new BluetoothGattService(BENCHMARK_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic testChar = new BluetoothGattCharacteristic(BenchmarkService.TEST_CHAR,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattDescriptor testDesc = new BluetoothGattDescriptor(BenchmarkService.TEST_DESC,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        testChar.addDescriptor(testDesc);

        BluetoothGattCharacteristic rawDataChar = new BluetoothGattCharacteristic(BenchmarkService.RAW_DATA_CHAR,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic latencyChar = new BluetoothGattCharacteristic(BenchmarkService.LATENCY_CHAR,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic idChar = new BluetoothGattCharacteristic(BenchmarkService.ID_CHAR,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic (testChar);
        service.addCharacteristic (rawDataChar);
        service.addCharacteristic (latencyChar);
        service.addCharacteristic (idChar);

        return service;
    }

    /**
     * Callback to be called by GATT layer to handle a characteristic. All the
     * parsing and interpretation of the data from the GATT layer occurs here.
     *
     * @param data - the data from the GATT layer if any (can be null)
     * @return a response. null if there was a problem with the action on the
     * characteristic.
     */
    @Override
    public GattData handleCharacteristic (GattData data) {
        GattData response = null;
        if (BenchmarkService.TEST_CHAR.equals(data.mCharID))
        {
            response = handleTestCharacteristic(data);
        }
        else if (BenchmarkService.RAW_DATA_CHAR.equals(data.mCharID)){
            response = handleRawDataRequest(data);
        }
        else if (BenchmarkService.LATENCY_CHAR.equals(data.mCharID)){
            response = handleLatencyRequest(data);
        }
        else if (BenchmarkService.ID_CHAR.equals(data.mCharID)){
            response = handleIDRequest();
        }

        return response;
    }

    /**
     * Since the data will be junk, just count the number of bytes received,
     * increment the number of packets, and record the time in the case of
     * writes. If we are using notifications, then we will be sent the byte
     * duration to use.
     *
     * @return data with sender address, char uuid, and null buffer
     */
    private GattData handleTestCharacteristic (GattData data){
        GattData response = null;

        //if we are using a notify then we are the sender
        if (mCommMethod == BenchmarkService.NOTIFY) {

            //the first thing we'll receive on this char is the benchmark duration
            if (0 == mBenchmarkDuration) {
                mBenchmarkDuration = getLong (data);

                mBenchmarkHandler.post(this);
            } else {
                //later calls to this function are operation latency measurements
                recordTime(OP_LATENCY, data.mAddress, this.getLong(data));

                data.mBuffer = null;
                response = data;
            }

        } else {
            //otherwise we are the receiver
            response = this.handleTestCharReceive(data);
        }

        return response;
    }

    /**
     * Streams all of the raw timing data back to caller in netstring format:
     * [num bytes].[bytes]
     * This means that it is the caller's responsibility to request more reads
     * on this characteristic if all bytes have not been received. Here we need
     * to keep track of how many bytes have been sent and how many still need to
     * be sent.
     *
     * TODO: implement
     *
     * @param data - information associated with request
     *
     * @return barebones response with only buffer set
     */
    private GattData handleRawDataRequest (GattData data) {
        GattData response = null;

        //get a new chunk of netstring to send and put in response.mBuffer

        return response;
    }

    /**
     * Return the latency timestamps, 1 read at a time until all
     * timestamps have been sent. When no more data is available, send
     * -1
     *
     * @param data - information associated with request (like address)
     * @return barebones response with only buffer set
     */
    private GattData handleLatencyRequest (GattData data) {
        long returnVal = -1;
        int index = 0;

        if (mCommMethod == BenchmarkService.NOTIFY) {
            index = mLatencyIndex.get(new Key(OP_LATENCY, data.mAddress));
        } else {
            index = mLatencyIndex.get(new Key(RECEIVER_LATENCY, data.mAddress));
        }

        if (null == mSentMeasurements.get(data.mAddress)) {
            mSentMeasurements.put(data.mAddress, 0);
        }

        if (mSentMeasurements.get(data.mAddress) < index) {
            returnVal = mLatency.get(new Key(OP_LATENCY, data.mAddress)).get(mSentMeasurements.get(data.mAddress));
            mSentMeasurements.put(data.mAddress, mSentMeasurements.get(data.mAddress) + 1);
        } else {
            mCB.onBenchmarkComplete();
        }

        return new GattData (null,
                null,
                ByteBuffer.allocate(Long.BYTES).putLong(returnVal).array());
    }

    /**
     * Get the display ID for this device and return
     * @return Build.DISPLAY
     */
    private GattData handleIDRequest () {
        return new GattData (null, null, Build.DISPLAY.getBytes());
    }


    /**
     * Stop the gatt server. This is a clean up function that is intended to
     * be called before the application closes.
     */
    public void stop() {
        mGattServer.stop();
    }

}
