package com.jobautofill.controller;

import com.jobautofill.model.ResumeData;
import com.jobautofill.service.ResumeParserService;
import com.jobautofill.storage.ResumeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeParserService parserService;
    private final ResumeStorage resumeStorage;

    public ResumeController(ResumeParserService parserService, ResumeStorage resumeStorage) {
        this.parserService = parserService;
        this.resumeStorage = resumeStorage;
    }

    /**
     * POST /api/resume/upload
     * Accepts a PDF file, extracts text, stores in memory.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadResume(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file is present
            if (file.isEmpty()) {
                response.put("error", "No file provided");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate file is PDF
            if (!parserService.isPdf(file)) {
                response.put("error", "File must be a PDF");
                return ResponseEntity.badRequest().body(response);
            }

            // Extract text from PDF
            String extractedText = parserService.extractTextFromPdf(file);

            // Store in memory
            ResumeData resumeData = new ResumeData(file.getOriginalFilename(), extractedText);
            resumeStorage.store(resumeData);

            log.info("Resume uploaded and stored: {}", file.getOriginalFilename());

            // Return success response
            response.put("success", true);
            response.put("fileName", file.getOriginalFilename());
            response.put("textLength", extractedText.length());
            response.put("preview", extractedText.substring(0, Math.min(200, extractedText.length())) + "...");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing resume upload", e);
            response.put("error", "Failed to process resume: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * GET /api/resume/current
     * Returns the currently stored resume data.
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentResume() {
        Map<String, Object> response = new HashMap<>();

        if (!resumeStorage.hasResume()) {
            response.put("error", "No resume uploaded yet");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        ResumeData resumeData = resumeStorage.get();
        response.put("fileName", resumeData.getFileName());
        response.put("uploadedAt", resumeData.getUploadedAt().toString());
        response.put("textLength", resumeData.getRawText().length());
        response.put("preview", resumeData.getRawText().substring(0, Math.min(300, resumeData.getRawText().length())) + "...");

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/resume/current
     * Clears the stored resume.
     */
    @DeleteMapping("/current")
    public ResponseEntity<Map<String, Object>> clearResume() {
        resumeStorage.clear();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Resume cleared from memory");
        return ResponseEntity.ok(response);
    }
}
