package com.atom.infrastructure.adapter.grpc;

import com.atom.application.port.out.ExternalInteractionPortOut;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class InteractionGrpcAdapter implements ExternalInteractionPortOut {

    private final String host;
    private final int port;

    private ManagedChannel channel;
    private AtomAgentServiceGrpc.AtomAgentServiceBlockingStub blockingStub;

    public InteractionGrpcAdapter(String host, int port){

        this.host = host;
        this.port = port;

    }

    public void init(){

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = AtomAgentServiceGrpc.newBlockingStub(channel);
        System.out.println("gRPC open to connect with python");

    }

    @Override
    public String commandResponse(UUID userId, String command) {

        CommandRequest request = CommandRequest.newBuilder()
                .setUserId(userId.toString())
                .setCommand(command)
                .build();

        CommandResponse response = this.blockingStub.executeCommand(request);
        return response.getOutMessage();

    }

    @Override
    public Stream<String> messageResponse(UUID userId, UUID chatId, String message) {

        MessageRequest request = MessageRequest.newBuilder()
                .setUserId(userId.toString())
                .setChatId(chatId.toString())
                .setMessage(message)
                .build();

        Iterator<MessageResponse> responseIterator = this.blockingStub.streamChat(request);

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(responseIterator, 0), false)
                .map(MessageResponse::getScriptToken);

    }

    // FUTURE WORK: voice (unified backend contract); not driven by any UI flow yet.
    @Override
    public String transcribeAudio(byte[] audioBytes, String mimeType, String language,
                                  String format, int beamSize) {

        TranscribeRequest request = TranscribeRequest.newBuilder()
                .setAudioBytes(ByteString.copyFrom(audioBytes))
                .setMimeType(mimeType)
                .setLanguage(language)
                .setFormat(format)
                .setBeamSize(beamSize)
                .build();

        TranscribeResponse response = this.blockingStub.transcribe(request);
        return response.getText();

    }

    @Override
    public Stream<byte[]> synthesizeSpeech(String text, String voice, String language,
                                           String format, float speed) {

        SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText(text)
                .setVoice(voice)
                .setLanguage(language)
                .setFormat(format)
                .setSpeed(speed)
                .build();

        Iterator<SynthesizeResponse> responseIterator = this.blockingStub.synthesize(request);

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(responseIterator, 0), false)
                .map(response -> response.getAudioBytes().toByteArray());

    }

    public void shutdown () {

        if (channel != null && !channel.isShutdown()){
            channel.shutdown();
            System.out.println("gRPC closed correctly");
        }

    }

}
