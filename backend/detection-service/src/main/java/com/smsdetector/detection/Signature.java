package com.smsdetector.detection;

import jakarta.persistence.*;

/** Hash-only threat signature. No SMS body is ever stored on the server. */
@Entity
@Table(name = "signatures")
public class Signature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_hash", nullable = false, unique = true, length = 64)
    private String messageHash;

    @Column(name = "verdict", nullable = false)
    private String verdict;

    @Column(name = "category")
    private String category;

    @Column(name = "spam_score")
    private float spamScore;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "reports")
    private int reports = 1;

    @Column(name = "created_at")
    private long createdAt = System.currentTimeMillis();

    public Signature() {}
    public Signature(String messageHash, String verdict, String category, float spamScore, String ruleId) {
        this.messageHash = messageHash;
        this.verdict = verdict;
        this.category = category;
        this.spamScore = spamScore;
        this.ruleId = ruleId;
    }

    public Long getId() { return id; }
    public String getMessageHash() { return messageHash; }
    public String getVerdict() { return verdict; }
    public String getCategory() { return category; }
    public float getSpamScore() { return spamScore; }
    public String getRuleId() { return ruleId; }
    public int getReports() { return reports; }
    public long getCreatedAt() { return createdAt; }

    public void setVerdict(String verdict) { this.verdict = verdict; }
    public void setCategory(String category) { this.category = category; }
    public void setSpamScore(float spamScore) { this.spamScore = spamScore; }
    public void incrementReports() { this.reports++; }
}
