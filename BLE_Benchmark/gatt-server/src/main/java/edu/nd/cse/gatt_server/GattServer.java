package edu.nd.cse.gatt_server;

import edu.nd.cse.benchmarkcommon.CharacteristicHandler;
import edu.nd.cse.benchmarkcommon.GattData;
import edu.nd.cse.benchmarkcommon.ConnectionUpdater;

/* BLE imports */
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
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
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/* misc imports */
import java.io.File;
import java.util.Arrays;
import java.util.Random;

/**
 * Class that implements the gatt server that implements all of the callbacks
 * to communicate with a Gatt Client. Actual understanding of the incoming or
 * outgoing information is not included here. At this layer we only deal with
 * in and out-bound bytes.
 */
public class GattServer extends BluetoothGattServerCallback {
    private static final String TAG = BenchmarkServer.class.getSimpleName();

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattService mBluetoothGattService;

    private CharacteristicHandler mHandler;
    private GattData mCharReadResponse;
    private ConnectionUpdater mConnUpdater;

    public boolean mHasBTSupport = true;

    private byte [] mBuffer = null;

    private Context mAppContext = null;

    private boolean mStopAdvOnConnect = false;

    /**
     * Constructor - set up the benchmark profile, open the time stamp file,
     * get a {@link BlutoothManager}, and check if we have the necessary
     * Bluetooth support
     * @param context - application context
     * @param service - service to advertise
     */
    public GattServer(Context context, BluetoothGattService service) {
        mAppContext = context;
        mBluetoothGattService = service;

        mBuffer = new byte [512]; //max characteristic size

        mBluetoothManager = (BluetoothManager) mAppContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            mHasBTSupport = false;
        }
    }

    /**
     * Set the callback for handling incoming data
     * @param func
     * @return
     */
    public boolean setCharacteristicHandler(CharacteristicHandler func) {
        if (null != func) {
            mHandler = func;
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Set up a broadcast listener for adapter state, enable the Bluetooth
     * adapter if it is not already or start advertising and the GATT server.
     * @param stopAdvOnConnect - boolean to indicate whether to stop
     *                         advertising once a connection has been made
     */
    public void start(boolean stopAdvOnConnect) {
        mStopAdvOnConnect = stopAdvOnConnect;
        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mAppContext.registerReceiver(mBluetoothReceiver, filter);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            startServer();
            startAdvertising();

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
     * Set the callback to be used for communicating connection
     * parameter changes up the stack
     * @param updater - the callback
     */
    public void setConnectionUpdateCallback(ConnectionUpdater updater) {
        mConnUpdater = updater;
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
                    startServer();
                    startAdvertising();
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
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BenchmarkProfileServer.BENCHMARK_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        Log.d(TAG, "Stopping advertisements");
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Benchmark Profile.
     */
    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(mAppContext, this);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        mBluetoothGattServer.addService(mBluetoothGattService);
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
            /*
            Based on a comment here: https://stackoverflow.com/questions/47676988/the-device-gattserver-stops-advertising-after-connecting-to-it
            The peripheral is supposed to stop advertising after a connection per the spec.... This
            would keep the peripheral from connecting to multiple centrals which we know is allowed
            by BT 4.2. However, it is clear that calling the following code led to instant
            disconnects
             */
//            if (mStopAdvOnConnect) {
//                stopAdvertising();
//            }
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
//        Log.i(TAG, "Received: " + String.valueOf(value));
//        Log.i(TAG, "handler is null? " + (null == mHandler));

        //callback to hand data up
        mHandler.handleCharacteristic(new GattData (device.getAddress(), characteristic.getUuid(), value));

        if (responseNeeded) {
            //Presumably the client's onCharacteristicWrite only gets called on receipt of
            //an acknowledgement
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }

    }

    /**
     * First call readies the characteristic which is then provided directly
     * through the characteristic without further interaction with the profile.
     * This is done because it means that you don't calculate or ready the
     * characteristic *every* time there is a change.
     *
     *  question: what do you do when you want to read > 512bytes?
     *  answer: multiple req's which is coordinated by profile
     *
     * @param device - the bluetooth device sending the read request
     * @param requestId  - the ID of the request
     * @param offset - the desired offset into the characteristic value to read
     * @param characteristic - the characteristic to which the read is requested
     */
    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device,
                                            int requestId, int offset,
                                            BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic);


        if (0 == offset) {
            mCharReadResponse = null;
            //hand off to profile layer to ready the characteristic
            mCharReadResponse = mHandler.handleCharacteristic(new GattData
                    (device.getAddress(), characteristic.getUuid(), null));

            characteristic.setValue(mCharReadResponse.mBuffer);
        }

        //null if the profile has no idea what to do with this request
        //otherwise the response has the complete response information
        if (null != mCharReadResponse) {
            int length = characteristic.getValue().length;
            if (offset > length) {
                //Log.i("BlueNet", "sending read response end");
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0});
                return;
            }

            int size = length - offset;
            byte[] response = new byte[size];

            for (int i = offset; i < length; i++) {
                response[i - offset] = characteristic.getValue()[i];
            }

            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response);
            return;
        }
        else {
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            return;
        }

    }

    /**
     * Report updated mtu to profile server
     *
     * @param device central that has initiated change
     * @param mtu new mtu value
     */
    @Override
    public void onMtuChanged (BluetoothDevice device, int mtu) {
        super.onMtuChanged(device, mtu);
        Log.i("Gatt", "MTU set to: " + String.valueOf(mtu));
        mConnUpdater.mtuUpdate(device.getAddress(), mtu);
    }

    /**
     *
     * @param device
     * @param requestId
     * @param descriptor
     * @param preparedWrite
     * @param responseNeeded
     * @param offset
     * @param value
     */
    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        Log.i(INFO_TAG, device + " registering");
        if (CLIENT_UUID.equals(descriptor.getUuid())) {
            if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                mRegisteredDevices.add(device);
                notifyOnConnected(this);
                // TODO: send list of registered devices
            } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                mRegisteredDevices.remove(device);
                notifyOnDisconnected(this);
            }

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }


        } else {
            Log.w(INFO_TAG, "Unknown descriptor write request");
            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }
    }

}
