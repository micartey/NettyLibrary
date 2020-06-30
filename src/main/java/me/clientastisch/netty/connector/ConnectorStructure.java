package me.clientastisch.netty.connector;

@SuppressWarnings("ALL")
public interface ConnectorStructure<T extends ConnectorStructure> {

    T initialise();

    T setProcessor(Class<? extends IConnector> processor);

    T setProcessor(IConnector processor);

    T addShutdownHook(Thread thread);

    T write(String message);

    T setName(String name);

    T terminate();

    T terminateGracefully();
}
