package com.example.adsportalbe.services.impl;

import com.mk3.chatapp.configs.TwilioConfig;
import com.example.adsportalbe.exceptions.ConflictException;
import com.example.adsportalbe.services.SmsSenderService;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsSenderServiceImpl implements SmsSenderService {

    private final TwilioConfig twilioConfig;

    @Override
    public void sendSMS(String number, String message) {
        try {
            Message created = Message.creator(
                            new PhoneNumber(number),
                            twilioConfig.getMessagingServiceSid(),
                            message)
                    .create();

            log.info("SMS sent to {} with sid={}", number, created.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", number, e.getMessage(), e);
        }
    }

    @Override
    public String normalizePhoneNumber(String phoneNumber) {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

        try {
            Phonenumber.PhoneNumber parsedNumber = phoneUtil.parse(phoneNumber, null);

            if (!phoneUtil.isValidNumber(parsedNumber)) {
                throw new ConflictException("Invalid phone number format");
            }

            return phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new ConflictException("Invalid phone number: " + e.getMessage());
        }
    }
}
