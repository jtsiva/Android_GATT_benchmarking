package edu.nd.cse.gatt_client;

import android.widget.TextView;
import android.app.Activity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;

/**
 * Activity that runs the client side of the BLE benchmark
 */
public class BenchmarkClient extends Activity{
    /* UI elements */
    private TextView mParameters;
    private TextView mUpdates;

    /* Gatt related things */
    private GattClient mGattClient;
    private BenchmarkProfileClient mBenchmarkProfileClient;

    @Override
    public void updateText (final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUpdates.append(text);
                mUpdates.append("\n");
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mParameters = (TextView) findViewById(R.id.params);
        //Bundle receiveBundle = this.getIntent().getExtras();
        mParameters.append("Parameters:\n");
        //Here we would append a text version of all of the parameters


        mUpdates = (TextView) findViewById(R.id.updates);

        mGattClient = new GattClient(this, this);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Now that we're all set up, let's start the server and start
        //advertising
        mGattClient.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGattServer.stop();
    }
}