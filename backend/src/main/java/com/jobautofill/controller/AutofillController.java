package com.jobautofill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobautofill.model.AutofillRequest;
import com.jobautofill.model.AutofillResponse;
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
@RequestMapping("/api/autofill")
public class AutofillController {

    private static final Logger log = LoggerFactory.getLogger(AutofillController.class);

    private final OllamaService ollamaService;
    private final ResumeStorage resumeStorage;
    private final ObjectMapper objectMapper;

    public AutofillController(OllamaService ollamaService, 
                              ResumeStorage resumeStorage,
                              ObjectMapper objectMapper) {
        this.ollamaService = ollamaService;
        this.resumeStorage = resumeStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/autofill
     * Maps a form field to a value from the stored resume.
     */
    @PostMapping
    public ResponseEntity<AutofillResponse> autofillField(@RequestBody AutofillRequest request) {
        
        try {
            // Check if resume exists
            if (!resumeStorage.hasResume()) {
                AutofillResponse errorResponse = new AutofillResponse(
                    "", 0.0, "No resume uploaded", null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Check if extraction exists
            ResumeData resumeData = resumeStorage.get();
            String extractedJson = resumeData.getExtractedJson();
            
            if (extractedJson == null) {
                AutofillResponse errorResponse = new AutofillResponse(
                    "", 0.0, "Resume not extracted. Call POST /api/extract first.", null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Parse structured resume
            StructuredResume structuredResume = objectMapper.readValue(extractedJson, StructuredResume.class);

            // Map field to resume value using Ollama
            AutofillResponse response = ollamaService.mapFieldToResumeValue(request, structuredResume);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Autofill failed", e);
            AutofillResponse errorResponse = new AutofillResponse(
                "", 0.0, "Autofill failed: " + e.getMessage(), null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * POST /api/autofill/batch
     * Autofills multiple fields at once (for efficiency).
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, AutofillResponse>> autofillBatch(
            @RequestBody Map<String, AutofillRequest> fields) {
        
        Map<String, AutofillResponse> responses = new HashMap<>();

        try {
            if (!resumeStorage.hasResume()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responses);
            }

            ResumeData resumeData = resumeStorage.get();
            String extractedJson = resumeData.getExtractedJson();
            
            if (extractedJson == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responses);
            }

            StructuredResume structuredResume = objectMapper.readValue(extractedJson, StructuredResume.class);

            // Process each field
            for (Map.Entry<String, AutofillRequest> entry : fields.entrySet()) {
                String fieldId = entry.getKey();
                AutofillRequest fieldRequest = entry.getValue();
                
                try {
                    AutofillResponse response = ollamaService.mapFieldToResumeValue(fieldRequest, structuredResume);
                    responses.put(fieldId, response);
                } catch (Exception e) {
                    log.error("Failed to autofill field {}", fieldId, e);
                    responses.put(fieldId, new AutofillResponse("", 0.0, "Failed: " + e.getMessage(), null));
                }
            }

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Batch autofill failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responses);
        }
    }
}