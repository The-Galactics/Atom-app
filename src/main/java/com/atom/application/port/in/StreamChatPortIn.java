package com.atom.application.port.in;

import java.util.UUID;
import java.util.stream.Stream;

public interface StreamChatPortIn {

    //Method to send order and get response for the python service with AI.
    Stream<String> messageChat (UUID userId, UUID chatId, String message);

}
