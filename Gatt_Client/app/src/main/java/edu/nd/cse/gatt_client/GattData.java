package edu.nd.cse.gatt_client;

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
