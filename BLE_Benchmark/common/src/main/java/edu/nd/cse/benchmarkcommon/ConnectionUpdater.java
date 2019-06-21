package edu.nd.cse.benchmarkcommon;

import ConnectionUpdaterIFace;

public class ConnectionUpdater implements ConnectionUpdaterIFace {

    public void mtuUpdate(String address, int mtu){
        //do nothing
    }

    public void connIntervalUpdate (String address, int interval){
        //do nothing
    }

    public void connectionUpdate (String address, int state){
        //do nothing
    }
}
