package com.chhavi.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class OpenRouterClient {

    @Value("${openrouter.api.key:}")
    private String apiKey;

    @Value("${openrouter.model:google/gemini-flash-1.5}")
    private String modelName;

    private final RestTemplate restTemplate;

    public OpenRouterClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000); // 8 seconds
        factory.setReadTimeout(12000);    // 12 seconds
        this.restTemplate = new RestTemplate(factory);
    }

    public String generateContent(String systemInstruction, String prompt) {
        return generateContent(systemInstruction, prompt, "en");
    }

    @SuppressWarnings("unchecked")
    public String generateContent(String systemInstruction, List<Map<String, Object>> chatHistory, String lang) {
        if (apiKey == null || apiKey.trim().isEmpty() || "your_openrouter_api_key".equals(apiKey.trim())) {
            System.err.println("Configuration Error: OPENROUTER_API_KEY environment variable is missing.");
            String lastPrompt = "";
            if (chatHistory != null && !chatHistory.isEmpty()) {
                Map<String, Object> lastTurn = chatHistory.get(chatHistory.size() - 1);
                List<Map<String, String>> parts = (List<Map<String, String>>) lastTurn.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    lastPrompt = parts.get(0).get("text");
                }
            }
            return generateOfflineChatResponse(systemInstruction, lastPrompt, lang);
        }

        String url = "https://openrouter.ai/api/v1/chat/completions";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey.trim());
            headers.set("HTTP-Referer", "http://localhost:8081");
            headers.set("X-Title", "Vote India");

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemInstruction));
            }

            if (chatHistory != null) {
                for (Map<String, Object> turn : chatHistory) {
                    String role = (String) turn.get("role");
                    if ("model".equalsIgnoreCase(role)) {
                        role = "assistant";
                    }
                    String text = "";
                    List<Map<String, String>> parts = (List<Map<String, String>>) turn.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        text = parts.get(0).get("text");
                    }
                    messages.add(Map.of("role", role, "content", text));
                }
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> responseMessage = (Map<String, Object>) firstChoice.get("message");
                    if (responseMessage != null && responseMessage.containsKey("content")) {
                        return (String) responseMessage.get("content");
                    }
                }
            }
            return "We couldn't generate an AI response right now.";
        } catch (Exception e) {
            System.err.println("OpenRouter client error: " + e.getMessage());
            return "AI assistance is temporarily unavailable.";
        }
    }

    @SuppressWarnings("unchecked")
    public String generateContent(String systemInstruction, String prompt, String lang) {
        if (apiKey == null || apiKey.trim().isEmpty() || "your_openrouter_api_key".equals(apiKey.trim())) {
            System.err.println("Configuration Error: OPENROUTER_API_KEY environment variable is missing.");
            if (systemInstruction != null && systemInstruction.toLowerCase().contains("summarize the provided candidate manifesto")) {
                return generateLocalFallbackSummary(prompt, lang);
            }
            return generateOfflineChatResponse(systemInstruction, prompt, lang);
        }

        String url = "https://openrouter.ai/api/v1/chat/completions";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey.trim());
            headers.set("HTTP-Referer", "http://localhost:8081");
            headers.set("X-Title", "Vote India");

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemInstruction));
            }
            messages.add(Map.of("role", "user", "content", prompt));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> responseMessage = (Map<String, Object>) firstChoice.get("message");
                    if (responseMessage != null && responseMessage.containsKey("content")) {
                        return (String) responseMessage.get("content");
                    }
                }
            }
            return "We couldn't generate an AI response right now.";
        } catch (HttpClientErrorException e) {
            System.err.println("OpenRouter Client Error: " + e.getResponseBodyAsString());
            return "AI assistance is temporarily unavailable.";
        } catch (HttpServerErrorException e) {
            return "We couldn't generate an AI response right now.";
        } catch (ResourceAccessException e) {
            return "The AI assistant took too long to respond. Please try again.";
        } catch (Exception e) {
            return "We couldn't generate an AI response right now.";
        }
    }

    private String generateLocalFallbackSummary(String manifesto, String lang) {
        if (manifesto == null || manifesto.trim().isEmpty()) {
            return "hi".equalsIgnoreCase(lang) ? "कोई घोषणापत्र प्रदान नहीं किया गया।" : "No manifesto provided.";
        }
        
        String[] sentences = manifesto.split("(?<=[.!?])\\s+");
        List<String> keyPoints = new ArrayList<>();
        
        for (String sentence : sentences) {
            String lower = sentence.toLowerCase();
            if (lower.contains("promise") || lower.contains("will") || lower.contains("aim") || 
                lower.contains("focus") || lower.contains("goal") || lower.contains("support") || 
                lower.contains("provide") || lower.contains("improve") || lower.contains("create") ||
                lower.contains("strengthen") || lower.contains("ensure") || lower.contains("develop")) {
                keyPoints.add(sentence.trim());
            }
            if (keyPoints.size() >= 4) {
                break;
            }
        }
        
        if (keyPoints.isEmpty()) {
            for (int i = 0; i < Math.min(sentences.length, 3); i++) {
                keyPoints.add(sentences[i].trim());
            }
        }
        
        List<String> translatedPoints = new ArrayList<>();
        for (String point : keyPoints) {
            String translatedPoint = translateText(point, lang);
            translatedPoints.add("• " + translatedPoint);
        }
        
        String header = getLocalFallbackHeader(lang);
        return header + "\n\n" + String.join("\n", translatedPoints);
    }

    private String translateText(String text, String targetLang) {
        if (targetLang == null || "en".equalsIgnoreCase(targetLang)) {
            return text;
        }
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" 
                    + targetLang + "&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8");
            
            RestTemplate translationTemplate = new RestTemplate();
            List<?> response = translationTemplate.getForObject(url, List.class);
            if (response != null && !response.isEmpty()) {
                List<?> outerList = (List<?>) response.get(0);
                StringBuilder translated = new StringBuilder();
                for (Object item : outerList) {
                    List<?> innerList = (List<?>) item;
                    if (innerList != null && !innerList.isEmpty()) {
                        translated.append(innerList.get(0).toString());
                    }
                }
                return translated.toString();
            }
        } catch (Exception e) {
            System.err.println("Translation error: " + e.getMessage());
        }
        return text;
    }

    private String getLocalFallbackHeader(String lang) {
        switch (lang.toLowerCase()) {
            case "hi": return "उम्मीदवार के घोषणापत्र से मुख्य विशेषताएं:";
            case "bn": return "प्रार्थियों के घोषणापत्र से मुख्य विशेषताएं:";
            case "te": return "అభ్యర్థి మేనిఫెస్టో నుండి ముఖ్య ముଖ్యాంశాలు:";
            case "ta": return "வேட்பாளரின் தேர்தல் அறிக்கையிலிருந்து முக்கிய சிறப்பம்சங்கள்:";
            case "mr": return "उमेदवाराच्या जाहीरनाम्यातील ठळक मुद्दे:";
            case "gu": return "ઉમેદવારના ઢંઢેરામાંથી મુખ્ય હાઇલાઇટ્સ:";
            case "kn": return "ಅಭ್ಯರ್ಥಿಯ ಪ್ರಣಾಳಿಕೆಯಿಂದ ಪ್ರಮುಖ ಮುಖ್ಯಾಂಶಗಳು:";
            case "ml": return "സ്ഥാനാർത്ഥിയുടെ മാനിഫെസ്റ്റോയിൽ നിന്നുള്ള പ്രധാന ഹൈലൈറ്റുകൾ:";
            case "pa": return "ਉਮੀਦਵਾਰ ਦੇ ਮਨੋਰਥ ਪੱਤਰ ਤੋਂ ਮੁੱਖ ਨੁਕਤੇ:";
            case "or": return "ପ୍ରାର୍ଥୀଙ୍କ ଇସ୍ତାହାରରୁ ମୁଖ୍ୟ ଆକର୍ଷଣ:";
            default: return "Key highlights from the candidate's manifesto:";
        }
    }

    private String generateOfflineChatResponse(String systemInstruction, String userMessage, String lang) {
        String englishMsg = translateText(userMessage, "en").toLowerCase();
        String response = "";
        
        if (englishMsg.contains("age") || englishMsg.contains("vote age") || englishMsg.contains("voting age") || englishMsg.contains("eligible") || englishMsg.contains("eligibility") || englishMsg.contains("umra")) {
            response = "In India, the minimum age to vote is 18 years, as per the 61st Amendment Act of 1988.";
        } else if (englishMsg.contains("previous prime minister") || englishMsg.contains("pichla pradhan mantri") || englishMsg.contains("former prime minister")) {
            response = "Dr. Manmohan Singh was the Prime Minister of India immediately before the current Prime Minister Narendra Modi.";
        } else if (englishMsg.contains("chief minister") || englishMsg.contains("cm") || englishMsg.contains("mukhyamantri")) {
            response = "The Chief Minister (CM) is the elected head of a state government in India, appointed by the Governor of that state.";
        } else if (englishMsg.contains("election commission") || englishMsg.contains("eci") || englishMsg.contains("chunav aayog")) {
            response = "The Election Commission of India (ECI) is the autonomous constitutional authority responsible for conducting free and fair election processes in India.";
        } else if (englishMsg.contains("nota")) {
            response = "NOTA stands for 'None Of The Above'. It is an option on the voting ballot that allows voters to officially reject all candidates.";
        } else if (englishMsg.contains("lok sabha") || englishMsg.contains("rajya sabha")) {
            response = "Lok Sabha is the lower house of the Indian Parliament, elected directly by the citizens. Rajya Sabha is the upper house, representing states and union territories.";
        } else if (englishMsg.contains("model code") || englishMsg.contains("conduct") || englishMsg.contains("mcc") || englishMsg.contains("aachar sanhita")) {
            response = "The Model Code of Conduct is a set of guidelines issued by the Election Commission of India for political parties and candidates during elections to ensure free and fair polls.";
        } else if (englishMsg.contains("how are elections") || englishMsg.contains("how elections") || englishMsg.contains("conducted")) {
            response = "Elections in India are conducted by the Election Commission of India (ECI) using Electronic Voting Machines (EVM) and Voter Verifiable Paper Audit Trail (VVPAT).";
        } else if (englishMsg.contains("last election") || englishMsg.contains("result") || englishMsg.contains("winner") || englishMsg.contains("past") || englishMsg.contains("closed") || englishMsg.contains("jeeta") || englishMsg.contains("pichla")) {
            int start = systemInstruction.indexOf("Past Election Results:");
            if (start != -1) {
                response = systemInstruction.substring(start);
            } else {
                response = "No past election results are currently available.";
            }
        } else if (englishMsg.contains("election") || englishMsg.contains("poll") || englishMsg.contains("chunav") || englishMsg.contains("chal raha")) {
            if (systemInstruction.contains("Active Election exists: Yes")) {
                int start = systemInstruction.indexOf("title: ") + 7;
                int end = systemInstruction.indexOf("\n", start);
                String title = systemInstruction.substring(start, end);
                response = "Currently, there is an active election: \"" + title + "\".";
            } else {
                response = "Currently, there are no active elections.";
            }
        } else if (englishMsg.contains("candidate") || englishMsg.contains("ummeedwar") || englishMsg.contains("party") || englishMsg.contains("neta") || englishMsg.contains("list")) {
            int start = systemInstruction.indexOf("Available candidates: ") + 22;
            int end = systemInstruction.indexOf("\n", start);
            String candidates = systemInstruction.substring(start, end);
            if (candidates.trim().isEmpty() || candidates.equalsIgnoreCase("None")) {
                response = "There are no candidates registered for the current election.";
            } else {
                response = "The registered candidates for the election are: " + candidates;
            }
        } else if (englishMsg.contains("how to vote") || englishMsg.contains("vote kaise") || englishMsg.contains("process") || englishMsg.contains("tarika") || englishMsg.contains("voted")) {
            response = "To cast your vote, please follow these steps:\n"
                     + "1. Go to the 'Cast Vote' page on your dashboard.\n"
                     + "2. Choose your preferred candidate.\n"
                     + "3. Click the 'Submit Vote' button.";
        } else if (englishMsg.contains("hello") || englishMsg.contains("hi") || englishMsg.contains("hey")) {
            response = "Hello! I am your offline AI Voting Assistant. You can ask me about candidate manifestos, active elections, how to vote, or who the candidates are.";
        } else {
            response = "AI assistance is temporarily unavailable. Please try again later.";
        }
        
        return translateText(response, lang);
    }
}
