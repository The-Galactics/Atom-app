package com.atom.app.di;

import com.atom.app.BuildConfig;
import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.in.StreamChatPortIn;
import com.atom.application.port.in.security.DeviceSecurityPort;
import com.atom.application.port.in.security.InputValidationPort;
import com.atom.application.port.out.ExternalInteractionPortOut;
import com.atom.application.port.out.security.DeviceInspectorPort;
import com.atom.application.usecase.ExternalCommandUseCase;
import com.atom.application.usecase.ExternalMessageUseCase;
import com.atom.application.usecase.security.DeviceSecurityUseCase;
import com.atom.application.usecase.security.InputValidationUsecase;
import com.atom.infrastructure.adapter.grpc.InteractionGrpcAdapter;
import com.atom.infrastructure.adapter.out.device.DeviceInspectorAdapter;

public class AppContainer {

    // Infrastructure adapters (out-ports).
    private final InteractionGrpcAdapter interactionGrpcAdapter;
    private final DeviceInspectorPort deviceInspectorPort;

    // Application use-cases (in-ports).
    private final StreamChatPortIn externalMessageUseCase;
    private final ExecuteCommandPortIn externalCommandUseCase;
    private final DeviceSecurityPort deviceSecurityUseCase;
    private final InputValidationPort inputValidationUsecase;

    public AppContainer() {

        // gRPC adapter -> external interaction out-port. Host/port from BuildConfig.
        this.interactionGrpcAdapter = new InteractionGrpcAdapter(
                BuildConfig.GRPC_HOST,
                BuildConfig.GRPC_PORT
        );
        this.interactionGrpcAdapter.init();
        ExternalInteractionPortOut externalInteractionPortOut = this.interactionGrpcAdapter;

        // External interaction use-cases (chat streaming + command execution).
        this.externalMessageUseCase = new ExternalMessageUseCase(externalInteractionPortOut);
        this.externalCommandUseCase = new ExternalCommandUseCase(externalInteractionPortOut);

        // Device security use-case <- device inspector adapter.
        this.deviceInspectorPort = new DeviceInspectorAdapter();
        this.deviceSecurityUseCase = new DeviceSecurityUseCase(deviceInspectorPort);

        // Input validation use-case (no out-port dependencies).
        this.inputValidationUsecase = new InputValidationUsecase();
    }

    public StreamChatPortIn getExternalMessageUseCase() {
        return externalMessageUseCase;
    }

    public ExecuteCommandPortIn getExternalCommandUseCase() {
        return externalCommandUseCase;
    }

    public DeviceSecurityPort getDeviceSecurityUseCase() {
        return deviceSecurityUseCase;
    }

    public InputValidationPort getInputValidationUsecase() {
        return inputValidationUsecase;
    }

    /** Releases process-scoped resources. Call once on application teardown. */
    public void shutdown() {
        this.interactionGrpcAdapter.shutdown();
    }
}
