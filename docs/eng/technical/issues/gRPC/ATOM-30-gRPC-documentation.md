# Technical Documentation: gRPC Connection [ATOM].

> **Author:** Emmanuel Suárez García.
> **Content:** Connection infrastructure.
> **Status:** 🟢 Implemented.

---

This document aims to explain the inner workings of the connection created through the gRPC client to connect the Java backend with the Python service, where responses or interactions sent by the user via the mobile application are created. It will cover the connection lifecycle management and the behavior of its methods.

## 1. Creating or modifying the proto contract:

To create our connection with gRPC, we first need a contract where the main rules for its operation are defined, such as:

- **Package:** This is responsible for managing the root path to create the contract classes.
- **Configuration:** This section defines the two most important functionalities of the contract: the creation of multiple classes for better request handling from the client (Java) to the server (Python service); and finally, the creation of a container class for the metadata created by gRPC.
- **Service:** This section provides functionality to the contract, utilizing two main methods: ExecuteCommand and StreamChat, which allow sending and requesting information to and from the server (Python service).
- **Classes/Parameters:** These are the vital core of the contract's operation, where we define what information we are going to send and what binary space position each field will utilize.


### 1.2 Methods and parameters:

The proto contract must have a functionality defined through methods that can be used by the user when making a request. In the proto contract, there are 2 main methods, ExecuteCommand and StreamChat, which are destined for a specific functionality.

**ExecuteCommand:** This synchronous method allows the user to execute a command, such as generating an interaction on the device. This method uses two mandatory classes: the input parameter **CommandRequest**, and it returns the **CommandResponse** class.

**StreamChat:** This asynchronous method allows the user to send a message, which will be answered by the AI. It is asynchronous because it allows showing the response to the user token by token, creating the effect that the AI is responding at the same time it thinks. This method uses two classes: the input parameter **MessageRequest**, and the asynchronous return class **MessageResponse**, which uses **stream** to keep the connection constantly open to receive information from the server (Python service).

---
> **Direct Access:**
> [Proto contract](../../../../../java/src/main/proto/ai.proto)


## 2. gRPC connection lifecycle:

To maintain the backend's performance, a connection lifecycle was created to avoid leaving connection sockets open, preventing the consumption of resources in a process that is not being used. This lifecycle is managed by:

- **Environment variables:** To create our connection, we must take into account that we need a host and a port, which are brought from the properties and injected by the constructor to maintain the encapsulation of the adapter.
- **Connection:** To create our connection, we first instantiate our channel (Socket), which is responsible for keeping the connection alive, closing it, and creating it; on the other hand, our stub (Client/Proxy) works underneath serializing the contract classes such as **CommandRequest** or **MessageRequest**.
- **Execution:** This part is where our contract methods come to life, defining their main functionality, such as building the request and its response.
- **Shutdown:** To avoid wasting excess system resources, a method was created to close the connection socket to prevent leaks.

### 2.1 Variables de entorno:
To create our connection with **ManagedChannel**, we must have our port and our host in our properties to avoid hardcoding information. The required variables in the properties are:

**grpc.agent.host:** This is our key in the properties for the host, but its value is **${GRPC_AGENT_HOST:localhost}**, allowing the easy integration of Spring Boot's @Value annotation, which looks for the value to inject into the variable where the annotation is used. To change the host, you must change the value after the : of the variable.

**grpc.agent.port:** This is our key in the properties for the port, but its value is **${GRPC_AGENT_PORT:5000}**, allowing the easy integration of Spring Boot's @Value annotation, which looks for the value to inject into the variable where the annotation is used. To change the port, you must change the value after the : of the variable.

### 2.2 Connection creation and shutdown:

For managing the creation and closure of the connection, two essential methods were created:

**init():** This method is responsible for creating the socket connection through environment variables located in the properties. Here, our instances of **ManagedChannel** (channel) and our proto contract service **AtomAgentServiceGrpc** (blockingStub) are used. First, we create our channel setting up our network with **ManagedChannelBuilder**, where we add our port and host variables; then, we create our stub with **newBlockingStub**, which we link to the connection we created previously.
**shutdown:** This method is responsible for ensuring a "clean shutdown" of system resources. First, a validation is generated to verify if the channel connection was created when running the program; then, we verify that the connection is not already closed, which tells us if the connection can be turned off or not. If these validations are met, we proceed to close the connection with **channel.shutdown()**, stopping the reception of requests and finishing any remaining processes (Graceful Shutdown).

---

> **Direct Access:**
> [Contract integration](../../../../../java/src/main/java/com/atom/infrastructure/adapter/grpc/InteractionGrpcAdapter.java)

---
