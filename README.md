# NettyLibrary

<div align="center">
  <a href="https://www.oracle.com/java/">
    <img
        src="https://img.shields.io/badge/Made%20with-Java-red?style=for-the-badge"
        height="30"
    />
  </a>
  <a href="https://jitpack.io/#micartey/NettyLibrary/v1.0-SNAPSHOT">
    <img
        src="https://img.shields.io/badge/Build-Jitpack-lgreen?style=for-the-badge"
        height="30"
    />
  </a>
  <a href="https://micartey.github.io/NettyLibrary/docs" target="_blank">
    <img
        src="https://img.shields.io/badge/javadoc-reference-5272B4.svg?style=for-the-badge"
        height="30"
    />
    </a>
</div>

<br>

<p align="center">
  <a href="#-introduction">Introduction</a> |
  <a href="#-terms-of-use">Getting started</a> |
  <a href="https://github.com/micartey/NettyLibrary/issues">Troubleshooting</a>
</p>

<p align="center">
  DEPRECATED
</p>

## 📚 Introduction

`NettyLibrary` is a dependency to easily setup a client-server connection. There were no changes to the source code for years and thus the project is deprecated. However, deprecated but still usable.

### Motivation

This project was created because I didn't want to use Java-Sockets. Mainly because Java-Sockets can only handle a single message at a time.

## 📖 Getting started

There aren't many classes in this project. However, this projects depends on netty and therefore needs an additional dependency. Maven example below:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>4.1.63.Final<version>
</dependency>
```

I don't know if newer versions of netty are compatible or how much needs to be changed. Maybe try around other versions and look for compiler errors (?)

### Cluster

The cluster is the server to which all the clients will connect.

```java
Cluster cluster = new Cluster(
    "127.0.0.1",
    3000,
    TYPE.STRING
).setProcessor(new ICluster() {...}).initialise();
```

[ICluster](https://micartey.github.io/NettyLibrary/docs/me/clientastisch/netty/cluster/ICluster.html) is an interface and has some functions which handle different events. You might want to instanciate it like in the provided example, but you can also create an extra class which implements the interface. Encoder type *STRING* is special to netty and not supported by other types. In case you want to receive messages from socket clients, you need to use *BYTEBUF*.

### Connector

The connector is the client trying to connect to the Server (Cluster).

```java
Connector connector = new Connector(
    "127.0.0.1",
    3000,
    TYPE.STRING
).setProcessor(new IConnector() {...}).initialise();
```

[IConnector](https://micartey.github.io/NettyLibrary/docs/me/clientastisch/netty/connector/IConnector.html) is similar to ICluster thus the above mentioned aspects also apply for this interface. 

### Sending messages to Cluster

You can send messages to the cluster using one of the following methods:

```java
connector.write("Some message")
```
```java
connector.getChannel().writeAndFlush("Some message\r\n")
```

Using the default netty channels will require you to use `\r\n` at the end because the message will not be send in case you didn't append it at the end.

### Sending messages to Clients

To send messages to clients you need to use the default netty channels. I  recommend to send messages to clients by listining for incomming messages and reply to those.