package com.chhavi.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.chhavi.ai.OpenRouterClient;
import com.chhavi.pojo.Candidate;
import com.chhavi.pojo.Election;
import com.chhavi.pojo.User;
import com.chhavi.repository.CandidateRepository;
import com.chhavi.repository.ElectionRepository;
import com.chhavi.repository.UserRepository;
import com.chhavi.repository.VoteRepository;
import com.chhavi.repository.VoterRecordRepository;
import jakarta.servlet.http.HttpSession;

@Controller
public class AiController {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final VoterRecordRepository voterRecordRepository;
    private final OpenRouterClient openRouterClient;

    // application.properties se API key inject karein
    @Value("${OPENROUTER_API_KEY:}")
    private String openRouterApiKey;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    public AiController(UserRepository userRepository, CandidateRepository candidateRepository,
                        ElectionRepository electionRepository, VoteRepository voteRepository,
                        VoterRecordRepository voterRecordRepository,
                        OpenRouterClient openRouterClient) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.electionRepository = electionRepository;
        this.voteRepository = voteRepository;
        this.voterRecordRepository = voterRecordRepository;
        this.openRouterClient = openRouterClient;
    }

    // Helper method: Pehle Environment variable, fir application.properties se key lene ke liye
    private String getEffectiveApiKey() {
        String envKey = System.getenv("OPENROUTER_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey;
        }
        if (openRouterApiKey != null && !openRouterApiKey.trim().isEmpty()) {
            return openRouterApiKey;
        }
        if (groqApiKey != null && !groqApiKey.trim().isEmpty()) {
            return groqApiKey;
        }
        return null;
    }

    // HTML View Load karne ke liye
    @GetMapping("/voter/ai-assistant")
    public String showAssistantPage() {
        return "voter/ai-assistant";
    }

    // Frontend JS se aane wali request ko handle karne ke liye EXACT URL PATH
    @SuppressWarnings("unchecked")
    @PostMapping(value = "/ai/chat", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Map<String, String> processChat(
            Principal principal,
            @RequestParam String message,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            HttpSession session) {

        Map<String, String> responseMap = new HashMap<>();

        if (message == null || message.trim().isEmpty()) {
            responseMap.put("response", "Message cannot be empty.");
            return responseMap;
        }

        // Check if API Key exists
        String activeKey = getEffectiveApiKey();
        if (activeKey == null || activeKey.trim().isEmpty()) {
            System.err.println("Configuration Error: API Key is missing in both Environment Variables and application.properties.");
        }

        // 1. Initialize/retrieve chat history from Session
        List<Map<String, Object>> history = (List<Map<String, Object>>) session.getAttribute("chatHistory");
        if (history == null) {
            history = new ArrayList<>();
        }

        // Add user message to history
        Map<String, Object> userTurn = new HashMap<>();
        userTurn.put("role", "user");
        userTurn.put("parts", List.of(Map.of("text", message)));
        history.add(userTurn);

        // Limit history size to last 20 turns
        if (history.size() > 20) {
            history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
        }

        // 2. PRIVACY GUARD
        if (isPrivateQuery(message)) {
            String guardResponse = "Registered voter information is private and cannot be exposed. I can help with general voting information, election status, or candidate details instead.";

            Map<String, Object> modelTurn = new HashMap<>();
            modelTurn.put("role", "model");
            modelTurn.put("parts", List.of(Map.of("text", guardResponse)));
            history.add(modelTurn);
            session.setAttribute("chatHistory", history);

            responseMap.put("response", guardResponse);
            return responseMap;
        }

        // User & Database context fetch
        User user = null;
        if (principal != null) {
            user = userRepository.findByEmail(principal.getName()).orElse(null);
        }

        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);
        List<Candidate> candidates = candidateRepository.findAll();

        boolean isActive = activeElection != null;
        boolean hasVoted = (user != null && activeElection != null)
                && voterRecordRepository.existsByUserIdAndElectionId(user.getId(), activeElection.getId());

        // 3. LOCAL RESPONSES FOR CORE QUESTIONS
        String localAns = getLocalResponse(message, lang, user, activeElection, hasVoted, candidates);
        if (localAns != null) {
            Map<String, Object> modelTurn = new HashMap<>();
            modelTurn.put("role", "model");
            modelTurn.put("parts", List.of(Map.of("text", localAns)));
            history.add(modelTurn);
            session.setAttribute("chatHistory", history);

            responseMap.put("response", localAns);
            return responseMap;
        }

        // Context Preparation for OpenRouter / Groq AI
        String candidatesInfo = candidates.stream()
                .map(c -> c.getName() + " (" + c.getParty() + ") - Manifesto: " + (c.getManifesto() != null ? c.getManifesto() : "None"))
                .collect(Collectors.joining("; "));

        List<Election> closedElections = electionRepository.findAllByStatus("CLOSED");
        StringBuilder closedElectionsInfo = new StringBuilder();
        if (closedElections.isEmpty()) {
            closedElectionsInfo.append("No closed elections yet.\n");
        } else {
            for (Election closed : closedElections) {
                closedElectionsInfo.append("- Election: ").append(closed.getTitle()).append(" (CLOSED). Results: ");
                long totalVotes = voteRepository.countByElectionId(closed.getId());
                long maxVotes = -1;
                List<String> winners = new ArrayList<>();
                for (Candidate c : candidates) {
                    long cvotes = voteRepository.countByElectionIdAndCandidateId(closed.getId(), c.getId());
                    if (cvotes > maxVotes) {
                        maxVotes = cvotes;
                        winners.clear();
                        winners.add(c.getName() + " (" + c.getParty() + ") with " + cvotes + " votes");
                    } else if (cvotes == maxVotes && cvotes > 0) {
                        winners.add(c.getName() + " (" + c.getParty() + ") with " + cvotes + " votes");
                    }
                }
                if (maxVotes <= 0) {
                    closedElectionsInfo.append("No votes were cast.");
                } else {
                    closedElectionsInfo.append(String.join(", ", winners)).append(" out of ").append(totalVotes).append(" total votes.");
                }
                closedElectionsInfo.append("\n");
            }
        }

        String factualContext = "Current Election State:\n"
                + "- Active Election exists: " + (isActive ? "Yes, title: " + activeElection.getTitle() : "No") + "\n"
                + "- Current user has voted: " + (hasVoted ? "Yes" : "No") + "\n"
                + "- Available candidates: " + candidatesInfo + "\n"
                + "- Voting process: Voter logs in, goes to 'Cast Vote' page, selects candidate, and clicks 'Submit Vote'.\n"
                + "Past Election Results:\n"
                + closedElectionsInfo.toString();

        String langName = getLanguageName(lang);
        String sysInstruction = "You are a neutral AI Voting Assistant. Use the following factual context to answer voter queries:\n"
                + factualContext + "\n"
                + "In addition to answering portal-specific questions using the context above, you can also answer general election, civic, and political knowledge questions using your general knowledge.\n"
                + "Strict Neutrality Instructions:\n"
                + "1. Do not tell users who to vote for, do not rank candidates.\n"
                + "2. Stated promises and manifestos must be summarized neutrally.\n"
                + "3. Keep answers neutral, objective, and factual.\n"
                + "4. Limit replies to a few sentences.\n"
                + "Please reply entirely in " + langName + " language using its native script.";

        String aiResponse = openRouterClient.generateContent(sysInstruction, history, lang);

        // Add model response to history
        Map<String, Object> modelTurn = new HashMap<>();
        modelTurn.put("role", "model");
        modelTurn.put("parts", List.of(Map.of("text", aiResponse)));
        history.add(modelTurn);
        session.setAttribute("chatHistory", history);

        responseMap.put("response", aiResponse);
        return responseMap;
    }

    private boolean isPrivateQuery(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase().trim();

        if (lower.contains("voter")) {
            if (lower.contains("list") || lower.contains("name") || lower.contains("email") ||
                    lower.contains("phone") || lower.contains("mobile") || lower.contains("details") ||
                    lower.contains("register") || lower.contains("identity") || lower.contains("identities") ||
                    lower.contains("private") || lower.contains("data") || lower.contains("info") ||
                    lower.contains("personal")) {
                return true;
            }
        }

        if (lower.contains("password") || lower.contains("otp") || lower.contains("token") ||
                lower.contains("api key") || lower.contains("api_key") || lower.contains("apikey") ||
                lower.contains("database") || lower.contains("mongodb") || lower.contains("credentials") ||
                lower.contains("groq") || lower.contains("grok") || lower.contains("gemini")) {
            return true;
        }

        return false;
    }

    private String getLocalResponse(String message, String lang, User user, Election activeElection, boolean hasVoted, List<Candidate> candidates) {
        String lower = message.toLowerCase().trim();
        boolean isHindi = "hi".equalsIgnoreCase(lang) || lower.contains("kaise") || lower.contains("kese") || lower.contains("kare") || lower.contains("kru") || lower.contains("krna") || lower.contains("kr ");

        if (lower.contains("how to vote") || lower.contains("how can i vote") || lower.contains("vote kaise") ||
                lower.contains("vote kr") || lower.contains("vote kese") || lower.contains("voting process")) {
            if (isHindi) {
                return "Vote karne ke liye pehle apne voter account se login karein. Dashboard par active election open karein (Cast Vote page par), candidates ki details dekhein, apna candidate select karein aur 'Submit Vote' par click karke vote confirm karein.";
            } else {
                return "To vote, please log in to your voter account. Navigate to the 'Cast Vote' page on your dashboard, review the active election details, select your preferred candidate, and click 'Submit Vote'.";
            }
        }

        if (lower.contains("eligib") || lower.contains("who can vote") || lower.contains("qualif") ||
                lower.contains("yogyata") || lower.contains("umra") || lower.contains("age limit")) {
            if (isHindi) {
                return "Vote karne ke liye aapki umra kam se kam 18 varsh honi chahiye, aur aapka VOTE INDIA portal par ek active registered voter account hona chahiye.";
            } else {
                return "To be eligible to vote, you must be a registered voter of VOTE INDIA, at least 18 years of age, and your account status must be active.";
            }
        }

        if (lower.contains("active election") || lower.contains("election details") || lower.contains("konsi election") ||
                lower.contains("kaunsa chunav") || lower.contains("kon sa chunav") || lower.contains("current election")) {
            if (activeElection != null) {
                return isHindi ? "Abhi active chunav hai: \"" + activeElection.getTitle() + "\" (" + activeElection.getDescription() + ")."
                        : "Currently, the active election is: \"" + activeElection.getTitle() + "\" (" + activeElection.getDescription() + ").";
            } else {
                return isHindi ? "Abhi koi active chunav nahi chal raha hai." : "There is no active election currently.";
            }
        }

        if (lower.contains("candidate") || lower.contains("ummeedwar") || lower.contains("neta list") || lower.contains("party list")) {
            if (candidates.isEmpty()) {
                return isHindi ? "Abhi koi registered candidate nahi hai." : "There are no candidates registered at the moment.";
            }
            String names = String.join(", ", candidates.stream().map(c -> c.getName() + " (" + c.getParty() + ")").toList());
            return isHindi ? "Registered candidates hain: " + names : "The registered candidates are: " + names;
        }

        if (lower.contains("already voted") || lower.contains("have i voted") || lower.contains("mera vote") ||
                lower.contains("voted status") || lower.contains("vote de diya")) {
            if (activeElection == null) {
                return isHindi ? "Abhi koi active chunav nahi hai jiski status check ki ja sake." : "There is no active election to check voting status.";
            }
            return hasVoted ? (isHindi ? "Aapne current active chunav mein safalata-purvak vote de diya hai." : "Your vote has already been successfully recorded in the current active election.")
                    : (isHindi ? "Aapne abhi tak current active chunav mein vote nahi diya hai." : "You have not voted in the active election yet.");
        }

        if (lower.contains("result") || lower.contains("scoreboard") || lower.contains("kon jeeta") || lower.contains("kaun jeeta")) {
            return isHindi ? "Aap voter dashboard par 'Results' tab par jaakar purane/closed chunav ke parinam dekh sakte hain."
                    : "You can view the results of past closed elections by navigating to the 'Results' tab on your voter dashboard.";
        }

        return null;
    }

    private String getLanguageName(String langCode) {
        switch (langCode.toLowerCase()) {
            case "hi": return "Hindi";
            case "bn": return "Bengali";
            case "te": return "Telugu";
            case "ta": return "Tamil";
            case "mr": return "Marathi";
            case "gu": return "Gujarati";
            case "kn": return "Kannada";
            case "ml": return "Malayalam";
            case "pa": return "Punjabi";
            case "or": return "Odia";
            default: return "English";
        }
    }
}