package edu.nd.cse.gatt_client;

import android.widget.TextView;
import android.app.Activity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.os.Bundle;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Activity that runs the client side of the BLE benchmark
 */
public class BenchmarkClient extends Activity{
    /* UI elements */
    private TextView mParameters;
    private TextView mUpdates;

    /* Profile related things */
    private BenchmarkProfileClient mBenchmarkClient;


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

        mBenchmarkClient = new BenchmarkProfileClient (this, new BenchmarkProfileClientCallback () {
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

                mBenchmarkClient.requestThroughput();
            }

            @Override
            public void onRawDataAvailable (String [] data){
                //nothing
            }

            @Override
            public void onThroughputAvailable (final float throughput){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUpdates.append("Throughput: " + throughput);
                        mUpdates.append("\n");
                    }
                });
            }

            @Override
            public void onLossRateAvailable (final float lossRate){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUpdates.append("Loss Rate: " + lossRate);
                        mUpdates.append("\n");
                    }
                });
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

        mBenchmarkClient.prepare(); //set up testing params with default values
        mBenchmarkClient.beginBenchmark(); //run default length of 10 seconds
    }

    @Override
    protected void onStop(){
        super.onStop();
        mBenchmarkClient.cleanup();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}