package com.chhavi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

    @Service
    public class GroqAiService {

        @Value("${groq.api.key}")
        private String apiKey;

        @Value("${groq.api.url}")
        private String apiUrl;

        public String askAiAssistant(String userPrompt) {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Model: llama-3.3-70b-versatile (Fast and Free)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama-3.3-70b-versatile");

            List<Map<String, String>> messages = new ArrayList<>();

            // System message to set neutral tone
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are a neutral AI Voting Assistant for VOTE INDIA. Answer queries helpfully.");
            messages.add(systemMsg);

            // User message
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            try {
                ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);
                List choices = (List) response.getBody().get("choices");
                Map firstChoice = (Map) choices.get(0);
                Map message = (Map) firstChoice.get("message");
                return message.get("content").toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "Failed to connect to assistant: " + e.getMessage();
            }
        }
    }