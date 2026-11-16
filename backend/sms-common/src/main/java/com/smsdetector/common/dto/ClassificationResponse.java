package com.smsdetector.common.dto;

public record ClassificationResponse(
    String messageHash,
    String verdict,          // SAFE, SUSPICIOUS, SPAM, PHISHING
    float spamScore,
    String category,
    String matchedRuleId,
    int signatureVersion
) {}
