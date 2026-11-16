package com.smsdetector.detection;

import com.smsdetector.common.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/detection")
public class DetectionController {
    private final DetectionEngineService service;
    private final DetectionProperties props;

    public DetectionController(DetectionEngineService service, DetectionProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/classify")
    public ClassificationResponse classify(@Valid @RequestBody ClassificationRequest request) {
        return service.classify(request);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@Valid @RequestBody FeedbackRequest request) {
        service.recordFeedback(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/signatures")
    public List<Signature> topThreats() { return service.topThreats(); }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
            "signatureCount", service.signatureCount(),
            "signatureVersion", props.getSignatureVersion()
        );
    }
}
