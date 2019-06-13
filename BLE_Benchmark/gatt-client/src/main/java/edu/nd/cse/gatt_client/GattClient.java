package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.BenchmarkProfile;
import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.GattData;
import edu.nd.cse.benchmarkcommon.UiUpdate;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class that manages the client side of the GATT I/O layer. Responsible
 * for scanning, setting up and managing connections, queuing write (or read)
 * operations, and interfacing with profile layer
 */
public class GattClient extends BluetoothGattCallback
                        implements CharacteristicHandler{
    private static final String TAG = BenchmarkClient.class.getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Map<String, BluetoothGatt> mConnectedDevices = new HashMap<String, BluetoothGatt>();
    private final Queue<GattData> mWriteQueue = new ConcurrentLinkedQueue<GattData>();
    private boolean mIsIdle = true;
    private UiUpdate mUiUpdate = null;
    private CharacteristicHandler mCharHandler = null;

    private Context mContext;
    private UUID mTargetService;

    /**
     *
     * @param context
     * @param targetServiceID
     */
    public GattClient (Context context, UUID targetService) {
        this.mContext = context;
        this.mTargetService = targetService;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }


    /**
     *
     * @param charHandler
     */
    public void setHandler (CharacteristicHandler charHandler) {
        mCharHandler = charHandler;
    }

    /**
     *
     * @param data
     */
    @Override
    public GattData handleCharacteristic(GattData data) {
        if (null != data) {
            mWriteQueue.add(data);
            if (mIsIdle) {
                mIsIdle = false;
                GattData readyData = mWriteQueue.poll();
                performOperation(readyData);
            }
        }

        return null;
    }

    /**
     *
     */
    public void start () {

    }

    /**
     *
     */
    public void stop () {

    }

    /**
     *
     */
    private void startScan(){
        //scan filters
        ScanFilter ResultsFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UART_UUID))
                .build();

        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(ResultsFilter);
        Log.i("Central","BLE SCAN STARTED");

        //scan settings
        ScanSettings settings = new ScanSettings.Builder()
                //.setReportDelay(0) //0: no delay; >0: queue up
                .setScanMode(this.scanSetting) //LOW_POWER, BALANCED, LOW_LATENCY
                .build();

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null){
            Log.e("Central", "no BLE scanner assigned!!!");
            return;
        }
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
    }

    /**
     *
     */
    private void stopScanning () {
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.stopScan(mScanCallback);
        Log.i("Central","LE scan stopped");
    }

    /**
     *
     */
    private ScanCallback mScanCallback = new ScanCallback () {
        @Override
        public void onScanResult ( int callbackType, ScanResult result){
            //connect
        }

        @Override
        public void onScanFailed ( int errorCode){
            Log.e(TAG, "LE Scan Failed: " + errorCode);
        }
    };

    /**
     *
     * @param data
     */
    private void performOperation (GattData data) {
        //perform operation on characteristic
    }

    /**
     *
     */
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mConnectedDevices.put(gatt.getDevice().getAddress(), gatt);

                //gatt.requestMtu(this.mtu);
            }
            else {
                // Error connecting to device.
                //connectFailure();
            }
        }
        else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            // Disconnected, notify callbacks of disconnection.
            mConnectedDevices.remove(gatt.getDevice().getAddress());

            //notifyOnDisconnected(this);
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        // Notify connection failure if service discovery failed.
        if (status == BluetoothGatt.GATT_FAILURE) {
            //connectFailure();
            Log.e(TAG, "onServicesDiscovered gatt failure");
            return;
        } else {
            Log.e(TAG, "onServicesDiscovered gatt success");
        }

        BluetoothDevice device = gatt.getDevice();
        String address = device.getAddress();
        final BluetoothGatt bluetoothGatt = mConnectedDevices.get(address);

        //reference to each UART characteristic
        if (null == bluetoothGatt.getService(BenchmarkProfile.BENCHMARK_SERVICE)) {
            //connectFailure();
            Log.e(TAG, "onServicesDiscovered gatt failure");
            return;
        }
    }

    /**
     * We don't really need to do anything after a read is completed. We just
     * need to make sure we pull something from queue (if we can) to initiate
     * another write
     *
     * @param gatt - the gatt instance for the connected device
     * @param characteristic - the characteristic to which we wrote
     * @param status - the status of the write operation
     */
    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG,"Characteristic write successful");


        } else {
            Log.d(TAG,"Characteristic write FAILED");
        }

        GattData data = mWriteQueue.poll();

        if (null == data) { //empty!
            mIsIdle = true;
        } else {
            mCharHandler.handleCharacteristic(data);
        }

    }

    /**
     * Handle the returned value from the characteristic read by passing it up
     * to the profile
     *
     * @param gatt - the gatt instance for the connected device
     * @param characteristic - the characteristic upon which the read was
     *                       carried out
     * @param status - whether the read was successful or not
     */
    @Override
    public void onCharacteristicRead (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        BluetoothDevice device = gatt.getDevice();
        String address = device.getAddress();
        final BluetoothGatt bluetoothGatt = mConnectedDevices.get(address);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            mCharHandler.handleCharacteristic(new GattData(address,
                                                            characteristic.getUUID(),
                                                            characteristic.getValue()));
        }
        else {
            Log.w("DIS", "Failed reading characteristic " + characteristic.getUuid().toString());
        }
    }
}
