package com.chhavi.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @GetMapping("/voter/ai-assistant")
    public String showAssistantPage() {
        return "voter/ai-assistant";
    }

    @SuppressWarnings("unchecked")
    @PostMapping(value = "/ai/chat", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public java.util.Map<String, String> processChat(
            Principal principal,
            @RequestParam String message,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            HttpSession session) {
        
        java.util.Map<String, String> responseMap = new java.util.HashMap<>();

        if (message == null || message.trim().isEmpty()) {
            responseMap.put("response", "Message cannot be empty.");
            return responseMap;
        }

        // Initialize/retrieve chat history
        List<java.util.Map<String, Object>> history = (List<java.util.Map<String, Object>>) session.getAttribute("chatHistory");
        if (history == null) {
            history = new ArrayList<>();
        }

        // Add user message to history
        java.util.Map<String, Object> userTurn = new java.util.HashMap<>();
        userTurn.put("role", "user");
        userTurn.put("parts", List.of(java.util.Map.of("text", message)));
        history.add(userTurn);

        // Keep history reasonably sized (last 10 turns)
        if (history.size() > 20) {
            history = new ArrayList<>(history.subList(history.size() - 20, history.size()));
        }

        // 1. PRIVACY GUARD
        if (isPrivateQuery(message)) {
            String guardResponse = "Registered voter information is private and cannot be exposed. I can help with general voting information, election status, or candidate details instead.";
            
            java.util.Map<String, Object> modelTurn = new java.util.HashMap<>();
            modelTurn.put("role", "model");
            modelTurn.put("parts", List.of(java.util.Map.of("text", guardResponse)));
            history.add(modelTurn);
            session.setAttribute("chatHistory", history);

            responseMap.put("response", guardResponse);
            return responseMap;
        }

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);
        List<Candidate> candidates = candidateRepository.findAll();

        boolean isActive = activeElection != null;
        boolean hasVoted = activeElection != null && voterRecordRepository.existsByUserIdAndElectionId(user.getId(), activeElection.getId());

        // 2. LOCAL SAFE RESPONSES FOR CORE PORTAL QUESTIONS
        String localAns = getLocalResponse(message, lang, user, activeElection, hasVoted, candidates);
        if (localAns != null) {
            java.util.Map<String, Object> modelTurn = new java.util.HashMap<>();
            modelTurn.put("role", "model");
            modelTurn.put("parts", List.of(java.util.Map.of("text", localAns)));
            history.add(modelTurn);
            session.setAttribute("chatHistory", history);

            responseMap.put("response", localAns);
            return responseMap;
        }

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
                + "In addition to answering portal-specific questions using the context above, you can also answer general election, civic, and political knowledge questions (e.g., voting age in India, previous Prime Ministers, NOTA, Rajya Sabha/Lok Sabha, Model Code of Conduct, etc.) using your general knowledge.\n"
                + "Strict Neutrality Instructions:\n"
                + "1. Do not tell users who to vote for, do not rank candidates, and do not manipulate choices.\n"
                + "2. Stated promises and manifestos must be summarized neutrally.\n"
                + "3. If asked for recommendations, explain that you are a neutral AI assistant designed only to explain the voting process and candidate manifestos neutrally.\n"
                + "4. Keep answers neutral, objective, and factual.\n"
                + "5. Limit replies to a few sentences.\n"
                + "Please reply entirely in " + langName + " language using its native script.";

        String aiResponse = openRouterClient.generateContent(sysInstruction, history, lang);

        // Add model response to history
        java.util.Map<String, Object> modelTurn = new java.util.HashMap<>();
        modelTurn.put("role", "model");
        modelTurn.put("parts", List.of(java.util.Map.of("text", aiResponse)));
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
        
        // 1. How to vote / voting process
        if (lower.contains("how to vote") || lower.contains("how can i vote") || lower.contains("vote kaise") || 
            lower.contains("vote kr") || lower.contains("vote kese") || lower.contains("voting process")) {
            if (isHindi) {
                return "Vote karne ke liye pehle apne voter account se login karein. Dashboard par active election open karein (Cast Vote page par), candidates ki details dekhein, apna candidate select karein aur 'Submit Vote' par click karke vote confirm karein. Ek election mein aap sirf ek baar vote kar sakte hain.";
            } else {
                return "To vote, please log in to your voter account. Navigate to the 'Cast Vote' page on your dashboard, review the active election details, select your preferred candidate, and click 'Submit Vote'. You are permitted to vote only once per active election.";
            }
        }
        
        // 2. Voting eligibility
        if (lower.contains("eligib") || lower.contains("who can vote") || lower.contains("qualif") || 
            lower.contains("yogyata") || lower.contains("umra") || lower.contains("age limit")) {
            if (isHindi) {
                return "Vote karne ke liye aapki umra kam se kam 18 varsh honi chahiye, aur aapka VOTE INDIA portal par ek active registered voter account hona chahiye.";
            } else {
                return "To be eligible to vote, you must be a registered voter of VOTE INDIA, at least 18 years of age, and your account status must be active.";
            }
        }
        
        // 3. Active election
        if (lower.contains("active election") || lower.contains("election details") || lower.contains("konsi election") || 
            lower.contains("kaunsa chunav") || lower.contains("kon sa chunav") || lower.contains("current election")) {
            if (activeElection != null) {
                if (isHindi) {
                    return "Abhi active chunav hai: \"" + activeElection.getTitle() + "\" (" + activeElection.getDescription() + "). Aap ise apne dashboard par dekh sakte hain.";
                } else {
                    return "Currently, the active election is: \"" + activeElection.getTitle() + "\" (" + activeElection.getDescription() + "). You can view it on your dashboard.";
                }
            } else {
                if (isHindi) {
                    return "Abhi koi active chunav nahi chal raha hai.";
                } else {
                    return "There is no active election currently.";
                }
            }
        }
        
        // 4. View candidates
        if (lower.contains("candidate") || lower.contains("ummeedwar") || lower.contains("neta list") || lower.contains("party list")) {
            if (isHindi) {
                if (candidates.isEmpty()) {
                    return "Abhi koi registered candidate nahi hai.";
                }
                String names = String.join(", ", candidates.stream().map(c -> c.getName() + " (" + c.getParty() + ")").toList());
                return "Aap voter dashboard par 'Candidates' section ya 'Cast Vote' page par candidates ki list dekh sakte hain. Registered candidates hain: " + names;
            } else {
                if (candidates.isEmpty()) {
                    return "There are no candidates registered at the moment.";
                }
                String names = String.join(", ", candidates.stream().map(c -> c.getName() + " (" + c.getParty() + ")").toList());
                return "You can view candidates on your dashboard under the 'Candidates' tab or on the 'Cast Vote' page. The registered candidates are: " + names;
            }
        }
        
        // 5. Already voted status / status check
        if (lower.contains("already voted") || lower.contains("have i voted") || lower.contains("mera vote") || 
            lower.contains("voted status") || lower.contains("vote de diya")) {
            if (activeElection == null) {
                return isHindi ? "Abhi koi active chunav nahi hai jiski status check ki ja sake." : "There is no active election to check voting status.";
            }
            if (hasVoted) {
                return isHindi ? "Aapne current active chunav mein safalata-purvak vote de diya hai." : "Your vote has already been successfully recorded in the current active election.";
            } else {
                return isHindi ? "Aapne abhi tak current active chunav mein vote nahi diya hai. Vote dene ke liye 'Cast Vote' page par jayein." : "You have not voted in the active election yet. Please go to the 'Cast Vote' page to cast your vote.";
            }
        }
        
        // 6. How to view results
        if (lower.contains("result") || lower.contains("scoreboard") || lower.contains("kon jeeta") || lower.contains("kaun jeeta")) {
            if (isHindi) {
                return "Aap voter dashboard par 'Results' tab par jaakar purane/closed chunav ke parinam dekh sakte hain.";
            } else {
                return "You can view the results of past closed elections by navigating to the 'Results' tab on your voter dashboard.";
            }
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
