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

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Map<String, BluetoothGatt> mConnectedDevices = new HashMap<String, BluetoothGatt>();
    private final Queue<GattData> mWriteQueue = new ConcurrentLinkedQueue<GattData>();
    private boolean mIsIdle = true;
    private UiUpdate mUiUpdate = null;
    private CharacteristicHandler mCharHandler = null;

    private boolean mHasBTSupport;

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
        this.mContext = context;
        this.mTargetService = targetService;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
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
     * The handler *from* the profile. Adds the data to an outbound queue.
     *
     * @param data - data to be sent
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
     * Start scanning for the target service
     *
     * @param stopScanningAfterConnect - indicate whether to continue scanning
     *                                 after making a connection
     *
     * NOT IMPLEMENTED
     */
    public void start (boolean stopScanningAfterConnect) {

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothReceiver, filter);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
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
        Log.i("Central","BLE SCAN STARTED");

        //scan settings
        ScanSettings settings = new ScanSettings.Builder()
                //.setReportDelay(0) //0: no delay; >0: queue up
                .setScanMode(LOW_LATENCY) //LOW_POWER, BALANCED, LOW_LATENCY
                .build();

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null){
            Log.e("Central", "no BLE scanner assigned!!!");
            return;
        }
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
    }

    /**
     * Disconnect and stop scanning
     *
     * NOT IMPLEMENTED
     */
    public void stop () {
        //disconnect

        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            Log.i("TAG", "LE scan stopped");
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
                    stopScanning();
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
            //connect
        }

        @Override
        public void onScanFailed ( int errorCode){
            Log.e(TAG, "LE Scan Failed: " + errorCode);
        }
    };

    /**
     * Perform the requested operation.
     *
     * @param data
     */
    private void performOperation (GattData data) {
        //perform operation on characteristic
    }

    /**
     *
     * @param gatt
     * @param status
     * @param newState
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

    /**
     *
     * @param gatt
     * @param status
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
