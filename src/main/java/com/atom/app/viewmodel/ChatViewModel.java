package com.atom.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.atom.app.data.ConversationRepository;
import com.atom.app.model.ResponseModel;
import com.atom.app.overlay.OperatingCueBus;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;
import com.atom.domain.action.DestructiveActionPolicy;
import com.atom.domain.action.ResolvedAction;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class ChatViewModel extends ViewModel {
    private final ChatRepository repository;
    private final CommandRepository commandRepository;
    // Bridges this path's autonomous loop to the overlay's cross-app operating cue.
    private final OperatingCueBus operatingCueBus;
    // Reports whether Atom's accessibility service is enabled. Kept as a
    // supplier so the ViewModel stays free of an Android Context and unit-testable.
    private final BooleanSupplier accessibilityEnabled;
    // Transcript store. Turns are persisted here, at the point each backend event
    // actually occurs, rather than in a UI observer — a retained LiveData replays
    // its last value to every new observer, so persisting on the UI side would
    // re-insert the last turn on each Activity re-creation (rotation, theme, and
    // notably the locale switch that recreates Activities). Persisting at the
    // event source makes each turn save exactly once.
    private final ConversationRepository conversationRepository;
    private MutableLiveData<String> chatResponse = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    // Emitted when an action needs the accessibility service but it is disabled.
    private MutableLiveData<Event<ResolvedAction>> accessibilityRequired = new MutableLiveData<>();
    // One-shot signal that the session is no longer valid: a pre-flight recognize()
    // RPC came back gRPC UNAUTHENTICATED (the access/refresh token is rejected). The
    // Activity observes this and redirects to Login instead of letting the dead
    // session surface as a transient, auto-recovering error. This funnels into the
    // same secure re-login pipeline as AtomApp.onSessionExpired() (SessionListener).
    private MutableLiveData<Event<Boolean>> sessionExpired = new MutableLiveData<>();
    // True while the loop runs; the UI freezes input and shows progress until it ends.
    private MutableLiveData<Boolean> automationActive = new MutableLiveData<>(false);
    // A spoken confirmation question to voice + capture; one-shot Event. The Activity
    // speaks it, re-opens the mic, and feeds the transcript via submitSpokenConfirmation.
    private MutableLiveData<Event<String>> voiceConfirmationRequested = new MutableLiveData<>();
    // One-shot signal that an autonomous chain COMPLETED (not aborted). The Activity uses
    // it to confirm the finish tangibly — a distinct success sub-label + a confirmation
    // haptic — so the user knows the task is done even when not watching the orb. Separate
    // from chatResponse (which also fires for ordinary chat replies) so only real task
    // completions get the affordance, and from the OperatingCueBus (whose one-shot Events
    // the overlay service already consumes) to avoid a second Event consumer.
    private MutableLiveData<Event<String>> taskCompleted = new MutableLiveData<>();
    // Hands the spoken reply back to the blocked loop thread (capacity 1).
    private final BlockingQueue<String> spokenConfirmation = new ArrayBlockingQueue<>(1);
    // How long the loop waits for the spoken reply before treating it as no answer.
    private static final long CONFIRM_TIMEOUT_SECONDS = 12L;
    // Sentinel the Activity submits when it CANNOT capture (mic muted / not granted);
    // ask() maps it to null so the loop aborts immediately instead of re-asking.
    public static final String CANT_ASK = " __atom_cant_ask__";
    // True only while an Activity is started and observing the confirmation event, so
    // ask() can abort (null) immediately when there's no UI to voice the question.
    private volatile boolean confirmationUiReady;

    public ChatViewModel(ChatRepository repository, CommandRepository commandRepository,
                         ConversationRepository conversationRepository,
                         BooleanSupplier accessibilityEnabled,
                         OperatingCueBus operatingCueBus) {
        this.repository = repository;
        this.commandRepository = commandRepository;
        this.conversationRepository = conversationRepository;
        this.accessibilityEnabled = accessibilityEnabled;
        this.operatingCueBus = operatingCueBus;
        // The backend holds sensitive actions and asks out loud mid-loop; this gate
        // speaks the question and captures the spoken "sí/no" (hands-free).
        commandRepository.setConfirmationGate(new VoiceConfirmationGate());
    }

    public LiveData<String> getChatResponse() { return chatResponse; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Event<ResolvedAction>> getAccessibilityRequired() { return accessibilityRequired; }
    public LiveData<Event<Boolean>> getSessionExpired() { return sessionExpired; }
    public LiveData<Boolean> getAutomationActive() { return automationActive; }
    public LiveData<Event<String>> getVoiceConfirmationRequested() { return voiceConfirmationRequested; }
    public LiveData<Event<String>> getTaskCompleted() { return taskCompleted; }

    /** Free-form conversational message (token-streamed via StreamChat). */
    public void sendMessage(String prompt) {
        isLoading.setValue(true);
        repository.askAtom(prompt, new ChatRepository.ChatCallback() {
            @Override
            public void onSuccess(ResponseModel response) {
                isLoading.setValue(false);
                // Record Atom's conversational reply once, as it arrives.
                conversationRepository.saveAssistantMessage(response.getResponseText());
                chatResponse.setValue(response.getResponseText());
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    /**
     * Treat the input as an ORDER: recognize the first intent, then either route a
     * conversational turn to StreamChat, prompt to enable accessibility, ask for
     * confirmation, or kick off the autonomous multi-step loop.
     */
    public void sendOrder(String order) {
        // Record the user turn here, at the single entry point for typed and spoken
        // orders, so it persists exactly once regardless of how the order resolves.
        conversationRepository.saveUserMessage(order);
        isLoading.setValue(true);
        commandRepository.recognize(order, new CommandRepository.CommandCallback() {
            @Override
            public void onResolved(ResolvedAction action) {
                if (!action.isExecutable() && !action.awaitingConfirmation()) {
                    // Not a command -> conversational chat path (StreamChat) keeps
                    // in-session context. isLoading stays true until it returns.
                    // A held sensitive action arrives as a NONE turn with
                    // awaitingConfirmation set; that is still an order, so it must
                    // fall through to the autonomous loop (which speaks the question
                    // and captures the spoken sí/no), NOT the chat path.
                    sendMessage(order);
                    return;
                }
                isLoading.setValue(false);
                // Accessibility-powered actions can't run until the user enables
                // the service: prompt for that before starting the loop.
                if (PermissionCoordinator.requiresAccessibility(action)
                        && !accessibilityEnabled.getAsBoolean()) {
                    accessibilityRequired.setValue(new Event<>(action));
                    return;
                }
                // Sensitive actions are no longer pre-confirmed by a tap dialog: the
                // backend holds them and asks out loud mid-loop (voice gate).
                runAutonomous(order);
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }

            @Override
            public void onError(String error, Throwable cause) {
                isLoading.setValue(false);
                if (isUnauthenticated(cause)) {
                    // Dead session: don't swallow it as a transient error. Surface a
                    // one-shot event so the UI redirects to Login (secure pipeline).
                    sessionExpired.setValue(new Event<>(Boolean.TRUE));
                } else {
                    // Non-auth failures keep the existing generic error behavior.
                    errorMessage.setValue(error);
                }
            }
        });
    }

    /**
     * True when {@code cause} (or any throwable it wraps) is a gRPC call that failed
     * with {@link Status.Code#UNAUTHENTICATED} — i.e. the access/refresh token is no
     * longer accepted and the user must re-authenticate. Walks the cause chain so a
     * status wrapped by an intermediate layer is still detected.
     */
    private static boolean isUnauthenticated(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof StatusRuntimeException) {
                return ((StatusRuntimeException) t).getStatus().getCode()
                        == Status.Code.UNAUTHENTICATED;
            }
        }
        return false;
    }

    /**
     * Runs the autonomous loop for an order, freezing the input and showing the
     * operating indicator until the chain completes or aborts. Used after the
     * user confirms a sensitive first action, or directly for hands-free ones.
     */
    public void runAutonomous(String order) {
        automationActive.setValue(true);
        commandRepository.executeAutonomous(order, new CommandRepository.AutomationCallback() {
            @Override
            public void onActionStarted(ResolvedAction action, int step) {
                // Bridge to the overlay's cross-app cue (pulsing handle + live notification).
                // The on-screen core (driven by automationActive) still covers the foreground.
                operatingCueBus.started(action != null ? action.type() : null, step);
            }

            @Override
            public void onComplete(String finalMessage) {
                automationActive.setValue(false);
                operatingCueBus.finished(finalMessage, false);
                conversationRepository.saveAssistantMessage(finalMessage);
                chatResponse.setValue(finalMessage);
                // Fire AFTER chatResponse so the Activity's success affordance (sub-label +
                // haptic) lands on top of the ordinary showResponse render, not before it.
                taskCompleted.setValue(new Event<>(finalMessage));
            }

            @Override
            public void onAborted(String message) {
                automationActive.setValue(false);
                operatingCueBus.finished(message, true);
                if (message != null && !message.trim().isEmpty()) {
                    errorMessage.setValue(message);
                }
            }
        });
    }

    /**
     * The Activity captured the user's spoken reply to a confirmation question: hand
     * the transcript to the blocked loop thread so it resumes (resends it as the next
     * command). An empty string means "no answer" (the loop re-asks or aborts).
     */
    public void submitSpokenConfirmation(String transcript) {
        spokenConfirmation.offer(transcript == null ? "" : transcript);
    }

    /** The Activity tells the ViewModel whether it can currently voice + capture a reply. */
    public void setConfirmationUiReady(boolean ready) {
        this.confirmationUiReady = ready;
        if (!ready) {
            // Unblock a loop waiting on a reply we can no longer capture.
            spokenConfirmation.offer(CANT_ASK);
        }
    }

    /**
     * Voice gate: for a held/destructive action the loop calls {@link #ask}, which asks
     * the Activity (via {@link #voiceConfirmationRequested}) to speak the question and
     * re-open the mic, then blocks on a bounded poll for the spoken reply. The timeout
     * guarantees no deadlock if no Activity is observing (returns null ⇒ loop aborts).
     */
    private final class VoiceConfirmationGate implements CommandRepository.ConfirmationGate {
        private final DestructiveActionPolicy policy = new DestructiveActionPolicy();

        @Override
        public boolean requiresConfirmation(ResolvedAction action) {
            return policy.requiresConfirmation(action);
        }

        @Override
        public String ask(String question) {
            if (!confirmationUiReady) {
                return null;  // no started Activity to voice the question -> abort, no re-ask
            }
            spokenConfirmation.clear();
            voiceConfirmationRequested.postValue(new Event<>(question));
            try {
                String reply = spokenConfirmation.poll(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (reply == null) {
                    return "";            // asked, heard nothing in time
                }
                if (CANT_ASK.equals(reply)) {
                    return null;          // Activity couldn't capture (mute/no-perm/stopped)
                }
                return reply;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
