package com.jobautofill.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ResumeParserService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);

    /**
     * Extracts text from a PDF file using Apache PDFBox.
     *
     * @param file MultipartFile uploaded from client
     * @return Extracted text as a single String
     * @throws IOException if PDF is corrupt or unreadable
     */
    public String extractTextFromPdf(MultipartFile file) throws IOException {
        log.info("Starting PDF text extraction for file: {}", file.getOriginalFilename());

        // Load PDF document from MultipartFile bytes
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            // Create PDFTextStripper to extract text
            PDFTextStripper stripper = new PDFTextStripper();

            // Extract text from all pages
            String text = stripper.getText(document);

            log.info("Successfully extracted {} characters from PDF", text.length());

            return text.trim();
        } catch (IOException e) {
            log.error("Failed to parse PDF: {}", e.getMessage());
            throw new IOException("Unable to parse PDF file: " + e.getMessage(), e);
        }
    }

    /**
     * Validates if the uploaded file is a PDF.
     *
     * @param file MultipartFile to validate
     * @return true if PDF, false otherwise
     */
    public boolean isPdf(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        return (contentType != null && contentType.equals("application/pdf")) ||
                (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
    }
}
