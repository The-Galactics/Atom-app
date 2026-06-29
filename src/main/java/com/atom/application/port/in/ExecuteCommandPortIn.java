package com.atom.application.port.in;

import com.atom.domain.action.ResolvedAction;

import java.util.UUID;

public interface ExecuteCommandPortIn {

    //Interpret a user order through python and return the action to execute.
    // orderId scopes one ReAct task across all its turns (incl. the spoken
    // confirmation reply); keep it constant for a whole executeAutonomous run.
    ResolvedAction execute(UUID userId, UUID orderId, String command);

}
