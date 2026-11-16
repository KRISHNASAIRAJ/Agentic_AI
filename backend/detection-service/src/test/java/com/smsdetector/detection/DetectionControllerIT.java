package com.smsdetector.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsdetector.common.dto.ClassificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DetectionControllerIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void classifyEndpointReturnsVerdict() throws Exception {
        mvc.perform(post("/api/v1/detection/classify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ClassificationRequest("05abcdef", "h", "1", "en"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("PHISHING"))
            .andExpect(jsonPath("$.signatureVersion").exists());
    }

    @Test
    void healthAndStatsAreExposed() throws Exception {
        mvc.perform(get("/api/v1/detection/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signatureCount").exists());
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void feedbackEndpointAcceptsPayload() throws Exception {
        mvc.perform(post("/api/v1/detection/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageHash\":\"abc\",\"originalVerdict\":\"SAFE\",\"correctedVerdict\":\"SPAM\",\"senderHash\":\"h\",\"timestamp\":1}"))
            .andExpect(status().isAccepted());
    }
}
