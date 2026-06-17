package com.atom.application.port.in;

import com.atom.domain.action.ResolvedAction;

import java.util.UUID;

public interface ExecuteCommandPortIn {

    //Interpret a user order through python and return the action to execute.
    ResolvedAction execute(UUID userId, String command);

}
