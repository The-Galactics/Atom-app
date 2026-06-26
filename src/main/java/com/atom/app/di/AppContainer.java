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
import com.atom.application.port.out.ContactResolverPortOut;
import com.atom.application.port.out.ExternalInteractionPortOut;
import com.atom.application.port.out.PhoneNumberNormalizerPortOut;
import com.atom.application.port.out.security.DeviceInspectorPort;
import com.atom.application.port.out.security.TokenStore;
import com.atom.application.usecase.ExternalCommandUseCase;
import com.atom.application.usecase.ExternalMessageUseCase;
import com.atom.application.usecase.SynthesizeSpeechUseCase;
import com.atom.application.usecase.security.DeviceSecurityGuard;
import com.atom.application.usecase.security.DeviceSecurityUseCase;
import com.atom.application.usecase.security.InputValidationUsecase;
import com.atom.infrastructure.adapter.action.AndroidActionExecutor;
import com.atom.infrastructure.adapter.grpc.AtomAgentServiceGrpc;
import com.atom.infrastructure.adapter.grpc.AuthGrpcAdapter;
import com.atom.infrastructure.adapter.grpc.GrpcChannelProvider;
import com.atom.infrastructure.adapter.grpc.InteractionGrpcAdapter;
import com.atom.infrastructure.adapter.out.device.ContactsContractResolver;
import com.atom.infrastructure.adapter.out.device.DeviceInspectorAdapter;
import com.atom.infrastructure.adapter.out.security.EncryptedTokenStore;
import com.atom.infrastructure.adapter.out.device.TelephonyE164Normalizer;

public class AppContainer {

    // Infrastructure adapters (out-ports).
    private final InteractionGrpcAdapter interactionGrpcAdapter;
    private final TokenStore tokenStore;
    private final GrpcChannelProvider channelProvider;
    private final DeviceInspectorPort deviceInspectorPort;
    private final ActionExecutorPortOut actionExecutorPortOut;

    // Application use-cases (in-ports).
    private final StreamChatPortIn externalMessageUseCase;
    private final ExecuteCommandPortIn externalCommandUseCase;
    private final SynthesizeSpeechPortIn synthesizeSpeechUseCase;
    private final DeviceSecurityPort deviceSecurityUseCase;
    private final DeviceSecurityGuard deviceSecurityGuard;
    private final InputValidationPort inputValidationUsecase;
    private final com.atom.application.port.in.security.AuthPortIn authUseCase;

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

        // Encrypted store for the session tokens (HU-27).
        this.tokenStore = new EncryptedTokenStore(context);

        // Deferred token supplier: the interceptor is built before AuthUseCase exists,
        // so it reads through a holder we point at AuthUseCase::getValidAccessToken below.
        java.util.concurrent.atomic.AtomicReference<java.util.function.Supplier<String>> tokenSupplierHolder =
                new java.util.concurrent.atomic.AtomicReference<>(() -> null);

        this.channelProvider = new GrpcChannelProvider(
                BuildConfig.GRPC_HOST, BuildConfig.GRPC_PORT,
                () -> tokenSupplierHolder.get().get());

        // Auth gateway over the RAW channel (public RPCs, no Bearer -> no refresh recursion).
        java.util.function.LongSupplier clock = () -> System.currentTimeMillis() / 1000L;
        com.atom.application.port.out.security.AuthGatewayPortOut authGateway =
                new AuthGrpcAdapter(
                        AtomAgentServiceGrpc.newBlockingStub(channelProvider.getRawChannel()),
                        clock);
        // Redirect to Login in real time when a refresh is rejected (US-10.3). The
        // Application implements SessionListener; fall back to a no-op otherwise.
        com.atom.application.port.out.security.SessionListener sessionListener =
                (context instanceof com.atom.application.port.out.security.SessionListener)
                        ? (com.atom.application.port.out.security.SessionListener) context
                        : com.atom.application.port.out.security.SessionListener.NONE;
        com.atom.application.usecase.security.AuthUseCase authUseCaseImpl =
                new com.atom.application.usecase.security.AuthUseCase(
                        authGateway, tokenStore, clock, sessionListener);
        this.authUseCase = authUseCaseImpl;

        // Now point the interceptor at the live refresh-aware supplier.
        tokenSupplierHolder.set(authUseCaseImpl::getValidAccessToken);

        // Protected interaction adapter over the AUTHED channel.
        this.interactionGrpcAdapter = new InteractionGrpcAdapter(channelProvider.getAuthedChannel());
        this.interactionGrpcAdapter.init();
        ExternalInteractionPortOut externalInteractionPortOut = this.interactionGrpcAdapter;

        // On-device action executor (out-port). Needs an Android context.
        // Resolves spoken contact names to numbers via the address book.
        ContactResolverPortOut contactResolver = new ContactsContractResolver(context);
        // Upgrades local numbers to E.164 (region inferred from the device) so the
        // WhatsApp deep link lands on the chat instead of WhatsApp's home screen.
        PhoneNumberNormalizerPortOut phoneNumberNormalizer = new TelephonyE164Normalizer(context);
        this.actionExecutorPortOut =
                new AndroidActionExecutor(context, contactResolver, phoneNumberNormalizer);

        // External interaction use-cases (chat streaming + command execution).
        this.externalMessageUseCase = new ExternalMessageUseCase(externalInteractionPortOut);
        this.externalCommandUseCase = new ExternalCommandUseCase(externalInteractionPortOut);
        this.synthesizeSpeechUseCase = new SynthesizeSpeechUseCase(externalInteractionPortOut);

        // Device security use-case <- device inspector adapter.
        this.deviceInspectorPort = new DeviceInspectorAdapter();
        this.deviceSecurityUseCase = new DeviceSecurityUseCase(deviceInspectorPort);
        // Opaque, fail-closed gate consulted by the protected services before they
        // grant elevated reach. Evaluate the verdict off the main thread now (the
        // probes do disk I/O + exec), so the services read a cached result later.
        this.deviceSecurityGuard = new DeviceSecurityGuard(this.deviceSecurityUseCase);
        Thread securityEval = new Thread(deviceSecurityGuard::refresh, "atom-security-eval");
        securityEval.setDaemon(true);
        securityEval.start();

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

    public DeviceSecurityGuard getDeviceSecurityGuard() {
        return deviceSecurityGuard;
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

    /** Secure store for the session tokens; the login flow saves the pair here. */
    public TokenStore getTokenStore() {
        return tokenStore;
    }

    /** Auth use-case: register, login, refresh, logout. */
    public com.atom.application.port.in.security.AuthPortIn getAuthUseCase() {
        return authUseCase;
    }

    /** Releases process-scoped resources. Call once on application teardown. */
    public void shutdown() {
        this.channelProvider.shutdown();
    }
}
