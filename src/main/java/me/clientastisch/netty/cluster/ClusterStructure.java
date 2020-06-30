package me.clientastisch.netty.cluster;

@SuppressWarnings("all")
public interface ClusterStructure<T extends ClusterStructure> {

    T setProcessor(Class<? extends ICluster> cluster);

    T setProcessor(ICluster cluster);

    T addShutdownHook(Thread thread);

    T autoPort(boolean autoPort);

    T setTimeOut(int timeOut);

    T setName(String name);

    boolean isAlive();

    T initialise();

    T terminate();

}
