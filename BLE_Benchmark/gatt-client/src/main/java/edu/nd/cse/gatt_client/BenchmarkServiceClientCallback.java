package edu.nd.cse.gatt_client;

import edu.nd.cse.benchmarkcommon.BenchmarkServiceCallback;

public interface BenchmarkServiceClientCallback extends BenchmarkServiceCallback{

    public static final int SET_MTU_ERROR = -1;
    public static final int SET_CONN_INTERVAL_ERROR = -2;
    public static final int SET_COMM_METHOD_ERROR = -3;

    public void onRawDataAvailable (String [] data);

    public void onStartupLatencyAvailable (long startLatency);

    public void onLossRateAvailable (float lossRate);

    public void onLatencyMeasurementsAvailable (long [] clientMeasurements, long [] serverMeasurements);


    public void onServerIDAvailable(String id);
}
