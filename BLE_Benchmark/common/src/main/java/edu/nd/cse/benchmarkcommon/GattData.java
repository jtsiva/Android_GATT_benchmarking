package edu.nd.cse.benchmarkcommon;

import java.util.UUID;

/**
 * Class to encapsulate the data that is moved between the GATT
 * and Profile layers
 */
public class GattData {
    public String mAddress;
    public UUID mCharID;
    public byte [] mBuffer;

    public GattData (String address, UUID charID, byte[] data) {
        mAddress = address;
        mCharID = charID;
        mBuffer = data.clone();
    }
}
