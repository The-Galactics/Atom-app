package com.atom.infrastructure.adapter.grpc;

import com.atom.app.BuildConfig;
import com.atom.application.port.out.ExternalInteractionPortOut;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;
import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService;
import com.google.protobuf.ByteString;
import io.grpc.Channel;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class InteractionGrpcAdapter implements ExternalInteractionPortOut {

    private static final int COMMAND_DEADLINE_SECONDS = 30;
    private static final int TRANSCRIBE_DEADLINE_SECONDS = 30;

    private final Channel channel;
    private AtomAgentServiceGrpc.AtomAgentServiceBlockingStub blockingStub;

    public InteractionGrpcAdapter(Channel authedChannel) {
        this.channel = authedChannel;
    }

    public void init() {
        this.blockingStub = AtomAgentServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public ResolvedAction commandResponse(UUID userId, UUID orderId, String command) {

        CommandRequest request = CommandRequest.newBuilder()
                .setUserId(userId.toString())
                .setCommand(command)
                .addAllScreenElements(captureScreenElements())
                .setOrderId(orderId == null ? "" : orderId.toString())
                .build();

        CommandResponse response = this.blockingStub
                .withDeadlineAfter(COMMAND_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .executeCommand(request);

        // Map the transport response into the domain projection here, so the
        // domain/application layers never touch JSON or protobuf types.
        return new ResolvedAction(
                ActionType.fromWire(response.getActionType()),
                parseParameters(response.getParametersJson()),
                response.getOutMessage(),
                response.getConfidence(),
                response.getRequiresConfirmation(),
                response.getTaskComplete(),
                response.getStep(),
                response.getAwaitingConfirmation());

    }

    /**
     * Snapshots the current screen via the accessibility service and maps each
     * node to a {@link ScreenElement}. Empty when the service is disabled — the
     * only place the proto ScreenElement type is constructed (layering).
     */
    private static List<ScreenElement> captureScreenElements() {
        AtomAccessibilityService service = AtomAccessibilityService.getInstance();
        if (service == null) {
            return List.of();
        }
        List<ScreenElement> elements = new ArrayList<>();
        for (AtomAccessibilityService.ScreenNode node : service.captureScreen()) {
            elements.add(ScreenElement.newBuilder()
                    .setText(node.text)
                    .setRole(node.role)
                    .setClickable(node.clickable)
                    .setFocusable(node.focusable)
                    .setEditable(node.editable)
                    .setScrollable(node.scrollable)
                    .setIndex(node.index)
                    .build());
        }
        return elements;
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

        TranscribeResponse response = this.blockingStub
                .withDeadlineAfter(TRANSCRIBE_DEADLINE_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .transcribe(request);
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

        Iterator<SynthesizeResponse> responseIterator = this.blockingStub
                .withDeadlineAfter(60, java.util.concurrent.TimeUnit.SECONDS)
                .synthesize(request);

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(responseIterator, 0), false)
                .map(response -> response.getAudioBytes().toByteArray());

    }

    public void shutdown() {
        // Channel lifecycle is owned by GrpcChannelProvider.
    }

}
