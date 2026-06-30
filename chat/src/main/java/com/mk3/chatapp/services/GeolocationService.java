package com.mk3.chatapp.services;

public interface GeolocationService {
    String determineCountryCode(String ipAddress, String phoneNumber);
}
