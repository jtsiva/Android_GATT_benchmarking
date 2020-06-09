package edu.nd.cse.gatt_server;

/**
 * The intention of this callback interface is to allow the server side
 * of the profile to provide updates to the application layer.
 */
public interface BenchmarkServiceServerCallback {
    public void onBenchmarkStart ();
    public void onBenchmarkComplete ();
    public void onBenchmarkError (int code, String details);
}
