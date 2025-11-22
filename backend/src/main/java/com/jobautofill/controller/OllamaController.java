package com.jobautofill.controller;

import com.jobautofill.service.OllamaService;
import com.jobautofill.service.OllamaService.ModelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ollama")
public class OllamaController {

    private static final Logger log = LoggerFactory.getLogger(OllamaController.class);

    private final OllamaService ollamaService;

    public OllamaController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ModelSummary> models = ollamaService.listAvailableModels();
            response.put("models", models);
            response.put("activeModel", ollamaService.getCurrentModel());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to fetch Ollama models", e);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/model")
    public ResponseEntity<Map<String, Object>> getCurrentModel() {
        return ResponseEntity.ok(Map.of("model", ollamaService.getCurrentModel()));
    }

    @PostMapping("/model")
    public ResponseEntity<Map<String, Object>> updateModel(@RequestBody Map<String, String> request) {
        String requestedModel = request.get("model");
        if (requestedModel == null || requestedModel.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", "Model name must not be empty"
                    ));
        }

        boolean available;
        try {
            available = ollamaService.isModelAvailable(requestedModel);
        } catch (RuntimeException e) {
            log.error("Failed to verify Ollama model {}", requestedModel, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }

        if (!available) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", "Model not found in local Ollama",
                            "model", requestedModel
                    ));
        }

        ollamaService.setModel(requestedModel);
        log.info("Ollama model switched to {}", requestedModel);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "model", requestedModel
        ));
    }
}
