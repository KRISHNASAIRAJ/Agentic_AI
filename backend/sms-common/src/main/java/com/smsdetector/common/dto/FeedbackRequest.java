package com.smsdetector.common.dto;

/** Privacy-safe feedback: hashed message id plus the user's corrected label. */
public record FeedbackRequest(
    String messageHash,
    String originalVerdict,
    String correctedVerdict,
    String senderHash,
    long timestamp
) {}
