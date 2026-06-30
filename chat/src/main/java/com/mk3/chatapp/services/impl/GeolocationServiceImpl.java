package com.mk3.chatapp.services.impl;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.mk3.chatapp.services.GeoIpDatabaseService;
import com.mk3.chatapp.services.GeolocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeolocationServiceImpl implements GeolocationService {

    private final GeoIpDatabaseService geoIpDatabaseService;
    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    @Override
    public String determineCountryCode(String ipAddress, String phoneNumber) {
        // 1. Try to determine from Phone Number (Source of Truth)
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            try {
                Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(phoneNumber, null);
                String regionCode = phoneNumberUtil.getRegionCodeForNumber(parsedNumber);
                if (regionCode != null && !regionCode.equals("ZZ")) { // ZZ is returned for unknown regions
                    log.debug("Determined country code {} from phone number", regionCode);
                    return regionCode;
                }
            } catch (NumberParseException e) {
                log.warn("Failed to parse phone number for geolocation: {}", e.getMessage());
            }
        }

        // 2. Fallback to IP Address
        if (ipAddress != null && !ipAddress.isBlank()) {
            try {
                DatabaseReader reader = geoIpDatabaseService.getDatabaseReader();
                if (reader != null) {
                    InetAddress inetAddress = InetAddress.getByName(ipAddress);
                    return reader.country(inetAddress).getCountry().getIsoCode();
                } else {
                    log.warn("GeoIP Database Reader is not available.");
                }
            } catch (IOException | GeoIp2Exception e) {
                log.warn("Failed to determine country from IP address {}: {}", ipAddress, e.getMessage());
            }
        }

        return null;
    }
}
