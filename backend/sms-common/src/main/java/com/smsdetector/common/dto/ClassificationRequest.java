package com.smsdetector.common.dto;

/** Content-free classification request: only a SHA-256 hash of the message is sent. */
public record ClassificationRequest(
    String messageHash,
    String senderHash,
    String clientRuleVersion,
    String locale
) {}
