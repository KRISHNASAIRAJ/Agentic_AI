package com.smsdetector.common.dto;

public record SenderReputation(
    String senderHash,
    int score,               // 0 (trusted) .. 100 (blocked)
    String label,            // VERIFIED, BANK, PROMOTIONAL, SUSPICIOUS, BLOCKED
    String source,
    long lastUpdated
) {}
