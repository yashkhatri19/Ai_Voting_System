package com.chhavi.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.chhavi.dto.ChangePasswordDto;
import com.chhavi.dto.ProfileUpdateDto;
import com.chhavi.pojo.Candidate;
import com.chhavi.pojo.Election;
import com.chhavi.pojo.User;
import com.chhavi.pojo.Vote;
import com.chhavi.repository.CandidateRepository;
import com.chhavi.repository.ElectionRepository;
import com.chhavi.repository.UserRepository;
import com.chhavi.repository.VoteRepository;
import com.chhavi.repository.VoterRecordRepository;
import com.chhavi.service.VotingService;

import jakarta.validation.Valid;

@Controller
public class VoterController {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final VoterRecordRepository voterRecordRepository;
    private final VotingService votingService;
    private final PasswordEncoder passwordEncoder;

    public VoterController(UserRepository userRepository, CandidateRepository candidateRepository,
                           ElectionRepository electionRepository, VoteRepository voteRepository,
                           VoterRecordRepository voterRecordRepository,
                           VotingService votingService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.electionRepository = electionRepository;
        this.voteRepository = voteRepository;
        this.voterRecordRepository = voterRecordRepository;
        this.votingService = votingService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/voter/dashboard")
    public String dashboard(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        model.addAttribute("user", user);

        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);
        model.addAttribute("activeElection", activeElection);

        boolean hasVoted = activeElection != null && voterRecordRepository.existsByUserIdAndElectionId(user.getId(), activeElection.getId());
        model.addAttribute("hasVoted", hasVoted);

        return "voter/dashboard";
    }

    @GetMapping("/voter/profile")
    public String profile(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        
        ProfileUpdateDto profileDto = new ProfileUpdateDto();
        profileDto.setFullName(user.getFullName());
        profileDto.setMobile(user.getMobile());
        profileDto.setDateOfBirth(user.getDateOfBirth());
        profileDto.setProfileImage(user.getProfileImage());

        model.addAttribute("profileDto", profileDto);
        model.addAttribute("changePasswordDto", new ChangePasswordDto());
        model.addAttribute("user", user);
        return "voter/profile";
    }

    @PostMapping("/voter/profile/update")
    public String updateProfile(
            Principal principal,
            @Valid @ModelAttribute("profileDto") ProfileUpdateDto dto,
            BindingResult result,
            @RequestParam(value = "profileImageFile", required = false) org.springframework.web.multipart.MultipartFile profileImageFile,
            Model model) {

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        if (result.hasErrors()) {
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("user", user);
            return "voter/profile";
        }

        // Validate unique mobile if changed
        if (!user.getMobile().equals(dto.getMobile()) && userRepository.existsByMobile(dto.getMobile())) {
            model.addAttribute("error", "Mobile number already in use.");
            model.addAttribute("changePasswordDto", new ChangePasswordDto());
            model.addAttribute("user", user);
            return "voter/profile";
        }

        user.setFullName(dto.getFullName().trim());
        user.setMobile(dto.getMobile().trim());
        user.setDateOfBirth(dto.getDateOfBirth());
        
        if (profileImageFile != null && !profileImageFile.isEmpty()) {
            try {
                byte[] bytes = profileImageFile.getBytes();
                String base64Image = "data:" + profileImageFile.getContentType() + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
                user.setProfileImage(base64Image);
            } catch (Exception e) {
                System.err.println("Failed to encode profile image during profile update: " + e.getMessage());
            }
        }
        
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        return "redirect:/voter/profile?success=true";
    }

    @PostMapping("/voter/profile/change-password")
    public String changePassword(
            Principal principal,
            @Valid @ModelAttribute("changePasswordDto") ChangePasswordDto dto,
            BindingResult result,
            Model model) {

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        if (result.hasErrors()) {
            ProfileUpdateDto profileDto = new ProfileUpdateDto();
            profileDto.setFullName(user.getFullName());
            profileDto.setMobile(user.getMobile());
            profileDto.setDateOfBirth(user.getDateOfBirth());
            profileDto.setProfileImage(user.getProfileImage());
            model.addAttribute("profileDto", profileDto);
            model.addAttribute("user", user);
            return "voter/profile";
        }

        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            model.addAttribute("error", "Incorrect old password.");
            ProfileUpdateDto profileDto = new ProfileUpdateDto();
            profileDto.setFullName(user.getFullName());
            profileDto.setMobile(user.getMobile());
            profileDto.setDateOfBirth(user.getDateOfBirth());
            profileDto.setProfileImage(user.getProfileImage());
            model.addAttribute("profileDto", profileDto);
            model.addAttribute("user", user);
            return "voter/profile";
        }

        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            model.addAttribute("error", "New passwords do not match.");
            ProfileUpdateDto profileDto = new ProfileUpdateDto();
            profileDto.setFullName(user.getFullName());
            profileDto.setMobile(user.getMobile());
            profileDto.setDateOfBirth(user.getDateOfBirth());
            profileDto.setProfileImage(user.getProfileImage());
            model.addAttribute("profileDto", profileDto);
            model.addAttribute("user", user);
            return "voter/profile";
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return "redirect:/voter/profile?passwordSuccess=true";
    }

    @GetMapping("/voter/vote")
    public String showVotePage(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);

        if (activeElection == null) {
            model.addAttribute("error", "There is no active election currently.");
            return "voter/vote";
        }

        boolean hasVoted = voterRecordRepository.existsByUserIdAndElectionId(user.getId(), activeElection.getId());
        if (hasVoted) {
            return "redirect:/voter/vote/status";
        }

        model.addAttribute("activeElection", activeElection);
        List<Candidate> candidates = new ArrayList<>();
        if (activeElection.getCandidateIds() != null) {
            candidates = candidateRepository.findAllById(activeElection.getCandidateIds());
        }
        model.addAttribute("candidates", candidates);
        return "voter/vote";
    }

    @PostMapping("/voter/vote")
    public String submitVote(
            Principal principal,
            @RequestParam String candidateId,
            Model model) {

        try {
            votingService.castVote(principal.getName(), candidateId);
            return "redirect:/voter/vote-success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);
            model.addAttribute("activeElection", activeElection);
            List<Candidate> candidates = new ArrayList<>();
            if (activeElection != null && activeElection.getCandidateIds() != null) {
                candidates = candidateRepository.findAllById(activeElection.getCandidateIds());
            }
            model.addAttribute("candidates", candidates);
            return "voter/vote";
        }
    }

    @GetMapping("/voter/vote-success")
    public String voteSuccess() {
        return "voter/vote-success";
    }

    @GetMapping("/voter/vote/status")
    public String voteStatus(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);

        if (activeElection == null) {
            model.addAttribute("statusMessage", "No active election at the moment.");
            return "voter/vote-status";
        }

        boolean hasVoted = voterRecordRepository.existsByUserIdAndElectionId(user.getId(), activeElection.getId());
        if (hasVoted) {
            model.addAttribute("statusMessage", "Your vote has been recorded successfully.");
        } else {
            model.addAttribute("statusMessage", "You have not voted in the active election yet.");
        }

        return "voter/vote-status";
    }

    @GetMapping("/voter/results")
    public String viewResults(Model model) {
        List<Election> closedElections = electionRepository.findAllByStatus("CLOSED");

        model.addAttribute("closedElections", closedElections);

        Map<String, List<Map<String, Object>>> electionResultsMap = new HashMap<>();
        for (Election election : closedElections) {
            List<Candidate> candidates = new ArrayList<>();
            if (election.getCandidateIds() != null) {
                candidates = candidateRepository.findAllById(election.getCandidateIds());
            }
            long totalVotes = voteRepository.countByElectionId(election.getId());
            List<Map<String, Object>> candidateResults = new ArrayList<>();

            for (Candidate candidate : candidates) {
                long votes = voteRepository.countByElectionIdAndCandidateId(election.getId(), candidate.getId());
                double percentage = totalVotes > 0 ? ((double) votes / totalVotes) * 100 : 0.0;

                Map<String, Object> cMap = new HashMap<>();
                cMap.put("name", candidate.getName());
                cMap.put("party", candidate.getParty());
                cMap.put("votes", votes);
                cMap.put("percentage", percentage);
                candidateResults.add(cMap);
            }
            electionResultsMap.put(election.getId(), candidateResults);
        }

        model.addAttribute("resultsMap", electionResultsMap);
        return "voter/results";
    }
}
