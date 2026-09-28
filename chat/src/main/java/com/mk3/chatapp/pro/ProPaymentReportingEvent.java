package com.mk3.chatapp.pro;

/** Only verified Stripe identifiers cross the asynchronous reporting boundary. */
public record ProPaymentReportingEvent(String type, String objectId, String chargeId) { }
