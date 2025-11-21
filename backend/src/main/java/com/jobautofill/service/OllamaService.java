package com.jobautofill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        request.setFormat("json");  // Force JSON output
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

            // SANITIZE JSON to handle inconsistent data types
            String sanitizedJson = JsonSanitizer.sanitizeOllamaJson(rawJsonContent, objectMapper);
            log.debug("Sanitized JSON: {}", sanitizedJson);

            // Parse JSON into StructuredResume
            StructuredResume structuredResume = objectMapper.readValue(sanitizedJson, StructuredResume.class);
            log.info("Successfully extracted structured resume");

            return structuredResume;

        } catch (Exception e) {
            log.error("Failed to extract structured resume", e);
            throw new RuntimeException("Ollama extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the prompt for resume extraction.
     * UPDATED: More explicit about data types.
     */
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
}