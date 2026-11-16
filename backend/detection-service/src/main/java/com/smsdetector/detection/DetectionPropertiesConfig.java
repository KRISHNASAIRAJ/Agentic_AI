package com.smsdetector.detection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DetectionProperties.class)
public class DetectionPropertiesConfig {}
