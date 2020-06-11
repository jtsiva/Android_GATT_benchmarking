package edu.nd.cse.benchmarkcommon;

public interface BenchmarkServiceCallback {
    public void onBenchmarkStart ();

    public void onBenchmarkComplete ();

    public void onBytesSentAvailable (long bytesSent);

    public void onBenchmarkError (int code, String details);
}
