package edu.nd.cse.gatt_client;

public interface BenchmarkProfileClientCallback {

    public void onBenchmarkComplete ();

    public void onRawDataAvailable (String [] data);

    public void onThroughputAvailable (float throughput);

    public void onLossRateAvailable (float lossRate);

    public void onBenchmarkError (int code, String details);
}
