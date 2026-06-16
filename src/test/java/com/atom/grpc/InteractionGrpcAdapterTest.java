package com.atom.grpc;

import com.atom.infrastructure.adapter.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class InteractionGrpcAdapterTest {

    // gRPC official rule to automatically clear channels and servers after each test execution
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private ManagedChannel inProcessChannel;
    private InteractionGrpcAdapter adapter;
    private FakeAtomAgentService fakeService;

    // Concrete static inner class to simulate real Python server behavior without Mockito bytecode bugs
    private static class FakeAtomAgentService extends AtomAgentServiceGrpc.AtomAgentServiceImplBase {
        CommandResponse commandResponseResult;
        List<MessageResponse> streamChatResults;

        /**
         * Simulates the synchronous executeCommand unary RPC method.
         * Pushes the predefined command response to the observer if present.
         */
        @Override
        public void executeCommand(CommandRequest request, StreamObserver<CommandResponse> responseObserver) {
            if (commandResponseResult != null) {
                responseObserver.onNext(commandResponseResult);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(io.grpc.Status.UNAVAILABLE.asRuntimeException());
            }
        }

        /**
         * Simulates the asynchronous streamChat server-streaming RPC method.
         * Iterates and streams multiple chunks/tokens to the response observer sequentially.
         */
        @Override
        public void streamChat(MessageRequest request, StreamObserver<MessageResponse> responseObserver) {
            if (streamChatResults != null) {
                for (MessageResponse res : streamChatResults) {
                    responseObserver.onNext(res);
                }
                responseObserver.onCompleted(); // Closes the data stream channel
            } else {
                responseObserver.onError(io.grpc.Status.UNAVAILABLE.asRuntimeException());
            }
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // 1. Generate a unique name for the in-memory gRPC server
        String serverName = InProcessServerBuilder.generateName();
        fakeService = new FakeAtomAgentService();

        // 2. Start the lightweight in-memory gRPC server with the concrete service instance
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(fakeService)
                .build()
                .start());

        // 3. Create the communication channel targeting the in-memory server instance
        inProcessChannel = grpcCleanup.register(InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build());

        // 4. Instantiate the target adapter with dummy host and port parameters
        adapter = new InteractionGrpcAdapter("localhost", 50051);

        // Reflection mechanism to inject the in-memory channel and stub instances.
        // This isolates the test class completely, avoiding loading the heavy Spring Boot context (@SpringBootTest)
        try {
            java.lang.reflect.Field channelField = InteractionGrpcAdapter.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(adapter, inProcessChannel);

            java.lang.reflect.Field stubField = InteractionGrpcAdapter.class.getDeclaredField("blockingStub");
            stubField.setAccessible(true);
            stubField.set(adapter, AtomAgentServiceGrpc.newBlockingStub(inProcessChannel));
        } catch (Exception e) {
            fail("Failed to set up infrastructure dependencies for gRPC unit test: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (inProcessChannel != null) {
            inProcessChannel.shutdownNow();
        }
    }

    @Test
    void shouldReturnCommandResponseSuccessfully() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        String command = "system:status";

        fakeService.commandResponseResult = CommandResponse.newBuilder()
                .setOutMessage("System operational on Linux Mint")
                .setSuccess(true)
                .build();

        // WHEN
        String result = adapter.commandResponse(userId, command);

        // THEN
        assertNotNull(result);
        assertEquals("System operational on Linux Mint", result);
    }

    @Test
    void shouldReturnStreamChatTokensCorrectly() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        String message = "Hello Atom";

        // Setup individual stream message tokens to simulate model output stream chunks
        MessageResponse token1 = MessageResponse.newBuilder().setScriptToken("Hello").build();
        MessageResponse token2 = MessageResponse.newBuilder().setScriptToken(", bro!").build();

        fakeService.streamChatResults = List.of(token1, token2);

        // WHEN
        Stream<String> resultStream = adapter.messageResponse(userId, chatId, message);

        // THEN
        assertNotNull(resultStream);
        List<String> collectedTokens = resultStream.toList();

        assertEquals(2, collectedTokens.size());
        assertEquals("Hello", collectedTokens.get(0));
        assertEquals(", bro!", collectedTokens.get(1));
    }
}
