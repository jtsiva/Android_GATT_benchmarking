package edu.nd.cse.gatt_client;

public interface BenchmarkProfileClientCallback {

    public static final SET_MTU_ERROR = -1;
    public static final SET_CONN_INTERVAL_ERROR = -2;

    public void onBenchmarkComplete ();

    public void onRawDataAvailable (String [] data);

    public void onThroughputAvailable (float throughput);

    public void onLossRateAvailable (float lossRate);

    public void onBenchmarkError (int code, String details);
}
