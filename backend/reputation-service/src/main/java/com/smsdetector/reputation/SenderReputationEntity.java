package com.smsdetector.reputation;

import jakarta.persistence.*;

@Entity
@Table(name = "sender_reputation")
public class SenderReputationEntity {
    @Id
    @Column(name = "sender_hash", length = 64)
    private String senderHash;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String label;

    private String source = "community";

    @Column(name = "last_updated")
    private long lastUpdated = System.currentTimeMillis();

    public SenderReputationEntity() {}
    public SenderReputationEntity(String senderHash, int score, String label, String source) {
        this.senderHash = senderHash;
        this.score = score;
        this.label = label;
        this.source = source;
    }

    public String getSenderHash() { return senderHash; }
    public int getScore() { return score; }
    public String getLabel() { return label; }
    public String getSource() { return source; }
    public long getLastUpdated() { return lastUpdated; }

    public void setScore(int score) { this.score = score; }
    public void setLabel(String label) { this.label = label; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}
