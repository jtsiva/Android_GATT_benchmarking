package edu.nd.cse.gatt_server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A runnable used to save a byte array of data to a file in the background
 */
public class SaveToFileRunnable implements Runnable {

    private File file = null;
    private byte [] buffer;
    private boolean append;

    /**
     * Constructor - initialize the file obj, the data buffer, and append op
     * @param outFile
     * @param data
     * @param append
     */
    public SaveToFileRunnable (File outFile, byte [] data, boolean append) {
        this.file = outFile;
        this.buffer = data.clone();
        this.append = append;
    }

    /**
     * Run function that will open the file (if possible) a write the byte
     * array to the file
     */
    public void run(){
        FileOutputStream stream = null;
        if (null != file) {
            try {
                stream = new FileOutputStream(file, append);
                stream.write(buffer);
                stream.close();
            } catch(IOException e) {
            }
        }
    }
}
