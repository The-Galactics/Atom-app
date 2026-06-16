package com.atom.application.port.in;

import java.util.UUID;

public interface ExecuteCommandPortIn {

    //Method to execute commands through python.
    String execute(UUID userId, String command);

}
