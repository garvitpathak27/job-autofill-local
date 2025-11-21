package com.jobautofill.model;

import java.time.LocalDateTime;

public class ResumeData {
    private String fileName;
    private String rawText;
    private LocalDateTime uploadedAt;
    private String extractedJson;  // Will store structured JSON from Ollama later

    public ResumeData() {
    }

    public ResumeData(String fileName, String rawText) {
        this.fileName = fileName;
        this.rawText = rawText;
        this.uploadedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getExtractedJson() {
        return extractedJson;
    }

    public void setExtractedJson(String extractedJson) {
        this.extractedJson = extractedJson;
    }
}
