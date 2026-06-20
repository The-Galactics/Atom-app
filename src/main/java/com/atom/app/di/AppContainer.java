package com.atom.app.di;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

import com.atom.app.BuildConfig;
import com.atom.app.data.ConversationRepository;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.in.StreamChatPortIn;
import com.atom.application.port.in.SynthesizeSpeechPortIn;
import com.atom.application.port.in.security.DeviceSecurityPort;
import com.atom.application.port.in.security.InputValidationPort;
import com.atom.application.port.out.ActionExecutorPortOut;
import com.atom.application.port.out.ExternalInteractionPortOut;
import com.atom.application.port.out.security.DeviceInspectorPort;
import com.atom.application.usecase.ExternalCommandUseCase;
import com.atom.application.usecase.ExternalMessageUseCase;
import com.atom.application.usecase.SynthesizeSpeechUseCase;
import com.atom.application.usecase.security.DeviceSecurityUseCase;
import com.atom.application.usecase.security.InputValidationUsecase;
import com.atom.infrastructure.adapter.action.AndroidActionExecutor;
import com.atom.infrastructure.adapter.grpc.InteractionGrpcAdapter;
import com.atom.infrastructure.adapter.out.device.DeviceInspectorAdapter;

public class AppContainer {

    // Infrastructure adapters (out-ports).
    private final InteractionGrpcAdapter interactionGrpcAdapter;
    private final DeviceInspectorPort deviceInspectorPort;
    private final ActionExecutorPortOut actionExecutorPortOut;

    // Application use-cases (in-ports).
    private final StreamChatPortIn externalMessageUseCase;
    private final ExecuteCommandPortIn externalCommandUseCase;
    private final SynthesizeSpeechPortIn synthesizeSpeechUseCase;
    private final DeviceSecurityPort deviceSecurityUseCase;
    private final InputValidationPort inputValidationUsecase;

    // Persistent conversation identity. Stored in SharedPreferences and reused across
    // app restarts so the backend keeps the same session (and its memory) for this
    // device. Shared by every ChatRepository (chat screen + floating bubble).
    private static final String SESSION_PREFS = "atom_session";
    private static final String KEY_USER_ID = "session_user_id";
    private static final String KEY_CHAT_ID = "session_chat_id";
    private final UUID sessionUserId;
    private final UUID sessionChatId;

    // Process-wide conversation transcript store (Room). Owned here so the chat
    // ViewModel persists turns at the event source, and every surface records the
    // dialogue through the same instance.
    private final ConversationRepository conversationRepository;

    // Application context, retained for process-scoped permission/state checks
    // (e.g. whether the accessibility service the user must enable is running).
    private final Context appContext;

    public AppContainer(Context context) {
        this.appContext = context.getApplicationContext();

        // Load (or lazily create + persist) the device-scoped conversation identity.
        SharedPreferences sessionPrefs =
                context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE);
        this.sessionUserId = loadOrCreateUuid(sessionPrefs, KEY_USER_ID);
        this.sessionChatId = loadOrCreateUuid(sessionPrefs, KEY_CHAT_ID);

        // gRPC adapter -> external interaction out-port. Host/port from BuildConfig.
        this.interactionGrpcAdapter = new InteractionGrpcAdapter(
                BuildConfig.GRPC_HOST,
                BuildConfig.GRPC_PORT
        );
        this.interactionGrpcAdapter.init();
        ExternalInteractionPortOut externalInteractionPortOut = this.interactionGrpcAdapter;

        // On-device action executor (out-port). Needs an Android context.
        this.actionExecutorPortOut = new AndroidActionExecutor(context);

        // External interaction use-cases (chat streaming + command execution).
        this.externalMessageUseCase = new ExternalMessageUseCase(externalInteractionPortOut);
        this.externalCommandUseCase = new ExternalCommandUseCase(externalInteractionPortOut);
        this.synthesizeSpeechUseCase = new SynthesizeSpeechUseCase(externalInteractionPortOut);

        // Device security use-case <- device inspector adapter.
        this.deviceInspectorPort = new DeviceInspectorAdapter();
        this.deviceSecurityUseCase = new DeviceSecurityUseCase(deviceInspectorPort);

        // Input validation use-case (no out-port dependencies).
        this.inputValidationUsecase = new InputValidationUsecase();

        // Conversation transcript store. Uses the application context internally,
        // so holding it on this process-scoped container leaks nothing.
        this.conversationRepository = new ConversationRepository(context);
    }

    public StreamChatPortIn getExternalMessageUseCase() {
        return externalMessageUseCase;
    }

    public ExecuteCommandPortIn getExternalCommandUseCase() {
        return externalCommandUseCase;
    }

    public SynthesizeSpeechPortIn getSynthesizeSpeechUseCase() {
        return synthesizeSpeechUseCase;
    }

    public ActionExecutorPortOut getActionExecutor() {
        return actionExecutorPortOut;
    }

    public DeviceSecurityPort getDeviceSecurityUseCase() {
        return deviceSecurityUseCase;
    }

    public InputValidationPort getInputValidationUsecase() {
        return inputValidationUsecase;
    }

    /** True if Atom's accessibility service is currently enabled by the user. */
    public boolean isAccessibilityEnabled() {
        return PermissionCoordinator.isAccessibilityServiceEnabled(appContext);
    }

    public ConversationRepository getConversationRepository() {
        return conversationRepository;
    }

    /** Returns the stored UUID for {@code key}, creating and persisting one if absent. */
    private static UUID loadOrCreateUuid(SharedPreferences prefs, String key) {
        String stored = prefs.getString(key, null);
        if (stored != null) {
            try {
                return UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
                // Corrupted value — fall through and regenerate.
            }
        }
        UUID generated = UUID.randomUUID();
        prefs.edit().putString(key, generated.toString()).apply();
        return generated;
    }

    public UUID getSessionUserId() {
        return sessionUserId;
    }

    public UUID getSessionChatId() {
        return sessionChatId;
    }

    /** Releases process-scoped resources. Call once on application teardown. */
    public void shutdown() {
        this.interactionGrpcAdapter.shutdown();
    }
}
