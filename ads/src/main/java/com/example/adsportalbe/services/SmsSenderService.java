package com.example.adsportalbe.services;

public interface SmsSenderService {

    void sendSMS(String number, String message);

    String normalizePhoneNumber(String phoneNumber);
}
