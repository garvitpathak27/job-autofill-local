package com.jobautofill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobautofill.model.AutofillRequest;
import com.jobautofill.model.AutofillResponse;
import com.jobautofill.model.OllamaRequest;
import com.jobautofill.model.OllamaResponse;
import com.jobautofill.model.StructuredResume;
import com.jobautofill.util.JsonSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.model}")
    private String model;

    @Value("${ollama.timeout}")
    private int timeout;

    public OllamaService(WebClient ollamaWebClient, ObjectMapper objectMapper) {
        this.webClient = ollamaWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts structured JSON from resume text using Ollama.
     */
    public StructuredResume extractStructuredResume(String resumeText) {
        log.info("Starting resume extraction with Ollama (model: {})", model);

        String prompt = buildExtractionPrompt(resumeText);

        OllamaRequest request = new OllamaRequest();
        request.setModel(model);
        request.setStream(false);
        request.setFormat("json");
        request.setMessages(List.of(
                new OllamaRequest.Message("user", prompt)
        ));

        try {
            OllamaResponse response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (response == null || response.getMessage() == null) {
                throw new RuntimeException("Empty response from Ollama");
            }

            String rawJsonContent = response.getMessage().getContent();
            log.debug("Raw Ollama response: {}", rawJsonContent);

            String sanitizedJson = JsonSanitizer.sanitizeOllamaJson(rawJsonContent, objectMapper);
            log.debug("Sanitized JSON: {}", sanitizedJson);

            StructuredResume structuredResume = objectMapper.readValue(sanitizedJson, StructuredResume.class);
            log.info("Successfully extracted structured resume");

            return structuredResume;

        } catch (Exception e) {
            log.error("Failed to extract structured resume", e);
            throw new RuntimeException("Ollama extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Maps a form field to a value from the structured resume.
     */
    public AutofillResponse mapFieldToResumeValue(AutofillRequest fieldRequest, StructuredResume resume) {
        log.info("Mapping field: {} (name: {})", fieldRequest.getFieldLabel(), fieldRequest.getFieldName());

        String prompt = buildAutofillPrompt(fieldRequest, resume);

        OllamaRequest request = new OllamaRequest();
        request.setModel(model);
        request.setStream(false);
        request.setFormat("json");
        request.setMessages(List.of(
                new OllamaRequest.Message("user", prompt)
        ));

        try {
            OllamaResponse response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (response == null || response.getMessage() == null) {
                throw new RuntimeException("Empty response from Ollama");
            }

            String jsonContent = response.getMessage().getContent();
            log.debug("Autofill response: {}", jsonContent);

            AutofillResponse autofillResponse = objectMapper.readValue(jsonContent, AutofillResponse.class);
            log.info("Mapped field to value: {}", autofillResponse.getSuggestedValue());

            return autofillResponse;

        } catch (Exception e) {
            log.error("Failed to map field to resume value", e);
            return new AutofillResponse("", 0.0, "Failed to map field: " + e.getMessage(), null);
        }
    }

    private String buildExtractionPrompt(String resumeText) {
        return """
                You are a resume parser. Extract the following information from the resume text and return ONLY valid JSON.
                
                CRITICAL RULES:
                1. Return ONLY the JSON object, no markdown, no code blocks, no explanation
                2. ALL string fields must be strings (use "" for empty, not arrays)
                3. ALL array fields must be arrays of strings
                4. Use null for missing data, not empty arrays for strings
                
                Required JSON structure (EXACT format):
                {
                  "personal_info": {
                    "name": "string",
                    "email": "string",
                    "phone": "string",
                    "linkedin": "string or null",
                    "github": "string or null"
                  },
                  "education": [
                    {
                      "degree": "string",
                      "institution": "string",
                      "year": "string (not array)",
                      "score": "string or null (not array)",
                      "location": "string or null (not array)"
                    }
                  ],
                  "experience": [
                    {
                      "title": "string",
                      "company": "string",
                      "duration": "string (not array)",
                      "description": "string",
                      "location": "string or null (not array)"
                    }
                  ],
                  "skills": ["string1", "string2", "string3"]
                }
                
                IMPORTANT:
                - "year" must be a STRING like "2025", NOT an array like ["2025"]
                - "score" must be a STRING like "8.40 CGPA", NOT an array like ["8.40"]
                - "duration" must be a STRING like "2023-2024", NOT an array
                - "skills" must be a FLAT array of strings, NOT nested arrays
                
                If a field is not found in the resume, use null (for strings) or [] (for arrays).
                
                Resume text:
                ---
                """ + resumeText + """
                ---
                
                Return ONLY the JSON object following the exact structure above.
                """;
    }

    private String buildAutofillPrompt(AutofillRequest fieldRequest, StructuredResume resume) {
        String resumeJson;
        try {
            resumeJson = objectMapper.writeValueAsString(resume);
        } catch (Exception e) {
            resumeJson = "{}";
        }

        return String.format("""
                You are an intelligent form autofill assistant. Your job is to map a form field to the most appropriate value from a candidate's resume.
                
                FORM FIELD INFORMATION:
                - Label: %s
                - Field name: %s
                - Placeholder: %s
                - Field type: %s
                - Current value: %s
                
                CANDIDATE'S RESUME DATA (JSON):
                %s
                
                INSTRUCTIONS:
                1. Analyze the form field label, name, and placeholder to understand what information is being requested
                2. Find the most relevant data from the resume JSON
                3. Return ONLY a JSON object with this EXACT structure:
                {
                  "suggested_value": "the actual value to fill in the field",
                  "confidence": 0.95,
                  "reasoning": "brief explanation of why this value matches",
                  "field_matched": "resume field name that was used"
                }
                
                RULES:
                - suggested_value must be a STRING (the actual text to fill)
                - confidence must be a NUMBER between 0.0 and 1.0
                - If no good match exists, return suggested_value as empty string "" with confidence 0.0
                - For "Full Name" or "Name" fields, use personal_info.name
                - For "Email" fields, use personal_info.email
                - For "Phone" fields, use personal_info.phone
                - For "Skills" or "Technical Skills" fields, join the skills array with commas
                - For "Experience" or "Work Experience" fields, summarize the experience array
                - For "Education" fields, use the most recent/relevant education entry
                - Match field labels intelligently (e.g., "First Name" should extract first part of name)
                
                Return ONLY the JSON object, no other text.
                """,
                fieldRequest.getFieldLabel() != null ? fieldRequest.getFieldLabel() : "",
                fieldRequest.getFieldName() != null ? fieldRequest.getFieldName() : "",
                fieldRequest.getFieldPlaceholder() != null ? fieldRequest.getFieldPlaceholder() : "",
                fieldRequest.getFieldType() != null ? fieldRequest.getFieldType() : "text",
                fieldRequest.getFieldValueCurrent() != null ? fieldRequest.getFieldValueCurrent() : "",
                resumeJson
        );
    }
}