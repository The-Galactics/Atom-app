package com.atom.application.usecase;

import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.out.ExternalInteractionPortOut;
import com.atom.domain.action.ResolvedAction;

import java.util.UUID;

public class ExternalCommandUseCase implements ExecuteCommandPortIn {

    private final ExternalInteractionPortOut out;

    public ExternalCommandUseCase (ExternalInteractionPortOut out) {
        this.out = out;
    }

    // Delegate order recognition to the backend.
    @Override
    public ResolvedAction execute(UUID userId, String command) {

        return out.commandResponse(userId, command);

    }

}
