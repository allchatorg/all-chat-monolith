package com.mk3.chatapp.dtos.requests;

public record DeleteAccountRequest(boolean removeMessages, String password) {
}
