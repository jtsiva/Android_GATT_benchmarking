package edu.nd.cse.gatt_server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SaveToFileRunnable implements Runnable {

    private File file = null;
    private byte [] buffer;
    private boolean append;
    public SaveToFileRunnable (File outFile, byte [] data, boolean append) {
        this.file = outFile;
        this.buffer = data.clone();
        this.append = append;
    }

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
