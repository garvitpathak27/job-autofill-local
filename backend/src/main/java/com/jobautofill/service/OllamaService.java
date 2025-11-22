package com.jobautofill.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobautofill.model.AutofillRequest;
import com.jobautofill.model.AutofillResponse;
import com.jobautofill.model.OllamaRequest;
import com.jobautofill.model.OllamaResponse;
import com.jobautofill.model.StructuredResume;
import com.jobautofill.util.FieldExtractor;
import com.jobautofill.util.FieldIntentClassifier;
import com.jobautofill.util.FieldIntentClassifier.IntentType;
import com.jobautofill.util.JsonSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.model}")
    private volatile String model;

    @Value("${ollama.timeout}")
    private int timeout;

    public OllamaService(WebClient ollamaWebClient, ObjectMapper objectMapper) {
        this.webClient = ollamaWebClient;
        this.objectMapper = objectMapper;
    }

    public StructuredResume extractStructuredResume(String resumeText) {
        log.info("Starting resume extraction with Ollama (model: {})", model);

        String prompt = buildExtractionPrompt(resumeText);

        OllamaRequest request = new OllamaRequest();
        request.setModel(model);
        request.setStream(false);
        request.setFormat("json");
        request.setMessages(List.of(
                new OllamaRequest.Message("user", prompt)));

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
            String sanitizedJson = JsonSanitizer.sanitizeOllamaJson(rawJsonContent, objectMapper);
            StructuredResume structuredResume = objectMapper.readValue(sanitizedJson, StructuredResume.class);

            log.info("Successfully extracted structured resume");
            return structuredResume;

        } catch (Exception e) {
            log.error("Failed to extract structured resume", e);
            throw new RuntimeException("Ollama extraction failed: " + e.getMessage(), e);
        }
    }

    public AutofillResponse mapFieldToResumeValue(AutofillRequest fieldRequest, StructuredResume resume) {
        log.info("Mapping field: {} (name: {})", fieldRequest.getFieldLabel(), fieldRequest.getFieldName());

        if (resume == null) {
            log.warn("Structured resume is null; returning empty value for field {}", fieldRequest.getFieldLabel());
            return new AutofillResponse("", 0.0, "Structured resume unavailable", "no_resume");
        }

        FieldIntentClassifier.IntentResult intentResult = FieldIntentClassifier.classify(fieldRequest);
        IntentType intentType = intentResult.getType();
        log.debug("Detected field intent {} (confidence: {})", intentType, intentResult.getConfidence());

        FieldExtractor.ExtractedValue simpleValue = FieldExtractor.extractValue(
                fieldRequest.getFieldLabel(),
                fieldRequest.getFieldName(),
                fieldRequest.getFieldType(),
                resume);

        if (!simpleValue.value.isEmpty() && simpleValue.confidence >= requiredConfidence(intentType)) {
            log.info("Using simple extraction: {} (confidence: {})", simpleValue.value, simpleValue.confidence);
            return new AutofillResponse(
                    simpleValue.value,
                    simpleValue.confidence,
                    simpleValue.reasoning,
                    "simple_extraction");
        }

        if (!hasResumeSupport(intentType, resume)) {
            log.info("No resume data found for intent {}. Returning empty value.", intentType);
            return new AutofillResponse("", 0.1, "No relevant resume data for intent " + intentType.getDisplayName(),
                    "no_data");
        }

        String prompt = buildSmartAutofillPrompt(fieldRequest, resume, intentResult);

        OllamaRequest request = new OllamaRequest();
        request.setModel(model);
        request.setStream(false);
        request.setFormat("json");
        request.setMessages(List.of(
                new OllamaRequest.Message("user", prompt)));

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
            AutofillResponse autofillResponse = objectMapper.readValue(jsonContent, AutofillResponse.class);
            AutofillResponse guardedResponse = enforceIntentConstraints(intentType, autofillResponse, resume);

            log.info("Autofill result for intent {}: {}", intentType, guardedResponse.getSuggestedValue());
            return guardedResponse;

        } catch (Exception e) {
            log.error("Failed to map field to resume value with Ollama", e);
            return new AutofillResponse("", 0.0, "Failed to map field: " + e.getMessage(), "llm_error");
        }
    }

    private String buildExtractionPrompt(String resumeText) {
        return """
                You are a resume parser. Extract information and return ONLY valid JSON.

                CRITICAL RULES:
                1. Return ONLY the JSON object, no markdown, no code blocks, no explanation
                2. ALL string fields must be strings (use "" for empty, not arrays)
                3. Use null for missing data

                Required JSON structure:
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
                      "year": "string",
                      "score": "string or null",
                      "location": "string or null"
                    }
                  ],
                  "experience": [
                    {
                      "title": "string",
                      "company": "string",
                      "duration": "string",
                      "description": "string",
                      "location": "string or null"
                    }
                  ],
                  "skills": ["string1", "string2"]
                }

                Resume text:
                ---
                """ + resumeText + """
                ---

                Return ONLY the JSON object.
                """;
    }

    private String buildSmartAutofillPrompt(AutofillRequest fieldRequest, StructuredResume resume,
            FieldIntentClassifier.IntentResult intentResult) {
        String focusedContext = buildFocusedContext(intentResult.getType(), resume);
        String resumeJson = toPrettyJson(resume);

        return String.format("""
                You are filling a job application form field.

                Field Label: %s
                Field Name: %s
                Field Type: %s
                Field Intent: %s

                Resume Context (intent focused):
                %s

                Full Resume JSON:
                %s

                Instructions:
                1. Use only information from the resume that matches the field intent.
                2. Do NOT repeat technical skills unless Field Intent = skill_list.
                3. If no relevant data exists, respond with the exact string EMPTY.
                4. Keep the response concise and aligned with the field intent.
                5. Set confidence to 0.0 when returning EMPTY.

                Return ONLY JSON in this format:
                {
                    "suggested_value": "value or EMPTY",
                    "confidence": 0.0,
                    "reasoning": "short explanation referencing resume",
                    "field_matched": "which resume section you used"
                }
                """,
                safe(fieldRequest.getFieldLabel()),
                safe(fieldRequest.getFieldName()),
                safe(fieldRequest.getFieldType()),
                intentResult.getType().getDisplayName(),
                focusedContext,
                resumeJson);
    }

    private double requiredConfidence(IntentType intentType) {
        return switch (intentType) {
            case GITHUB_URL, LINKEDIN_URL, PORTFOLIO_URL, GENERIC_URL -> 0.60;
            case EDUCATION_INSTITUTION, EDUCATION_DEGREE, EDUCATION_YEAR, EXPERIENCE_SUMMARY -> 0.75;
            case MOTIVATION_STATEMENT, AVAILABILITY_DATE, TIMELINE, HEAR_ABOUT -> 0.85;
            default -> 0.80;
        };
    }

    private boolean hasResumeSupport(IntentType intentType, StructuredResume resume) {
        if (resume == null) {
            return false;
        }

        return switch (intentType) {
            case SKILL_LIST -> resume.getSkills() != null && !resume.getSkills().isEmpty();
            case EXPERIENCE_SUMMARY -> resume.getExperience() != null && !resume.getExperience().isEmpty();
            case EDUCATION_INSTITUTION, EDUCATION_DEGREE, EDUCATION_YEAR ->
                resume.getEducation() != null && !resume.getEducation().isEmpty();
            case GITHUB_URL -> resume.getPersonalInfo() != null && safeNotEmpty(resume.getPersonalInfo().getGithub());
            case LINKEDIN_URL, PORTFOLIO_URL ->
                resume.getPersonalInfo() != null && safeNotEmpty(resume.getPersonalInfo().getLinkedin());
            case MOTIVATION_STATEMENT -> (resume.getExperience() != null && !resume.getExperience().isEmpty())
                    || (resume.getSkills() != null && !resume.getSkills().isEmpty());
            case AVAILABILITY_DATE, TIMELINE, HEAR_ABOUT -> false;
            default -> true;
        };
    }

    private AutofillResponse enforceIntentConstraints(IntentType intentType, AutofillResponse response,
            StructuredResume resume) {
        if (response == null) {
            return new AutofillResponse("", 0.0, "Model returned null response", "llm_error");
        }

        String value = response.getSuggestedValue();
        String reasoning = response.getReasoning();
        String fieldMatched = response.getFieldMatched();

        if (value == null) {
            value = "";
        }

        String trimmed = value.trim();

        if ("EMPTY".equalsIgnoreCase(trimmed)) {
            return new AutofillResponse("", 0.1,
                    reasoning != null ? reasoning : "Model indicated no relevant data",
                    fieldMatched != null ? fieldMatched : "no_data");
        }

        if (intentType != IntentType.SKILL_LIST && looksLikeSkillDump(trimmed, resume)) {
            log.warn("Discarding AI output for intent {} due to skill spillover", intentType);
            return new AutofillResponse("", 0.0, "Discarded AI output that did not match intent", "intent_guard");
        }

        if ((intentType == IntentType.GITHUB_URL || intentType == IntentType.LINKEDIN_URL
                || intentType == IntentType.PORTFOLIO_URL || intentType == IntentType.GENERIC_URL)
                && !trimmed.isEmpty()) {
            trimmed = normalizeUrl(trimmed);
        }

        return new AutofillResponse(trimmed, response.getConfidence(), reasoning, fieldMatched);
    }

    private String normalizeUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private boolean looksLikeSkillDump(String value, StructuredResume resume) {
        if (value == null || value.isEmpty() || resume == null || resume.getSkills() == null
                || resume.getSkills().isEmpty()) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String skill : resume.getSkills()) {
            if (skill != null && !skill.isEmpty() && lower.contains(skill.toLowerCase(Locale.ROOT))) {
                matches++;
            }
        }

        if (matches == 0) {
            return false;
        }

        int threshold = Math.max(3, resume.getSkills().size() / 2);
        return matches >= threshold;
    }

    private String buildFocusedContext(IntentType intentType, StructuredResume resume) {
        if (resume == null) {
            return "{}";
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            switch (intentType) {
                case SKILL_LIST -> root.set("skills", objectMapper.valueToTree(resume.getSkills()));
                case EXPERIENCE_SUMMARY -> root.set("experience", objectMapper.valueToTree(resume.getExperience()));
                case EDUCATION_INSTITUTION, EDUCATION_DEGREE, EDUCATION_YEAR ->
                    root.set("education", objectMapper.valueToTree(resume.getEducation()));
                case GITHUB_URL -> {
                    ObjectNode profile = objectMapper.createObjectNode();
                    if (resume.getPersonalInfo() != null) {
                        profile.put("github", safe(resume.getPersonalInfo().getGithub()));
                    }
                    root.set("profile", profile);
                }
                case LINKEDIN_URL, PORTFOLIO_URL -> {
                    ObjectNode profile = objectMapper.createObjectNode();
                    if (resume.getPersonalInfo() != null) {
                        profile.put("linkedin", safe(resume.getPersonalInfo().getLinkedin()));
                    }
                    root.set("profile", profile);
                }
                case MOTIVATION_STATEMENT -> {
                    root.set("experience_highlights", objectMapper.valueToTree(resume.getExperience()));
                    root.set("skills", objectMapper.valueToTree(resume.getSkills()));
                }
                default -> root.set("resume_snapshot", objectMapper.valueToTree(resume));
            }

            if (root.isEmpty()) {
                root.set("resume_snapshot", objectMapper.valueToTree(resume));
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to build focused context for intent {}", intentType, e);
            return "{}";
        }
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON", e);
            return "{}";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean safeNotEmpty(String value) {
        return value != null && !value.isBlank();
    }

    public List<ModelSummary> listAvailableModels() {
        try {
            String rawResponse = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (rawResponse == null) {
                throw new RuntimeException("Empty response from Ollama when listing models");
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode modelsNode = root.get("models");

            List<ModelSummary> summaries = new ArrayList<>();
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode node : modelsNode) {
                    String name = node.path("name").asText();
                    long sizeBytes = node.path("size").asLong(0L);
                    String modifiedAt = node.path("modified_at").asText(null);
                    String family = null;

                    JsonNode detailsNode = node.path("details");
                    if (detailsNode != null && !detailsNode.isMissingNode()) {
                        JsonNode familiesNode = detailsNode.path("families");
                        if (familiesNode.isArray() && familiesNode.size() > 0) {
                            family = familiesNode.get(0).asText(null);
                        } else {
                            family = detailsNode.path("family").asText(null);
                        }
                    }

                    summaries.add(new ModelSummary(name, family, sizeBytes, modifiedAt));
                }
            }

            return summaries;

        } catch (Exception e) {
            log.error("Failed to fetch available Ollama models", e);
            throw new RuntimeException("Unable to fetch available models: " + e.getMessage(), e);
        }
    }

    public boolean isModelAvailable(String candidateModel) {
        try {
            webClient.post()
                    .uri("/api/show")
                    .bodyValue(Map.of("model", candidateModel))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Model '{}' not found in Ollama", candidateModel);
                return false;
            }
            log.error("Error while verifying model {}", candidateModel, e);
            throw new RuntimeException("Failed to verify model: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while verifying model {}", candidateModel, e);
            throw new RuntimeException("Failed to verify model: " + e.getMessage(), e);
        }
    }

    public String getCurrentModel() {
        return model;
    }

    public void setModel(String newModel) {
        log.info("Switching Ollama model from {} to {}", this.model, newModel);
        this.model = newModel;
    }

    public static class ModelSummary {
        private final String name;
        private final String family;
        private final long sizeBytes;
        private final String modifiedAt;

        public ModelSummary(String name, String family, long sizeBytes, String modifiedAt) {
            this.name = name;
            this.family = family;
            this.sizeBytes = sizeBytes;
            this.modifiedAt = modifiedAt;
        }

        public String getName() {
            return name;
        }

        public String getFamily() {
            return family;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getModifiedAt() {
            return modifiedAt;
        }
    }
}
