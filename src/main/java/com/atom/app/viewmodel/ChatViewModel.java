package com.atom.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.atom.app.model.ResponseModel;
import com.atom.app.repository.ChatRepository;

public class ChatViewModel extends ViewModel {
    private ChatRepository repository;
    private MutableLiveData<String> chatResponse = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public ChatViewModel() {
        repository = new ChatRepository();
    }

    public LiveData<String> getChatResponse() { return chatResponse; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void sendMessage(String prompt) {
        isLoading.setValue(true);
        repository.askAtom(prompt, new ChatRepository.ChatCallback() {
            @Override
            public void onSuccess(ResponseModel response) {
                isLoading.setValue(false);
                chatResponse.setValue(response.getResponseText());
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }
}
