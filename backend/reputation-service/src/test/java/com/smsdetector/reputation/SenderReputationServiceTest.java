package com.smsdetector.reputation;

import com.smsdetector.common.dto.SenderReputation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SenderReputationServiceTest {
    @Mock SenderRepository repository;

    @Test
    void unknownSenderGetsDeterministicFallback() {
        SenderReputationService service = new SenderReputationService(repository);
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        SenderReputation blocked = service.lookup("05abcdef");
        assertEquals("BLOCKED", blocked.label());
        assertEquals(92, blocked.score());

        SenderReputation verified = service.lookup("ffabcdef");
        assertEquals("VERIFIED", verified.label());
    }

    @Test
    void reportPersistsNewEntry() {
        SenderReputationService service = new SenderReputationService(repository);
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        SenderReputation result = service.report("abc123", "BLOCKED", 95);
        assertEquals("BLOCKED", result.label());
        verify(repository).save(any(SenderReputationEntity.class));
    }
}
