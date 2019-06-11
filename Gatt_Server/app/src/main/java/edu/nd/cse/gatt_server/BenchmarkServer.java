package edu.nd.cse.gatt_server;

import edu.nd.cse.BenchmarkCommon;
import android.app.Activity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;

/**
 * Activity that runs the gatt server. Receives operating parameters
 * and passes to server.
 */
public class BenchmarkServer extends Activity{

    /* UI elements */
    private TextView mParameters;
    private TextView mUpdates;
    private BenchmarkCommon.UiUpdate mUpdater = new BenchmarkCommon.UiUpdate () {
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
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mParameters = (TextView) findViewById(R.id.params);
        //Bundle receiveBundle = this.getIntent().getExtras();
        mParameters.append("Parameters:\n");
        //Here we would append a text version of all of the parameters


        mUpdates = (TextView) findViewById(R.id.updates);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //start profile

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
