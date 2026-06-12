package com.atom.app.network;

import com.atom.app.model.ResponseModel;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    
    // Example endpoint to talk with the assistant
    @GET("api/chat")
    Call<ResponseModel> sendMessage(@Query("prompt") String prompt);
    
    // You can add the endpoints defined by your Maven team here
}
