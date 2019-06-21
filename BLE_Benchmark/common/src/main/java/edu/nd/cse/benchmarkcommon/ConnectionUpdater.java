package edu.nd.cse.benchmarkcommon;

public interface ConnectionUpdater {

    public void mtuUpdate(int mtu);

    public void connIntervalUpdate (int interval);

    public void connectionUpdate (String address);
}
