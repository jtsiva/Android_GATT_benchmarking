package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class that manages the client side of the GATT I/O layer. Responsible
 * for scanning, setting up and managing connections, queuing write (or read)
 * operations, and interfacing with profile layer
 *
 */
public class GattClient implements BenchmarkCommon.CharacteristicHandler{
    private static final String TAG = BenchmarkClient.class.getSimpleName();
    private Map<String, BluetoothGatt> mConnectedDevices = new HashMap<String, BluetoothGatt>();
    private final Queue<BenchmarkCommon.GattData> mWriteQueue = new ConcurrentLinkedQueue<BenchmarkCommon.GattData>();
    private boolean mIsIdle = true;
    private BenchmarkCommon.UiUpdate mUiUpdate = null;

    /**
     *
     * @param context
     * @param targetServiceID
     */
    public GattClient (Context context, UUID targetServiceID) {

    }

    /**
     *
     * @param func
     */
    public void setUiUpdater (BenchmarkCommon.UiUpdate func) {

    }

    /**
     *
     * @param charHandler
     */
    public void setHandler (BenchmarkCommon.CharacteristicHandler charHandler) {

    }

    /**
     *
     * @param data
     */
    @Override
    public void go(BenchmarkCommon.GattData data) {
        if (null != data) {
            mWriteQueue.put(data);
            if (mIsIdle) {
                mIsIdle = false;
                BenchmarkCommon.GattData readyData = mWriteQueue.poll();
                performOperation(readyData);
            }
        }

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
    private void startScanning () {

    }

    /**
     *
     */
    private void stopScanning () {

    }

    /**
     *
     * @param data
     */
    private void performOperation (BenchmarkCommon.GattData data) {
        //perform operation on characteristic
    }

    /**
     *
     */
    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback () {
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
                connectFailure();
                Log.e(TAG, "onServicesDiscovered gatt failure");
                return;
            } else {
                Log.e(TAG, "onServicesDiscovered gatt success");
            }

            BluetoothDevice device = gatt.getDevice();
            String address = device.getAddress();
            final BluetoothGatt bluetoothGatt = mConnectedDevices.get(address);

            //reference to each UART characteristic
            if (null == bluetoothGatt.getService(UART_UUID)) {
                //connectFailure();
                Log.e(TAG, "onServicesDiscovered gatt failure");
                return;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG,"Characteristic write successful");


            } else {
                Log.d(TAG,"Characteristic write FAILED");
            }

            BenchmarkCommon.GattData data = mWriteQueue.poll();

            if (null == data) { //empty!
                mIsIdle = true;
            } else {
                mCharHandler.go(data);
            }

        }
    };
}
