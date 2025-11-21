package com.jobautofill.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request model for autofill operations.
 * Represents a form field that needs to be filled from resume data.
 */
public class AutofillRequest {

    @JsonProperty("field_label")
    private String fieldLabel;

    @JsonProperty("field_name")
    private String fieldName;

    @JsonProperty("field_placeholder")
    private String fieldPlaceholder;

    @JsonProperty("field_type")
    private String fieldType;

    @JsonProperty("field_value_current")
    private String fieldValueCurrent;

    // Default constructor
    public AutofillRequest() {}

    // Constructor
    public AutofillRequest(String fieldLabel, String fieldName, String fieldPlaceholder,
                          String fieldType, String fieldValueCurrent) {
        this.fieldLabel = fieldLabel;
        this.fieldName = fieldName;
        this.fieldPlaceholder = fieldPlaceholder;
        this.fieldType = fieldType;
        this.fieldValueCurrent = fieldValueCurrent;
    }

    // Getters and Setters
    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldPlaceholder() {
        return fieldPlaceholder;
    }

    public void setFieldPlaceholder(String fieldPlaceholder) {
        this.fieldPlaceholder = fieldPlaceholder;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getFieldValueCurrent() {
        return fieldValueCurrent;
    }

    public void setFieldValueCurrent(String fieldValueCurrent) {
        this.fieldValueCurrent = fieldValueCurrent;
    }
}