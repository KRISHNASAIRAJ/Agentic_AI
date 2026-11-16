package com.smsdetector.detection;

import com.smsdetector.common.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Content-free detection: the server only ever sees SHA-256 hashes.
 * Known malformed-hash signatures are matched from PostgreSQL; otherwise a
 * deterministic heuristic derived from the hash prefix is used for the demo.
 */
@Service
public class DetectionEngineService {
    private final SignatureRepository signatures;
    private final DetectionProperties props;

    public DetectionEngineService(SignatureRepository signatures, DetectionProperties props) {
        this.signatures = signatures;
        this.props = props;
    }

    public ClassificationResponse classify(ClassificationRequest request) {
        var known = signatures.findByMessageHash(request.messageHash());
        if (known.isPresent()) {
            Signature s = known.get();
            return new ClassificationResponse(request.messageHash(), s.getVerdict(), s.getSpamScore(),
                s.getCategory(), s.getRuleId(), props.getSignatureVersion());
        }

        if (props.isHashHeuristicsEnabled() && request.messageHash() != null && request.messageHash().length() >= 2) {
            int prefix = Integer.parseInt(request.messageHash().substring(0, 2), 16);
            if (prefix < 12) {
                return new ClassificationResponse(request.messageHash(), "PHISHING", 0.95f,
                    "PHISHING", "hash-prefix-phishing", props.getSignatureVersion());
            }
            if (prefix < 40) {
                return new ClassificationResponse(request.messageHash(), "SPAM", 0.78f,
                    "PROMOTION", "hash-prefix-spam", props.getSignatureVersion());
            }
            if (prefix < 70) {
                return new ClassificationResponse(request.messageHash(), "SUSPICIOUS", 0.55f,
                    "UNKNOWN", "hash-prefix-suspicious", props.getSignatureVersion());
            }
        }
        return new ClassificationResponse(request.messageHash(), "SAFE", 0.02f,
            "PERSONAL", "default", props.getSignatureVersion());
    }

    @Transactional
    public void recordFeedback(FeedbackRequest feedback) {
        signatures.findByMessageHash(feedback.messageHash()).ifPresentOrElse(
            sig -> {
                sig.setVerdict(feedback.correctedVerdict());
                sig.setCategory(feedback.correctedVerdict());
                sig.setSpamScore(feedback.correctedVerdict().equals("SAFE") ? 0f : 0.9f);
                sig.incrementReports();
            },
            () -> signatures.save(new Signature(feedback.messageHash(), feedback.correctedVerdict(),
                feedback.correctedVerdict(), feedback.correctedVerdict().equals("SAFE") ? 0f : 0.9f,
                "user-feedback"))
        );
    }

    public List<Signature> topThreats() {
        return signatures.findAll().stream()
            .sorted((a, b) -> Integer.compare(b.getReports(), a.getReports()))
            .limit(20).toList();
    }

    public long signatureCount() { return signatures.count(); }
}
