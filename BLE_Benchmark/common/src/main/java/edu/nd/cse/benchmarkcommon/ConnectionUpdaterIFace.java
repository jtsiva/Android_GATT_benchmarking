package edu.nd.cse.benchmarkcommon;

public interface ConnectionUpdaterIFace {

    public void commMethodUpdate(String address, int method);

    public void mtuUpdate(String address, int mtu);

    public void connIntervalUpdate (String address, int interval);

    public void connectionUpdate (String address, int state);
}
