package com.jobautofill.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model for autofill operations.
 * Contains the suggested value and metadata about the mapping.
 */
public class AutofillResponse {

    @JsonProperty("suggested_value")
    private String suggestedValue;

    private double confidence;

    private String reasoning;

    @JsonProperty("field_matched")
    private String fieldMatched;

    // Default constructor
    public AutofillResponse() {}

    // Constructor
    public AutofillResponse(String suggestedValue, double confidence, String reasoning, String fieldMatched) {
        this.suggestedValue = suggestedValue;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.fieldMatched = fieldMatched;
    }

    // Getters and Setters
    public String getSuggestedValue() {
        return suggestedValue;
    }

    public void setSuggestedValue(String suggestedValue) {
        this.suggestedValue = suggestedValue;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getFieldMatched() {
        return fieldMatched;
    }

    public void setFieldMatched(String fieldMatched) {
        this.fieldMatched = fieldMatched;
    }
}