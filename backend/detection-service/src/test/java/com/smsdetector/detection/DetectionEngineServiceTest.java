package com.smsdetector.detection;

import com.smsdetector.common.dto.ClassificationRequest;
import com.smsdetector.common.dto.ClassificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DetectionEngineServiceTest {
    @Mock SignatureRepository signatures;
    DetectionEngineService service;

    @BeforeEach
    void setUp() {
        service = new DetectionEngineService(signatures, new DetectionProperties());
    }

    @Test
    void knownSignatureIsReturned() {
        Signature sig = new Signature("abc123", "SPAM", "PROMOTION", 0.8f, "rule-1");
        when(signatures.findByMessageHash("abc123")).thenReturn(Optional.of(sig));

        ClassificationResponse res = service.classify(new ClassificationRequest("abc123", "hash", "1", "en"));

        assertEquals("SPAM", res.verdict());
        assertEquals("rule-1", res.matchedRuleId());
    }

    @Test
    void unknownHashFallsBackToHeuristic() {
        when(signatures.findByMessageHash(anyString())).thenReturn(Optional.empty());
        ClassificationResponse res = service.classify(new ClassificationRequest("05abcdef", "h", "1", "en"));
        assertEquals("PHISHING", res.verdict());
        assertEquals(0.95f, res.spamScore());

        ClassificationResponse safe = service.classify(new ClassificationRequest("ffabcdef", "h", "1", "en"));
        assertEquals("SAFE", safe.verdict());
    }

    @Test
    void feedbackCreatesNewSignature() {
        when(signatures.findByMessageHash(anyString())).thenReturn(Optional.empty());
        service.recordFeedback(new com.smsdetector.common.dto.FeedbackRequest("deadbeef", "SAFE", "SPAM", "h", 1L));
        verify(signatures).save(any(Signature.class));
    }
}
