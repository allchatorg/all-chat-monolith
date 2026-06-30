package com.mk3.chatapp.services;

public interface SmsSenderService {
    void sendSMS(String number, String message);

    String normalizePhoneNumber(String phoneNumber);
}
