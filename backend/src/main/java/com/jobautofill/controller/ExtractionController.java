package com.jobautofill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobautofill.model.ResumeData;
import com.jobautofill.model.StructuredResume;
import com.jobautofill.service.OllamaService;
import com.jobautofill.storage.ResumeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/extract")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final OllamaService ollamaService;
    private final ResumeStorage resumeStorage;
    private final ObjectMapper objectMapper;

    public ExtractionController(OllamaService ollamaService, 
                               ResumeStorage resumeStorage,
                               ObjectMapper objectMapper) {
        this.ollamaService = ollamaService;
        this.resumeStorage = resumeStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/extract
     * Extracts structured JSON from the currently stored resume.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> extractResume() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if resume exists
            if (!resumeStorage.hasResume()) {
                response.put("error", "No resume uploaded. Upload a resume first.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            ResumeData resumeData = resumeStorage.get();
            String resumeText = resumeData.getRawText();

            log.info("Starting extraction for resume: {}", resumeData.getFileName());

            // Call Ollama to extract structured data
            StructuredResume structuredResume = ollamaService.extractStructuredResume(resumeText);

            // Store the extracted JSON back in ResumeData
            String extractedJson = objectMapper.writeValueAsString(structuredResume);
            resumeData.setExtractedJson(extractedJson);
            resumeStorage.store(resumeData);

            log.info("Extraction complete and stored");

            // Return the structured resume
            response.put("success", true);
            response.put("structured_resume", structuredResume);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Extraction failed", e);
            response.put("error", "Extraction failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/extract/current
     * Returns the cached structured resume (if available).
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentExtraction() {
        Map<String, Object> response = new HashMap<>();

        if (!resumeStorage.hasResume()) {
            response.put("error", "No resume uploaded");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        ResumeData resumeData = resumeStorage.get();
        String extractedJson = resumeData.getExtractedJson();

        if (extractedJson == null) {
            response.put("error", "Resume not extracted yet. Call POST /api/extract first.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        try {
            StructuredResume structuredResume = objectMapper.readValue(extractedJson, StructuredResume.class);
            response.put("success", true);
            response.put("structured_resume", structuredResume);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to parse stored extraction");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
