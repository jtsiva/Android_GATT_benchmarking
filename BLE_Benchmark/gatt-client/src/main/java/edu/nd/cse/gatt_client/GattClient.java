package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.GattData;
import edu.nd.cse.benchmarkcommon.UiUpdate;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;
import android.content.Context;

import java.util.ArrayList;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class that manages the client side of the GATT I/O layer. Responsible
 * for scanning, setting up and managing connections, queuing write (or read)
 * operations, and interfacing with profile layer
 */
public class GattClient extends BluetoothGattCallback
                        implements CharacteristicHandler{
    private static final String TAG = BenchmarkClient.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Map<String, BluetoothGatt> mConnectedDevices = new HashMap<String, BluetoothGatt>();
    private final Queue<GattData> mOperationQueue = new ConcurrentLinkedQueue<GattData>();
    private boolean mIsIdle = true;
    private UiUpdate mUiUpdate = null;
    private CharacteristicHandler mCharHandler = null;

    private boolean mHasBTSupport;
    private boolean mStopScanningOnConnect;
    private boolean mScanStarted = false;

    private Context mContext;
    private UUID mTargetService;

    /**
     * Initialize the context, bluetooth adapter, and service UUID
     * that we want to connect to
     *
     * @param context - the application context
     * @param targetServiceID - UUID of the service to scan for
     */
    public GattClient (Context context, UUID targetService) {
        mContext = context;
        mTargetService = targetService;
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(mBluetoothAdapter)) {
            mHasBTSupport = false;
        }

    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }


    /**
     * Set the characteristic handler up the stack
     *
     * @param charHandler - the handler
     */
    public void setHandler (CharacteristicHandler charHandler) {
        mCharHandler = charHandler;
    }

    /**
     * The handler called by the profile. Adds the data to the operation queue.
     *
     * @param data - data to be sent
     */
    @Override
    public GattData handleCharacteristic(GattData data) {
        if (null != data) {
            mOperationQueue.add(data);
            if (mIsIdle) {
                mIsIdle = false;
                GattData readyData = mOperationQueue.poll();
                performOperation(readyData);
            }
        }

        return null;
    }

    /**
     * Start scanning for the target service
     *
     * @param stopScanningAfterConnect - indicate whether to continue scanning
     *                                 after making a connection
     *
     */
    public void start (boolean stopScanningOnConnect) {
        mStopScanningOnConnect = stopScanningOnConnect;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothReceiver, filter);

        if (!mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            mBluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            startScanning();
        }

    }

    /**
     * Start scanning for target service. Stops scanning after a connection
     * is made as default.
     */
    public void start (){
        this.start(true);
    }

    /**
     * Set up the scan filter and the scan parameters and then start
     * scanning
     */
    private void startScanning() {
        //scan filters
        ScanFilter ResultsFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(mTargetService))
                .build();

        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(ResultsFilter);
        Log.i(TAG,"BLE scan started");

        //scan settings
        ScanSettings settings = new ScanSettings.Builder()
                //.setReportDelay(0) //0: no delay; >0: queue up
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //LOW_POWER, BALANCED, LOW_LATENCY
                .build();

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null){
            Log.e("Central", "no BLE scanner assigned!!!");
            return;
        }
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mScanStarted = true;
    }

    /**
     * Disconnect and stop scanning
     *
     */
    public void stop () {
        for (Map.Entry<String, BluetoothGatt> entry : mConnectedDevices.entrySet()) {
            entry.getValue().disconnect();
        }

        stopScanning();
    }

    /**
     * Stop scanning
     */
    private void stopScanning () {
        if (mScanStarted) {
            if (mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
                mBluetoothLeScanner.stopScan(mScanCallback);
                Log.i("TAG", "LE scan stopped");
            }

            mScanStarted = false;
        }
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * scanning
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startScanning();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stop();
                    break;
                default:
                    // Do nothing
            }
        }
    };


    /**
     * Set up the callbacks for the scanner. When a result is received, connect
     * On failure, log failure code
     */
    private ScanCallback mScanCallback = new ScanCallback () {
        @Override
        public void onScanResult ( int callbackType, ScanResult result){

            if (!(mStopScanningOnConnect && mConnectedDevices.size() > 0)) {
                if (!mConnectedDevices.containsKey(result.getDevice().getAddress())) {
                    connect(result.getDevice());
                }
            }

        }

        @Override
        public void onScanFailed ( int errorCode){
            Log.e(TAG, "LE Scan Failed: " + errorCode);
        }
    };

    /**
     * Initiate connection with device
     * @param device - gatt device to which a connection will be started
     */
    private void connect(BluetoothDevice device) {
        device.connectGatt(mContext, false, this, BluetoothDevice.TRANSPORT_LE);
    }

    /**
     * Perform the requested operation.
     *
     * @param data - collection of information needed to perform operation
     */
    private void performOperation (GattData data) {
        BluetoothGattService service = mConnectedDevices.get(data.mAddress).getService(mTargetService);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(data.mCharID);

        if (null == data.mBuffer) { //read
            mConnectedDevices.get(data.mAddress).readCharacteristic(characteristic);
        }
        else { //write
            characteristic.setValue(data.mBuffer);
            mConnectedDevices.get(data.mAddress).writeCharacteristic(characteristic);
        }
    }

    /**
     * Add gatt object to hashmap for device that connects or remove if
     * device disconnects
     *
     * @param gatt - the gatt object to handle
     * @param status - success or fail
     * @param newState - connect or disconnect
     */
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mConnectedDevices.put(gatt.getDevice().getAddress(), gatt);

                if (mStopScanningOnConnect) {
                    stopScanning();
                }
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

    /**
     * After service discovery, check that target service is available
     *
     * @param gatt - object to handle
     * @param status - success or fail of service discovery
     */
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

        final BluetoothGatt bluetoothGatt = mConnectedDevices.get(gatt.getDevice().getAddress());

        //reference to each UART characteristic
        if (null == bluetoothGatt.getService(mTargetService)) {
            //connectFailure();
            Log.e(TAG, "onServicesDiscovered gatt failure");
            return;
        }
    }

    /**
     * Pull something from queue (if we can) to initiate
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

        GattData data = mOperationQueue.poll();

        if (null == data) { //empty!
            mIsIdle = true;
        } else {
            performOperation(data);
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

        if (status == BluetoothGatt.GATT_SUCCESS) {
            mCharHandler.handleCharacteristic(new GattData(gatt.getDevice().getAddress(),
                                                            characteristic.getUuid(),
                                                            characteristic.getValue()));
        }
        else {
            Log.w(TAG, "Failed reading characteristic " + characteristic.getUuid().toString());
        }
    }
}
