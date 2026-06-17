package com.atom.application.port.out;

import com.atom.domain.action.ResolvedAction;

import java.util.UUID;
import java.util.stream.Stream;

public interface ExternalInteractionPortOut {

    //Interpret a user order via python and return the resolved action to execute.
    ResolvedAction commandResponse (UUID userId, String command);

    //Get the message response of python to display token per token to the user.
    Stream<String> messageResponse (UUID userId, UUID chatId, String message);

    // FUTURE WORK: Speech-to-Text; no voice-capture UI flow is wired yet.
    String transcribeAudio (byte[] audioBytes, String mimeType, String language, String format, int beamSize);

    // FUTURE WORK: Text-to-Speech; no audio-playback UI flow is wired yet.
    Stream<byte[]> synthesizeSpeech (String text, String voice, String language, String format, float speed);

}
