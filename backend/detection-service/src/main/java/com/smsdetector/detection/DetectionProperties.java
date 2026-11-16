package com.smsdetector.detection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "detection")
public class DetectionProperties {
    private int signatureVersion = 7;
    private boolean hashHeuristicsEnabled = true;

    public int getSignatureVersion() { return signatureVersion; }
    public void setSignatureVersion(int signatureVersion) { this.signatureVersion = signatureVersion; }
    public boolean isHashHeuristicsEnabled() { return hashHeuristicsEnabled; }
    public void setHashHeuristicsEnabled(boolean hashHeuristicsEnabled) { this.hashHeuristicsEnabled = hashHeuristicsEnabled; }
}
