package com.mk3.chatapp.services;

public interface TurnstileService {
    boolean verify(String token, String ip);
}
