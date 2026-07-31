package com.chhavi.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.chhavi.pojo.Candidate;
import com.chhavi.pojo.Election;
import com.chhavi.pojo.User;
import com.chhavi.repository.CandidateRepository;
import com.chhavi.repository.ElectionRepository;
import com.chhavi.repository.UserRepository;
import com.chhavi.repository.VoteRepository;

@Controller
public class AdminController {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;

    public AdminController(UserRepository userRepository, CandidateRepository candidateRepository,
                           ElectionRepository electionRepository, VoteRepository voteRepository) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.electionRepository = electionRepository;
        this.voteRepository = voteRepository;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Principal principal, Model model) {
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        model.addAttribute("user", user);

        long totalUsers = userRepository.count();
        long totalVoters = userRepository.countByRole("VOTER");
        long totalCandidates = candidateRepository.count();

        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);
        long totalVotes = 0;
        long remainingVoters = totalVoters;
        String electionStatus = "No active election";

        if (activeElection != null) {
            totalVotes = voteRepository.countByElectionId(activeElection.getId());
            remainingVoters = totalVoters - totalVotes;
            electionStatus = "Active: " + activeElection.getTitle();
        }

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalVoters", totalVoters);
        model.addAttribute("totalCandidates", totalCandidates);
        model.addAttribute("totalVotes", totalVotes);
        model.addAttribute("remainingVoters", remainingVoters);
        model.addAttribute("electionStatus", electionStatus);
        model.addAttribute("activeElection", activeElection);

        // Winning Candidate calculation
        if (activeElection != null && totalVotes > 0) {
            List<Candidate> candidates = new ArrayList<>();
            if (activeElection.getCandidateIds() != null) {
                candidates = candidateRepository.findAllById(activeElection.getCandidateIds());
            }
            long maxVotes = -1;
            List<Candidate> winners = new ArrayList<>();

            for (Candidate c : candidates) {
                long cvotes = voteRepository.countByElectionIdAndCandidateId(activeElection.getId(), c.getId());
                if (cvotes > maxVotes) {
                    maxVotes = cvotes;
                    winners.clear();
                    winners.add(c);
                } else if (cvotes == maxVotes) {
                    winners.add(c);
                }
            }

            if (winners.size() > 1) {
                model.addAttribute("winnerMessage", "TIE between: " + winners.stream().map(Candidate::getName).collect(java.util.stream.Collectors.joining(", ")));
            } else if (!winners.isEmpty()) {
                model.addAttribute("winnerMessage", winners.get(0).getName() + " (" + winners.get(0).getParty() + ") with " + maxVotes + " votes");
            }
        } else {
            model.addAttribute("winnerMessage", "N/A (No votes cast or no active election)");
        }

        return "admin/dashboard";
    }

    @GetMapping("/admin/users")
    public String viewUsers(
            @RequestParam(required = false) String search,
            Model model) {

        List<User> voters;
        if (search != null && !search.trim().isEmpty()) {
            voters = userRepository.findAllByRole("VOTER").stream()
                    .filter(u -> u.getFullName().toLowerCase().contains(search.toLowerCase()) 
                            || u.getEmail().toLowerCase().contains(search.toLowerCase())
                            || u.getVoterId().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        } else {
            voters = userRepository.findAllByRole("VOTER");
        }

        // Clean user entities to prevent exposing passwords/tokens in HTML
        List<User> safeVoters = new ArrayList<>();
        for (User u : voters) {
            User safe = new User();
            safe.setId(u.getId());
            safe.setFullName(u.getFullName());
            safe.setEmail(u.getEmail());
            safe.setMobile(u.getMobile());
            safe.setVoterId(u.getVoterId());
            safe.setDateOfBirth(u.getDateOfBirth());
            safe.setRole(u.getRole());
            safe.setHasVoted(u.isHasVoted());
            safe.setEmailVerified(u.isEmailVerified());
            safe.setAccountLocked(u.isAccountLocked());
            safe.setStatus(u.getStatus());
            safe.setProfileImage(u.getProfileImage());
            safeVoters.add(safe);
        }

        model.addAttribute("voters", safeVoters);
        model.addAttribute("search", search);
        return "admin/users";
    }

    @PostMapping("/admin/users/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if ("ACTIVE".equals(user.getStatus())) {
            user.setStatus("INACTIVE");
        } else {
            user.setStatus("ACTIVE");
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return "redirect:/admin/users";
    }

    @GetMapping("/admin/statistics")
    public String statistics(Model model) {
        Election activeElection = electionRepository.findByStatus("ACTIVE").orElse(null);
        List<String> labels = new ArrayList<>();
        List<Long> votesData = new ArrayList<>();
        List<Double> percentageData = new ArrayList<>();

        if (activeElection != null) {
            List<Candidate> candidates = new ArrayList<>();
            if (activeElection.getCandidateIds() != null) {
                candidates = candidateRepository.findAllById(activeElection.getCandidateIds());
            }
            long totalVotes = voteRepository.countByElectionId(activeElection.getId());

            for (Candidate c : candidates) {
                long cvotes = voteRepository.countByElectionIdAndCandidateId(activeElection.getId(), c.getId());
                double percentage = totalVotes > 0 ? ((double) cvotes / totalVotes) * 100 : 0.0;
                labels.add(c.getName() + " (" + c.getParty() + ")");
                votesData.add(cvotes);
                percentageData.add(percentage);
            }
            model.addAttribute("electionTitle", activeElection.getTitle());
        } else {
            model.addAttribute("electionTitle", "No Active Election");
        }

        model.addAttribute("chartLabels", labels);
        model.addAttribute("chartVotes", votesData);
        model.addAttribute("chartPercentages", percentageData);

        return "admin/statistics";
    }

    @GetMapping("/admin/results/{id}")
    public String viewResults(@PathVariable String id, Model model) {
        Election election = electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        if (!"CLOSED".equals(election.getStatus())) {
            throw new IllegalArgumentException("Results are only available for closed elections.");
        }

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

        model.addAttribute("election", election);
        model.addAttribute("totalVotes", totalVotes);
        model.addAttribute("candidateResults", candidateResults);
        return "admin/results";
    }
}
