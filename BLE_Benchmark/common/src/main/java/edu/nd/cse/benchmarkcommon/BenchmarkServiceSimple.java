package edu.nd.cse.benchmarkcommon;

import android.os.SystemClock;

import java.util.Random;

import BenchmarkService;

public class BenchmarkServiceSimple extends BenchmarkService {

   /**
     * Create some random bytes and pass them off to the Gatt layer directed
     * at the test device. Schedule to do this again after conn interval ms if
     * this will not exceed the benchmark duration. Call onBenchmarkComplete
     * when done.
     *
     * Data to be sent is a pseudo-random collection of bits as suggested by
     * RFC4814 (https://tools.ietf.org/html/rfc4814#section-3). Since the data
     * to be sent *could* be encoded or compressed, it is imperative to not
     * just test using alpha-numeric characters.
     */
    protected Runnable goTest = new Runnable () {
        @Override
        public void run() {
            int packetSize = mMtu - 3;
            if (packetSize + mBenchmarkBytesSent > mBenchmarkDuration){

                packetSize = Math.toIntExact(mBenchmarkDuration - mBenchmarkBytesSent);
            }
            byte [] b = new byte[packetSize];
            new Random().nextBytes(b);


            GattData data = new GattData(mServerAddress, BenchmarkService.TEST_CHAR, b);
            mBenchmarkBytesSent += packetSize;

            mGattServer.handleCharacteristic(data);

            long now = SystemClock.elapsedRealtimeNanos ();


            if (mBenchmarkBytesSent < mBenchmarkDuration) {
                mBenchmarkHandler.postDelayed(this, mConnInterval);
            }

        }
    };

    public GattData handleTestCharReceive(GattData data) {

    }

    public GattData handleTestCharSend(GattData data){
        
    }


}
