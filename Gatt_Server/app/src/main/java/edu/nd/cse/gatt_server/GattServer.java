package edu.nd.cse.gatt_server;

/* BLE imports */
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;


/* android imports */
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/* misc imports */
import java.io.File;

/**
 * Class that implements the gatt server that implements all of the callbacks
 * to communicate with a Gatt Client
 */
public class GattServer {
    private static final String TAG = GattServerActivity.class.getSimpleName();

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    public boolean mHasBTSupport = true;

    private byte [] mBuffer = null;

    private Context mAppContext = null;
    private BenchmarkProfile mBenchmarkProfile = null;


    /* Interact with UI */
    private UiUpdate mUpdater = null;


    /**
     * Constructor - set up the benchmark profile, open the time stamp file,
     * get a {@link BlutoothManager}, and check if we have the necessary
     * Bluetooth support
     * @param context - application context
     * @param updater - callback function used to update the UI
     */
    public GattServer(Context context, UiUpdate updater) {
        mAppContext = context;
        mBenchmarkProfile = new BenchmarkProfile();
        File path = mAppContext.getExternalFilesDir(null);
        mBenchmarkProfile.setTimeStampFile(new File(path, "gatt_cap.txt"));

        mBuffer = new byte [512]; //max characteristic size

        mUpdater = updater;

        mBluetoothManager = (BluetoothManager) mAppContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            mHasBTSupport = false;
        }
    }

    /**
     * Set up a broadcast listener for adapter state, enable the Bluetooth
     * adapter if it is not already or start advertising and the GATT server.
     */
    public void start() {
        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mAppContext.registerReceiver(mBluetoothReceiver, filter);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            startAdvertising();
            startServer();
        }
    }

    /**
     * Stop the GATT server and stop advertising. Unregister broadcast
     * receiver
     */
    public void stop(){
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
        }

        mAppContext.unregisterReceiver(mBluetoothReceiver);
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

        if (!mAppContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Benchmark Service
     */
    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BenchmarkProfile.BENCHMARK_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);

        mUpdater.updateText("Advertising...");
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Benchmark Profile.
     */
    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(mAppContext, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        mBluetoothGattServer.addService(BenchmarkProfile.createBenchmarkService());

        mUpdater.updateText("Gatt Server ready");
    }

    /**
     * Shut down the GATT server.
     */
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        /**
         * As a server that only reacts to requests, we do not need to do anything
         * upon connection
         * @param device
         * @param status
         * @param newState
         */
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
            }
        }

        /**
         * Receive data from GATT client, start timing or record time diff, and pass
         * received data up to UI
         * @param device
         * @param requestId
         * @param characteristic
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param value
         */
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);

            //We don't need to do anything but acknowledge since we aren't setting any chars
            Log.i(TAG, "Received: " + String.valueOf(value));

            if (responseNeeded) {
                //Presumably the client's onCharacteristicWrite only gets called on receipt of
                //an acknowledgement
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            } else {
                if (!mBenchmarkProfile.timerStarted()) {
                    mBenchmarkProfile.startTiming();
                } else {
                    mBenchmarkProfile.recordTimeDiff();
                }
            }

            mUpdater.updateText("> " + String.valueOf(value));

        }

    };

}
