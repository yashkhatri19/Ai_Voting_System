package com.chhavi.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.ConcurrentHashMap;
import com.chhavi.ai.OpenRouterClient;
import com.chhavi.dto.CandidateRequestDto;
import com.chhavi.pojo.Candidate;
import com.chhavi.repository.CandidateRepository;
import com.chhavi.repository.VoteRepository;

import jakarta.validation.Valid;

@Controller
public class CandidateController {

    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final OpenRouterClient openRouterClient;
    private final Map<String, String> manifestoSummaryCache = new ConcurrentHashMap<>();

    public CandidateController(CandidateRepository candidateRepository, VoteRepository voteRepository, OpenRouterClient openRouterClient) {
        this.candidateRepository = candidateRepository;
        this.voteRepository = voteRepository;
        this.openRouterClient = openRouterClient;
    }

    // Admin View
    @GetMapping("/admin/candidates")
    public String adminListCandidates(Model model) {
        model.addAttribute("candidates", candidateRepository.findAll());
        return "admin/candidates";
    }

    @GetMapping("/admin/candidates/new")
    public String showCandidateForm(Model model) {
        model.addAttribute("candidateDto", new CandidateRequestDto());
        return "admin/candidate-form";
    }

    @PostMapping("/admin/candidates")
    public String saveCandidate(
            @Valid @ModelAttribute("candidateDto") CandidateRequestDto dto,
            BindingResult result,
            @RequestParam(value = "symbolImageFile", required = false) org.springframework.web.multipart.MultipartFile symbolImageFile,
            Model model) {

        if (result.hasErrors()) {
            return "admin/candidate-form";
        }

        Candidate candidate = new Candidate();
        candidate.setName(dto.getName());
        candidate.setParty(dto.getParty());
        
        if (symbolImageFile != null && !symbolImageFile.isEmpty()) {
            try {
                byte[] bytes = symbolImageFile.getBytes();
                String base64Image = "data:" + symbolImageFile.getContentType() + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
                candidate.setSymbol(base64Image);
            } catch (Exception e) {
                candidate.setSymbol(dto.getSymbol());
            }
        } else {
            candidate.setSymbol(dto.getSymbol());
        }

        candidate.setManifesto(dto.getManifesto());
        candidate.setProfileImage(dto.getProfileImage());
        candidate.setCreatedAt(LocalDateTime.now());
        candidate.setUpdatedAt(LocalDateTime.now());

        candidateRepository.save(candidate);
        return "redirect:/admin/candidates";
    }

    @GetMapping("/admin/candidates/edit/{id}")
    public String showEditCandidateForm(@PathVariable String id, Model model) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        CandidateRequestDto dto = new CandidateRequestDto();
        dto.setName(candidate.getName());
        dto.setParty(candidate.getParty());
        dto.setSymbol(candidate.getSymbol());
        dto.setManifesto(candidate.getManifesto());
        dto.setProfileImage(candidate.getProfileImage());

        model.addAttribute("candidateDto", dto);
        model.addAttribute("candidateId", id);
        return "admin/candidate-form";
    }

    @PostMapping("/admin/candidates/edit/{id}")
    public String updateCandidate(
            @PathVariable String id,
            @Valid @ModelAttribute("candidateDto") CandidateRequestDto dto,
            BindingResult result,
            @RequestParam(value = "symbolImageFile", required = false) org.springframework.web.multipart.MultipartFile symbolImageFile,
            Model model) {

        if (result.hasErrors()) {
            return "admin/candidate-form";
        }

        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        candidate.setName(dto.getName());
        candidate.setParty(dto.getParty());
        
        if (symbolImageFile != null && !symbolImageFile.isEmpty()) {
            try {
                byte[] bytes = symbolImageFile.getBytes();
                String base64Image = "data:" + symbolImageFile.getContentType() + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
                candidate.setSymbol(base64Image);
            } catch (Exception e) {
                // keep old or fallback
            }
        } else {
            if (dto.getSymbol() != null && !dto.getSymbol().trim().isEmpty() && !dto.getSymbol().startsWith("data:image")) {
                candidate.setSymbol(dto.getSymbol());
            }
        }

        candidate.setManifesto(dto.getManifesto());
        candidate.setProfileImage(dto.getProfileImage());
        candidate.setUpdatedAt(LocalDateTime.now());

        candidateRepository.save(candidate);

        // Evict AI cache on update
        manifestoSummaryCache.keySet().removeIf(key -> key.startsWith(id + "_"));

        return "redirect:/admin/candidates";
    }

    @PostMapping("/admin/candidates/delete/{id}")
    public String deleteCandidate(@PathVariable String id, Model model) {
        // Safe candidate deletion fix: check if votes exist for the candidate before deleting
        if (voteRepository.existsByCandidateId(id)) {
            // Safe existing-data strategy: block deletion and present warning
            // To pass message to front end we redirect with parameter or use FlashAttributes.
            // For simple implementation we can delete associated votes if required, or block.
            // As per requirements: "block deletion with a clear admin message, OR implement safe existing-data strategy"
            // Let's block it or delete references. Blocking is safest to prevent data corruption.
            return "redirect:/admin/candidates?error=Cannot+delete+candidate+because+votes+have+already+been+cast+for+them.";
        }
        try {
            candidateRepository.deleteById(id);
        } catch (Exception e) {
            System.err.println("Candidate deletion failed: " + e.getMessage());
        }
        return "redirect:/admin/candidates";
    }

    // Voter View
    @GetMapping("/voter/candidates")
    public String voterListCandidates(Model model) {
        model.addAttribute("candidates", candidateRepository.findAll());
        return "voter/candidates";
    }

    @GetMapping("/voter/candidates/{id}")
    public String voterCandidateDetails(
            @PathVariable String id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Model model) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        model.addAttribute("candidate", candidate);
        model.addAttribute("currentLang", lang);

        String cacheKey = id + "_" + lang;

        // Retrieve or generate manifesto AI summary
        String summary = manifestoSummaryCache.computeIfAbsent(cacheKey, key -> {
            String manifesto = candidate.getManifesto();
            if (manifesto == null || manifesto.trim().isEmpty()) {
                return getLocalizedNoManifestoMessage(lang);
            }
            if (manifesto.length() > 5000) {
                return getLocalizedManifestoTooLongMessage(lang);
            }

            String langName = getLanguageName(lang);
            String sysInstruction = "You are a neutral AI Candidate Manifesto Summarizer. Summarize the provided candidate manifesto neutrally.\n"
                    + "Please format the output strictly using the following markdown sections:\n\n"
                    + "### 1. English Summary\n"
                    + "- **Short Summary**: [Provide a 2-3 sentence overview]\n"
                    + "- **Detailed Summary**: [Provide a detailed paragraph]\n"
                    + "- **Key Promises (Bullet Points)**:\n"
                    + "  - [Promise 1]\n"
                    + "  - [Promise 2]\n"
                    + "  - [Promise 3]\n\n"
                    + "### 2. " + langName + " Summary\n"
                    + "- **संक्षिप्त सारांश (Short Summary)**: [Provide summary in " + langName + " using native script]\n"
                    + "- **विस्तृत सारांश (Detailed Summary)**: [Provide detailed paragraph in " + langName + " using native script]\n"
                    + "- **मुख्य वादे (Key Promises - Bullet Points)**:\n"
                    + "  - [Promise 1 in " + langName + "]\n"
                    + "  - [Promise 2 in " + langName + "]\n"
                    + "  - [Promise 3 in " + langName + "]\n\n"
                    + "Strict Neutrality: Do not endorse, rank, compare, or recommend the candidate. Keep it objective and factual.";
            
            return openRouterClient.generateContent(sysInstruction, manifesto, lang);
        });

        model.addAttribute("summary", summary);
        return "voter/candidate-details";
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

    private String getLocalizedNoManifestoMessage(String lang) {
        switch (lang.toLowerCase()) {
            case "hi": return "उम्मीदवार द्वारा कोई घोषणापत्र प्रदान नहीं किया गया।";
            case "bn": return "প্রার্থীর কোনো ইশতেহার দেওয়া হয়নি।";
            case "te": return "అభ్యర్థి ద్వారా ఎలాంటి మేనిఫెస్టో అందించబడలేదు.";
            case "ta": return "வேட்பாளரால் தேர்தல் அறிக்கை எதுவும் வழங்கப்படவில்லை.";
            case "mr": return "उमेदवाराकडून कोणताही जाहीरनामा सादर केलेला नाही.";
            case "gu": return "ઉમેદવાર દ્વારા કોઈ ઢંઢેરો આપવામાં આવ્યો નથી.";
            case "kn": return "ಅಭ್ಯರ್ಥಿಯಿಂದ ಯಾವುದೇ ಪ್ರಣಾಳಿಕೆಯನ್ನು ಒದಗಿಸಲಾಗಿಲ್ಲ.";
            case "ml": return "സ്ഥാനാര്‍ത്ഥി മാനിഫെസ്റ്റോയൊന്നും നല്‍കിയിട്ടില്ല.";
            case "pa": return "ਉਮੀਦਵਾਰ ਵੱਲੋਂ ਕੋਈ ਮਨੋਰਥ ਪੱਤਰ ਨਹੀਂ ਦਿੱਤਾ ਗਿਆ।";
            case "or": return "ପ୍ରାର୍ଥୀଙ୍କ ଦ୍ୱାରା କୌଣସି ଇସ୍ତାହାର ଦିଆଯାଇ ନାହିଁ।";
            default: return "No manifesto provided by the candidate.";
        }
    }

    private String getLocalizedManifestoTooLongMessage(String lang) {
        switch (lang.toLowerCase()) {
            case "hi": return "घोषणापत्र AI सारांश के लिए बहुत लंबा है।";
            case "bn": return "ইশতেহারটি এআই সারসংক্ষেপের জন্য খুব দীর্ঘ।";
            case "te": return "మేనిఫెస్టో AI సారాంశం కోసం చాలా పొಡవుగా ఉంది.";
            case "ta": return "தேர்தல் அறிக்கை AI சுருக்கத்திற்கு மிகவும் நீளமாக உள்ளது.";
            case "mr": return "जाहीरनामा AI सारांशसाठी खूप मोठा आहे.";
            case "gu": return "ઢંઢેરો AI સારાંશ માટે ખૂબ લાંબો છે.";
            case "kn": return "ಪ್ರಣಾಳಿಕೆಯು AI ಸಾರಾಂಶಕ್ಕಾಗಿ ತುಂಬಾ ಉದ್ದವಾಗಿದೆ.";
            case "ml": return "മാനിഫെസ്റ്റോ AI സംഗ്രഹത്തിന് വളരെ ദൈര്‍ഘ്യമുള്ളതാണ്.";
            case "pa": return "ਮਨੋਰਥ ਪੱਤਰ AI ਸਾਰਾਂਸ਼ ਲਈ ਬਹੁਤ ਲੰਮਾ ਹੈ।";
            case "or": return "ଇସ୍ତାହାରଟି AI ସାରାଂଶ ପାଇଁ ଅତି ଦୀର୍ଘ ଅଟେ।";
            default: return "Manifesto is too long for AI summary.";
        }
    }
}
