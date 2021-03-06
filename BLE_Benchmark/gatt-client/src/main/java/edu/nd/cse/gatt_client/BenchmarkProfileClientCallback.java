package edu.nd.cse.gatt_client;

public interface BenchmarkProfileClientCallback {

    public static final int SET_MTU_ERROR = -1;
    public static final int SET_CONN_INTERVAL_ERROR = -2;

    public void onBenchmarkStart ();

    public void onBenchmarkComplete ();

    public void onRawDataAvailable (String [] data);

    public void onBytesSentAvailable (long bytesSent);

    public void onStartupLatencyAvailable (long startLatency);

    public void onThroughputAvailable (float throughput);

    public void onLossRateAvailable (float lossRate);

    public void onLatencyMeasurementsAvailable (long [] clientMeasurements, long [] serverMeasurements);

    public void onBenchmarkError (int code, String details);

    public void onServerIDAvailable(String id);
}
