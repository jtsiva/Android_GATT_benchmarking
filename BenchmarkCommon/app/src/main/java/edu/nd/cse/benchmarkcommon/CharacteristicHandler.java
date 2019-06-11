package edu.nd.cse.benchmarkcommon;

import java.util.UUID;

/**
 * This interface is used for passing data back and forth between
 * the profile and GATT layers.
 */
public interface CharacteristicHandler {

    /**
     * Generic sort of function that can be used for handling data in
     * either direction (writes or receives)
     *
     * @param GattData - the data coming from or going to the GATT layer
     */
    public void handleCharacteristic (GattData data);
}

