package com.atom.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.atom.app.data.ConversationRepository;
import com.atom.app.model.ResponseModel;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;
import com.atom.domain.action.DestructiveActionPolicy;
import com.atom.domain.action.ResolvedAction;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

public class ChatViewModel extends ViewModel {
    private final ChatRepository repository;
    private final CommandRepository commandRepository;
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
    // Sensitive actions awaiting confirmation; one-shot Event avoids re-firing on recreation.
    private MutableLiveData<Event<ResolvedAction>> pendingConfirmation = new MutableLiveData<>();
    // Emitted when an action needs the accessibility service but it is disabled.
    private MutableLiveData<Event<ResolvedAction>> accessibilityRequired = new MutableLiveData<>();
    // True while the loop runs; the UI freezes input and shows progress until it ends.
    private MutableLiveData<Boolean> automationActive = new MutableLiveData<>(false);
    // A destructive action detected mid-loop, awaiting the user's confirm/decline.
    private MutableLiveData<Event<ResolvedAction>> destructiveConfirmation = new MutableLiveData<>();
    // The order currently being recognized, so a confirmed sensitive first action
    // can resume the autonomous loop with the original utterance.
    private String lastOrder;
    // Hands the user's confirm/decline back to the blocked loop thread (capacity 1).
    private final BlockingQueue<Boolean> destructiveAnswer = new ArrayBlockingQueue<>(1);

    public ChatViewModel(ChatRepository repository, CommandRepository commandRepository,
                         ConversationRepository conversationRepository,
                         BooleanSupplier accessibilityEnabled) {
        this.repository = repository;
        this.commandRepository = commandRepository;
        this.conversationRepository = conversationRepository;
        this.accessibilityEnabled = accessibilityEnabled;
        // Wire a prompting gate so a destructive TAP_ELEMENT detected mid-loop pauses
        // the chain and surfaces the confirmation UI (resume on confirm, abort on decline).
        commandRepository.setConfirmationGate(new DestructiveConfirmationGate());
    }

    public LiveData<String> getChatResponse() { return chatResponse; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Event<ResolvedAction>> getPendingConfirmation() { return pendingConfirmation; }
    public LiveData<Event<ResolvedAction>> getAccessibilityRequired() { return accessibilityRequired; }
    public LiveData<Boolean> getAutomationActive() { return automationActive; }
    public LiveData<Event<ResolvedAction>> getDestructiveConfirmation() { return destructiveConfirmation; }

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
        this.lastOrder = order;
        isLoading.setValue(true);
        commandRepository.recognize(order, new CommandRepository.CommandCallback() {
            @Override
            public void onResolved(ResolvedAction action) {
                if (!action.isExecutable()) {
                    // Not a command -> conversational chat path (StreamChat) keeps
                    // in-session context. isLoading stays true until it returns.
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
                if (action.requiresConfirmation()) {
                    pendingConfirmation.setValue(new Event<>(action));
                } else {
                    runAutonomous(order);
                }
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
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
                // Per-step progress; the boolean state already drives the indicator.
            }

            @Override
            public void onComplete(String finalMessage) {
                automationActive.setValue(false);
                conversationRepository.saveAssistantMessage(finalMessage);
                chatResponse.setValue(finalMessage);
            }

            @Override
            public void onAborted(String message) {
                automationActive.setValue(false);
                if (message != null && !message.trim().isEmpty()) {
                    errorMessage.setValue(message);
                }
            }
        });
    }

    /**
     * Resume after the user confirmed a sensitive first action (or granted its
     * runtime permission): drive the autonomous loop with the original order.
     */
    public void runAction(ResolvedAction action) {
        runAutonomous(lastOrder != null ? lastOrder : action.outMessage());
    }

    /**
     * The user answered the mid-loop destructive prompt: hand the decision to the
     * blocked loop thread so it resumes (confirm) or aborts (decline).
     */
    public void resolveDestructiveConfirmation(boolean confirmed) {
        destructiveAnswer.offer(confirmed);
    }

    /** Prompting gate: detects via {@link DestructiveActionPolicy}, then blocks the loop thread until the user answers. */
    private final class DestructiveConfirmationGate implements CommandRepository.ConfirmationGate {
        private final DestructiveActionPolicy policy = new DestructiveActionPolicy();

        @Override
        public boolean requiresConfirmation(ResolvedAction action) {
            return policy.requiresConfirmation(action);
        }

        @Override
        public boolean confirm(ResolvedAction action) {
            destructiveAnswer.clear();
            destructiveConfirmation.postValue(new Event<>(action));
            try {
                return destructiveAnswer.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false; // interrupted -> treat as declined, abort the chain
            }
        }
    }
}
