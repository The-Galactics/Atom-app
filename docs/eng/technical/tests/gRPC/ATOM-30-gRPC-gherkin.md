# Technical Documentation: gRPC Unit Testing [ATOM].

> **Author:** Emmanuel Suárez García.
> **Content:** Interaction Adapter Unit Tests.
> **Status:** 🟢 Implemented.

---

This document describes the structure and behavior of the unit tests designed for the gRPC connection layer. To validate network messaging without launching external services or loading a heavy Spring Boot context framework, an isolated in-memory network server architecture was built.

## 1. Test Architecture Configuration:

To validate our gRPC adapter, we established a controlled environment that mimics the production network using lightweight, thread-safe testing utilities:

- **GrpcCleanupRule:** An official tool that automatically manages and destroys network channels and servers after each test execution to prevent thread leaks.
- **FakeAtomAgentService:** A concrete static inner class that extends the generated gRPC base implementation. It acts as a mock for the Python server, allowing us to predefine standard responses or trigger specific errors.
- **In-Memory Server & Channel:** Leverages `InProcessServerBuilder` and `InProcessChannelBuilder` to build a localized communication loopback that bypasses real network ports.
- **Reflection Mechanism:** Manually injects the mock channel and stub objects into the adapter instance, decoupling the test suite completely from heavy integration frameworks.

### 1.2 Test Cases and Validation Criteria

The test suite validates the exact methods mapped out in the proto contract definition, analyzing both synchronous and streaming communication pipelines:

**shouldReturnCommandResponseSuccessfully:** This test verifies the synchronous (unary) RPC execution flow. It triggers a system status request, intercepts the parameter payload using a preconfigured `CommandResponse`, and executes assertions to guarantee that the adapter unpacks and formats the server message properly.

**shouldReturnStreamChatTokensCorrectly:** This test verifies the server-streaming RPC communication pattern used for AI interactions. It sets up an ordered list of individual data tokens (`MessageResponse`) and ensures that the adapter processes them as a continuous reactive sequence (`Stream<String>`). It verifies that all chunks are collected in the exact order they were broadcasted by the mock server.

---
> **Direct Access:**
> [Test Execution Reference](../../../../../java/src/test/java/com/atom/grpc/InteractionGrpcAdapterTest.java)

## 2. Test Infrastructure Lifecycle:

To guarantee test independence and repeatability, a clear initialization and teardown sequence manages all in-memory system sockets:

- **Setup Phase:** Generates a randomized server address, boots the in-memory architecture, establishes a direct client executor, and executes the reflection mechanism to inject internal fields.
- **Teardown Phase:** Invokes immediate hard destruction (`shutdownNow()`) on the active channels at the end of each test to avoid test cross-contamination or background resource consumption.

### 2.1 Rationale for Key Test Objects

| Component / Object              | Technical Reason for Implementation                                                                                       |
|:--------------------------------|:--------------------------------------------------------------------------------------------------------------------------|
| **InProcessServerBuilder**      | Generates an isolated embedded server inside the JVM, enabling fast execution and preventing port collision errors.       |
| **StreamObserver**              | The reactive mechanism used to feed simulated payloads or stream chunks sequentially back to the client proxy.            |
| **Reflection Field Injections** | Essential strategy to override private properties inside the adapter class without exposing them through production code. |
