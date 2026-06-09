package com.atom.app.repository;

import com.atom.app.model.ResponseModel;
import com.atom.app.network.ApiService;
import com.atom.app.network.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatRepository {
    private ApiService apiService;

    public ChatRepository() {
        apiService = RetrofitClient.getClient().create(ApiService.class);
    }

    public void askAtom(String prompt, final ChatCallback callback) {
        apiService.sendMessage(prompt).enqueue(new Callback<ResponseModel>() {
            @Override
            public void onResponse(Call<ResponseModel> call, Response<ResponseModel> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Error in server response");
                }
            }

            @Override
            public void onFailure(Call<ResponseModel> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public interface ChatCallback {
        void onSuccess(ResponseModel response);
        void onError(String error);
    }
}
