package com.smsdetector.reputation;

import com.smsdetector.common.dto.SenderReputation;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reputation")
public class ReputationController {
    private final SenderReputationService service;

    public ReputationController(SenderReputationService service) { this.service = service; }

    @GetMapping("/{senderHash}")
    public SenderReputation lookup(@PathVariable String senderHash) {
        return service.lookup(senderHash);
    }

    @PostMapping("/batch")
    public List<SenderReputation> batch(@RequestBody Map<String, List<String>> body) {
        return service.batchLookup(body.getOrDefault("hashes", List.of()));
    }

    @PostMapping("/report")
    public SenderReputation report(@RequestBody Map<String, Object> body) {
        return service.report(
            String.valueOf(body.get("senderHash")),
            String.valueOf(body.getOrDefault("label", "BLOCKED")),
            Integer.parseInt(String.valueOf(body.getOrDefault("score", 90)))
        );
    }
}
