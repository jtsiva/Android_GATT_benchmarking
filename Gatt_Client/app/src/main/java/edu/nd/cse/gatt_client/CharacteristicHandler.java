package edu.nd.cse.gatt_client;
import java.util.UUID;

public interface CharacteristicHandler {

    public go (UUID charID, byte [] data);
}
