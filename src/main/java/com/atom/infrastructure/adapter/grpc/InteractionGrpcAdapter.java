package com.atom.infrastructure.adapter.grpc;

import com.atom.application.port.out.ExternalInteractionPortOut;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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
    public ResolvedAction commandResponse(UUID userId, String command) {

        CommandRequest request = CommandRequest.newBuilder()
                .setUserId(userId.toString())
                .setCommand(command)
                .build();

        CommandResponse response = this.blockingStub.executeCommand(request);

        // Map the transport response into the domain projection here, so the
        // domain/application layers never touch JSON or protobuf types.
        return new ResolvedAction(
                ActionType.fromWire(response.getActionType()),
                parseParameters(response.getParametersJson()),
                response.getOutMessage(),
                response.getConfidence(),
                response.getRequiresConfirmation());

    }

    /**
     * Flattens the backend {@code parameters_json} into a string map; values are
     * coerced to strings. Blank/{@code "{}"} short-circuits to an empty map.
     */
    private static Map<String, String> parseParameters(String parametersJson) {
        if (parametersJson == null) {
            return Map.of();
        }
        String trimmed = parametersJson.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed)) {
            return Map.of();
        }
        try {
            JSONObject json = new JSONObject(trimmed);
            Map<String, String> parameters = new LinkedHashMap<>();
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!json.isNull(key)) {
                    parameters.put(key, json.get(key).toString());
                }
            }
            return parameters;
        } catch (Exception malformed) {
            // Defensive: a malformed payload degrades to a parameterless action
            // rather than crashing the order flow.
            return Map.of();
        }
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
