package edu.nd.cse.gatt_server;

import android.app.Activity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;

import java.util.Date;
import java.sql.Timestamp;

/**
 * Activity that runs the gatt server. Receives operating parameters
 * and passes to server.
 */
public class BenchmarkServer extends Activity{

    /* UI elements */
    private TextView mParameters;
    private TextView mUpdates;

    /* Benchmarking Profile Server */
    private BenchmarkProfileServer mBenchmarkServer;


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
        mBenchmarkServer = new BenchmarkProfileServer(this, new BenchmarkProfileServerCallback () {
            @Override
            public void onBenchmarkStart () {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Timestamp ts = new Timestamp(new Date().getTime());
                        mUpdates.append("Benchmark started at: " + ts);
                        mUpdates.append("\n");
                    }
                });
            }

            @Override
            public void onBenchmarkComplete (){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Timestamp ts = new Timestamp(new Date().getTime());
                        mUpdates.append("Benchmark completed at: " + ts);
                        mUpdates.append("\n");
                    }
                });
                mBenchmarkServer.stop();
            }

            @Override
            public void onBenchmarkError (final int code, final String details) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUpdates.append("Error " + code + ": " + details);
                        mUpdates.append("\n");
                    }
                });
            }
        });

        mBenchmarkServer.start();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
