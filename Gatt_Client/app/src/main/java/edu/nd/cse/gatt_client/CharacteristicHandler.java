package edu.nd.cse.gatt_client;
import java.util.UUID;

/**
 * This interface is used for passing data back and forth between
 * the BenchmarkProfileClient and the GattClient.
 */
public interface CharacteristicHandler {

    /**
     * Generic sort of function that can be used for handling data in
     * either direction (writes or receives)
     *
     * @param GattData - the data coming from or going to the GATT layer
     */
    public void go (GattData data);
}
