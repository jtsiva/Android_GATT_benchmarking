package edu.nd.cse.benchmarkcommon;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;

//https://stackoverflow.com/questions/52832640/programmatically-restarting-bluetooth

/**
 * The purpose of this class is to provide a mechanism to reset the Bluetooth
 * adapter. Over the dozens of tests that are to be done, the Bluetooth can
 * start doing funky things (maybe from not releasing resources nicely?). This
 * can help alleviate that problem.
 */
public class BluetoothRestarter {

    private Context mContext;
    private RestartListener mListener;
    private BroadcastReceiver mReceiver;

    public BluetoothRestarter(Context context) {
        mContext = context;
    }

    /**
     * Sets up a broadcast receiver to deal with the BT intents, turns the
     * bluetooth off, and then turns the bluetooth on again. Fires callback
     * once the bluetooth has been turned on again.
     *
     * @param listener - callback to fire once restart is complete
     */
    public void restart(RestartListener listener) {
        mListener = listener;
        if (mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    //https://stackoverflow.com/questions/9693755/detecting-state-changes-made-to-the-bluetoothadapter
                    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR);
                        Log.e("BluetoothRestarter", "HERE");
                        switch (state) {
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                Log.i ("BluetoothRestarter", "Turning Bluetooth off");
                                break;
                            case BluetoothAdapter.STATE_OFF:
                                if (!BluetoothAdapter.getDefaultAdapter().enable()) {
                                    Log.e("BluetoothRestarter", "Failed to start bluetooth!");
                                } else {
                                    Log.e("BluetoothRestarter", "Starting bluetooth!");
                                }
                                break;
                            case BluetoothAdapter.STATE_TURNING_ON:
                                Log.i ("BluetoothRestarter", "Turning Bluetooth on");
                                break;
                            case BluetoothAdapter.STATE_ON:
                                context.unregisterReceiver(this);
                                mListener.onRestartComplete();
                                break;
                        }
                    }

                }
            };
            mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            BluetoothAdapter.getDefaultAdapter().disable();
        }
    }

    /**
     * Simple callback to let someone know when the restart is complete
     */
    public interface RestartListener {
        public void onRestartComplete();
    }
}

