package com.atom.application.usecase;

import com.atom.application.port.in.StreamChatPortIn;
import com.atom.application.port.out.ExternalInteractionPortOut;

import java.util.UUID;
import java.util.stream.Stream;

public class ExternalMessageUseCase implements StreamChatPortIn {

    private final ExternalInteractionPortOut out;

    public ExternalMessageUseCase (ExternalInteractionPortOut out){
        this.out = out;
    }

    //Implement message execution flow.
    @Override
    public Stream<String> messageChat(UUID userId, UUID chatId, String message) {

        return out.messageResponse(userId, chatId, message);

    }

}
