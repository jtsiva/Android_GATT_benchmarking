package edu.nd.cse.benchmarkcommon;

import java.util.UUID;

/**
 * This interface is used for passing data back and forth between
 * the profile and GATT layers.
 */
public interface CharacteristicHandler {

    /**
     * Generic sort of function that can be used for handling data in
     * either direction (writes or reads). The convention is that if
     * the buffer in the data is empty then the operation is a read.
     *
     * @param GattData - the data coming from or going to the GATT layer
     * @return data for response (if needed), null if no response
     */
    public GattData handleCharacteristic (GattData data);
}

