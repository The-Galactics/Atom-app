package com.atom.app.model;

import com.google.gson.annotations.SerializedName;

public class ResponseModel {
    
    @SerializedName("response")
    private String responseText;

    @SerializedName("status")
    private String status;

    // Getters and Setters
    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
