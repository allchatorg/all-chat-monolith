package com.mk3.chatapp.vip;

/** Only verified Stripe identifiers cross the asynchronous reporting boundary. */
public record VipPaymentReportingEvent(String type, String objectId, String chargeId) { }
