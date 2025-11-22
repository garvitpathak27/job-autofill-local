# Architecture Documentation

## System Overview

Job Autofill Assistant is a three-tier application:

1. **Chrome Extension (Frontend)** - User interface and form interaction
2. **Spring Boot Backend (API Layer)** - PDF parsing and orchestration
3. **Ollama (AI Layer)** - Local LLM for intelligent field mapping

---

## Data Flow

### Resume Upload & Extraction

```
User uploads PDF
↓
Extension sends to /api/resume/upload
↓
Spring Boot: PDFBox extracts text
↓
Text stored in ResumeStorage (in-memory)
↓
Extension calls /api/extract
↓
Spring Boot: OllamaService.extractStructuredResume()
↓
Ollama: Phi-3 Mini processes prompt + resume text
↓
Returns structured JSON (personal_info, education, experience, skills)
↓
JsonSanitizer fixes inconsistent types
↓
Stored in ResumeData.extractedJson
↓
Extension displays resume summary
```

### Form Autofill

```
User clicks "Autofill Current Page"
↓
content_script.js: detectFormFields()
↓
For each field:
↓
Extract: label, name, placeholder, type
↓
Send to /api/autofill
↓
Spring Boot: OllamaService.mapFieldToResumeValue()
↓
Ollama: Phi-3 Mini analyzes field + resume JSON
↓
Returns: {suggested_value, confidence, reasoning}
↓
If confidence > 0.5:
↓
content_script.js: fillField()
↓
Field value updated + green flash
```

---

## API Endpoints

### Health Check
```
GET /api/health
Response: {status: "UP", timestamp: "...", service: "...", version: "..."}
```

### Resume Management
```
POST /api/resume/upload
Body: multipart/form-data (file: PDF)
Response: {success: true, fileName: "...", textLength: 3674, preview: "..."}

GET /api/resume/current
Response: {fileName: "...", uploadedAt: "...", textLength: 3674, preview: "..."}

DELETE /api/resume/current
Response: {success: true, message: "Resume cleared from memory"}
```

### Extraction
```
POST /api/extract
Response: {
  success: true,
  structured_resume: {
    personal_info: {...},
    education: [...],
    experience: [...],
    skills: [...]
  }
}

GET /api/extract/current
Response: Same as POST /api/extract (cached)
```

### Autofill
```
POST /api/autofill
Body: {
  field_label: "Full Name",
  field_name: "applicant_name",
  field_placeholder: "Enter your name",
  field_type: "text"
}
Response: {
  suggested_value: "Garvit Pathak",
  confidence: 0.95,
  reasoning: "Matches personal_info.name",
  field_matched: "personal_info.name"
}

POST /api/autofill/batch
Body: {
  "field1": {...},
  "field2": {...}
}
Response: {
  "field1": {suggested_value: "...", ...},
  "field2": {suggested_value: "...", ...}
}
```

---

## Key Design Decisions

### Why Spring Boot?
- Lightweight, fast startup
- Built-in HTTP server (Tomcat)
- Excellent REST API support
- Easy integration with Jackson (JSON)
- Familiar for Java developers

### Why Ollama?
- 100% local execution
- No API keys or cloud dependencies
- Fast inference on consumer hardware
- Simple HTTP API
- Wide model support

### Why Phi-3 Mini?
- Small size (~3.8 GB)
- Instruction-tuned for structured tasks
- Good JSON output quality
- Fast inference (<10s for resume extraction)
- Free and open-source

### Why Manifest V3?
- Latest Chrome extension standard
- Better security model
- Service workers (instead of background pages)
- Required for new extensions

### Why In-Memory Storage?
- Simplicity for POC
- Fast access (no disk I/O)
- Privacy (cleared on restart)
- Can be upgraded to SQLite later

---

## Security Considerations

1. **CORS**: Wildcard (`*`) allows extension to call backend
2. **No Authentication**: Single-user local setup
3. **File Validation**: Only PDFs accepted
4. **Content Script Isolation**: Runs in sandbox
5. **No External APIs**: Zero network exposure

---

## Performance Optimizations

1. **WebClient**: Non-blocking HTTP for Ollama calls
2. **JSON Sanitizer**: Fixes LLM output inconsistencies
3. **Confidence Threshold**: Skips low-quality suggestions (< 0.5)
4. **Content Script Caching**: Injected once per page load
5. **In-Memory Storage**: Fast resume retrieval

---

## Error Handling

### Backend
- Try-catch blocks with detailed logging
- HTTP status codes (400, 404, 500)
- Graceful fallbacks (empty suggestions on error)

### Extension
- Console logging for debugging
- User-friendly error messages
- Retry logic for content script injection

---

## Testing Strategy

1. **Unit Tests**: (Not implemented yet - future work)
2. **Integration Tests**: Manual curl commands
3. **E2E Tests**: Test form with known fields
4. **Real-World Tests**: Actual job sites

---

## Scalability Considerations

### Current Limitations
- Single resume at a time
- In-memory storage (lost on restart)
- Sequential field processing (no batch optimization)
- Single backend instance

### Future Improvements
- Multi-user support with database
- Resume versioning
- Batch autofill API
- Load balancing for Ollama
- Redis caching for structured resumes

---

## Deployment Options

### Development (Current)
- Backend: `mvn spring-boot:run` on localhost:8080
- Ollama: `ollama serve` on localhost:11434
- Extension: Load unpacked from `extension/`

### Production (Future)
- Backend: Package as JAR, run as systemd service
- Ollama: Run as background service
- Extension: Package as CRX, distribute via Chrome Web Store

---

## Monitoring & Logging

### Backend Logs
```
2025-11-22T11:00:00.123 INFO : Started JobAutofillApplication
2025-11-22T11:00:05.456 INFO : Resume uploaded: resume.pdf
2025-11-22T11:00:20.789 INFO : Successfully extracted structured resume
2025-11-22T11:00:25.012 DEBUG : Mapped field 'Full Name' to 'Garvit Pathak'
```

### Extension Console
```
Job Autofill content script loaded on: https://...
Found 12 form fields
✓ Filled: Full Name = Garvit Pathak
✓ Filled: Email = garvitpathak2003@gmail.com
```

---

## Dependencies

### Backend (pom.xml)
- spring-boot-starter-web: 3.2.0
- spring-boot-starter-webflux: 3.2.0
- pdfbox: 3.0.1
- jackson-databind: (included in spring-boot-starter-web)
- lombok: (optional, for boilerplate reduction)

### Extension
- No external dependencies (vanilla JS)
- Chrome Extension API (Manifest V3)

---

**For questions or contributions, see README.md**
