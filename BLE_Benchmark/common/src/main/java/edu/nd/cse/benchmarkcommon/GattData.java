package edu.nd.cse.benchmarkcommon;

import java.util.UUID;

/**
 * Class to encapsulate the data that is moved between the GATT
 * and Profile layers. The convention is that if mBuffer is null
 * then it's as if this data structure is requesting to be filled
 * by the indicated characteristic at the given device address--
 * that is, it's a read op.
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
