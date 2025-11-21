package com.jobautofill.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sanitizes JSON from Ollama to handle inconsistent data types.
 * Converts single-element arrays to strings where appropriate.
 */
public class JsonSanitizer {

    private static final Logger log = LoggerFactory.getLogger(JsonSanitizer.class);

    /**
     * Sanitizes Ollama JSON response to ensure consistent data types.
     * Converts single-element arrays to strings for fields that should be strings.
     * 
     * @param rawJson Raw JSON string from Ollama
     * @param objectMapper Jackson ObjectMapper
     * @return Sanitized JSON string
     */
    public static String sanitizeOllamaJson(String rawJson, ObjectMapper objectMapper) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawJson);
            
            // Sanitize education array
            if (rootNode.has("education") && rootNode.get("education").isArray()) {
                ArrayNode educationArray = (ArrayNode) rootNode.get("education");
                for (JsonNode educationNode : educationArray) {
                    if (educationNode.isObject()) {
                        sanitizeStringFields((ObjectNode) educationNode, "year", "score", "location");
                    }
                }
            }

            // Sanitize experience array
            if (rootNode.has("experience") && rootNode.get("experience").isArray()) {
                ArrayNode experienceArray = (ArrayNode) rootNode.get("experience");
                for (JsonNode experienceNode : experienceArray) {
                    if (experienceNode.isObject()) {
                        sanitizeStringFields((ObjectNode) experienceNode, 
                            "title", "company", "duration", "description", "location");
                    }
                }
            }

            // Sanitize personal_info
            if (rootNode.has("personal_info") && rootNode.get("personal_info").isObject()) {
                sanitizeStringFields((ObjectNode) rootNode.get("personal_info"), 
                    "name", "email", "phone", "linkedin", "github");
            }

            // Sanitize skills (convert nested arrays to flat string array)
            if (rootNode.has("skills") && rootNode.get("skills").isArray()) {
                ArrayNode skillsArray = (ArrayNode) rootNode.get("skills");
                ArrayNode newSkills = objectMapper.createArrayNode();
                
                for (JsonNode skillNode : skillsArray) {
                    if (skillNode.isArray()) {
                        // Flatten nested arrays
                        for (JsonNode nestedSkill : skillNode) {
                            if (nestedSkill.isTextual()) {
                                newSkills.add(nestedSkill.asText());
                            }
                        }
                    } else if (skillNode.isTextual()) {
                        newSkills.add(skillNode.asText());
                    }
                }
                
                ((ObjectNode) rootNode).set("skills", newSkills);
            }

            String sanitizedJson = objectMapper.writeValueAsString(rootNode);
            log.debug("Sanitized JSON: {}", sanitizedJson);
            
            return sanitizedJson;

        } catch (Exception e) {
            log.warn("Failed to sanitize JSON, returning original: {}", e.getMessage());
            return rawJson;
        }
    }

    /**
     * Converts single-element arrays to strings for specified fields.
     */
    private static void sanitizeStringFields(ObjectNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode fieldValue = node.get(fieldName);
                
                // If it's an array with a single element, convert to string
                if (fieldValue.isArray() && fieldValue.size() == 1) {
                    String stringValue = fieldValue.get(0).asText();
                    node.put(fieldName, stringValue);
                    log.debug("Converted field '{}' from array to string: {}", fieldName, stringValue);
                }
                // If it's an array with multiple elements, join with comma
                else if (fieldValue.isArray() && fieldValue.size() > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < fieldValue.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(fieldValue.get(i).asText());
                    }
                    node.put(fieldName, sb.toString());
                    log.debug("Converted field '{}' from multi-element array to string: {}", fieldName, sb.toString());
                }
            }
        }
    }
}
