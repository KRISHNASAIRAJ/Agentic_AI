package com.smsdetector.reputation;

import com.smsdetector.common.dto.SenderReputation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Sender reputation by hashed sender id. Falls back to a deterministic demo score. */
@Service
public class SenderReputationService {
    private final SenderRepository repository;

    public SenderReputationService(SenderRepository repository) { this.repository = repository; }

    public SenderReputation lookup(String senderHash) {
        return repository.findById(senderHash)
            .map(e -> new SenderReputation(e.getSenderHash(), e.getScore(), e.getLabel(),
                e.getSource(), e.getLastUpdated()))
            .orElseGet(() -> fallback(senderHash));
    }

    public List<SenderReputation> batchLookup(List<String> hashes) {
        return hashes.stream().map(this::lookup).toList();
    }

    @Transactional
    public SenderReputation report(String senderHash, String label, int score) {
        var entity = repository.findById(senderHash).orElseGet(() ->
            new SenderReputationEntity(senderHash, score, label, "community"));
        entity.setScore(score);
        entity.setLabel(label);
        entity.setLastUpdated(System.currentTimeMillis());
        repository.save(entity);
        return new SenderReputation(senderHash, score, label, "community", entity.getLastUpdated());
    }

    private SenderReputation fallback(String hash) {
        if (hash == null || hash.length() < 2) return new SenderReputation(hash, 0, "UNKNOWN", "default", 0);
        int prefix = Integer.parseInt(hash.substring(0, 2), 16);
        if (prefix < 16) return new SenderReputation(hash, 92, "BLOCKED", "seed-list", 0);
        if (prefix < 48) return new SenderReputation(hash, 70, "SUSPICIOUS", "seed-list", 0);
        if (prefix < 80) return new SenderReputation(hash, 20, "PROMOTIONAL", "seed-list", 0);
        return new SenderReputation(hash, 2, "VERIFIED", "seed-list", 0);
    }
}
