package com.chhavi.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.chhavi.pojo.Election;
import com.chhavi.pojo.User;
import com.chhavi.pojo.Candidate;
import com.chhavi.repository.ElectionRepository;
import com.chhavi.repository.UserRepository;
import com.chhavi.repository.CandidateRepository;
import com.chhavi.repository.VoteRepository;
import com.chhavi.repository.VoterRecordRepository;
import com.chhavi.utils.EmailService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ElectionController {

    private final ElectionRepository electionRepository;
    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final VoteRepository voteRepository;
    private final VoterRecordRepository voterRecordRepository;
    private final EmailService emailService;

    public ElectionController(ElectionRepository electionRepository,
                              UserRepository userRepository,
                              CandidateRepository candidateRepository,
                              VoteRepository voteRepository,
                              VoterRecordRepository voterRecordRepository,
                              EmailService emailService) {
        this.electionRepository = electionRepository;
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.voteRepository = voteRepository;
        this.voterRecordRepository = voterRecordRepository;
        this.emailService = emailService;
    }

    @GetMapping("/admin/elections")
    public String listElections(Model model) {
        model.addAttribute("elections", electionRepository.findAll());
        return "admin/elections";
    }

    @GetMapping("/admin/elections/new")
    public String showElectionForm(Model model) {
        model.addAttribute("election", new Election());
        model.addAttribute("candidates", candidateRepository.findAll());
        return "admin/election-form";
    }

    @PostMapping("/admin/elections")
    public String saveElection(@ModelAttribute("election") Election election, Model model) {
        if (election.getTitle() == null || election.getTitle().trim().isEmpty()) {
            model.addAttribute("error", "Election title is required");
            return "admin/election-form";
        }
        if (election.getStartDate() == null || election.getEndDate() == null) {
            model.addAttribute("error", "Start and End dates are required");
            return "admin/election-form";
        }
        if (election.getEndDate().isBefore(election.getStartDate())) {
            model.addAttribute("error", "End date must be after start date");
            return "admin/election-form";
        }

        election.setStatus("SCHEDULED");
        election.setCreatedAt(LocalDateTime.now());
        election.setUpdatedAt(LocalDateTime.now());

        electionRepository.save(election);
        return "redirect:/admin/elections";
    }

    @PostMapping("/admin/elections/start/{id}")
    public String startElection(@PathVariable String id, Model model) {
        boolean activeExists = electionRepository.existsByStatus("ACTIVE");
        if (activeExists) {
            model.addAttribute("error", "Another election is already active.");
            model.addAttribute("elections", electionRepository.findAll());
            return "admin/elections";
        }

        Election election = electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        election.setStatus("ACTIVE");
        election.setUpdatedAt(LocalDateTime.now());
        electionRepository.save(election);

        // Notify all registered users
        List<User> voters = userRepository.findAll();
        for (User voter : voters) {
            try {
                emailService.sendElectionNotificationEmail(voter, election);
            } catch (Exception e) {
                System.err.println("Failed to send election start notification email to " + voter.getEmail() + ": " + e.getMessage());
            }
        }

        return "redirect:/admin/elections";
    }

    @PostMapping("/admin/elections/end/{id}")
    public String endElection(@PathVariable String id) {
        Election election = electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        election.setStatus("CLOSED");
        election.setUpdatedAt(LocalDateTime.now());
        electionRepository.save(election);

        // Calculate results for scoreboard
        List<Candidate> candidates = new ArrayList<>();
        if (election.getCandidateIds() != null) {
            candidates = candidateRepository.findAllById(election.getCandidateIds());
        }
        long totalVotes = voteRepository.countByElectionId(id);
        List<Map<String, Object>> candidateResults = new ArrayList<>();

        for (Candidate candidate : candidates) {
            long votes = voteRepository.countByElectionIdAndCandidateId(id, candidate.getId());
            double percentage = totalVotes > 0 ? ((double) votes / totalVotes) * 100 : 0.0;

            Map<String, Object> cMap = new HashMap<>();
            cMap.put("name", candidate.getName());
            cMap.put("party", candidate.getParty());
            cMap.put("votes", votes);
            cMap.put("percentage", percentage);
            candidateResults.add(cMap);
        }

        // Send results email to all registered users
        List<User> voters = userRepository.findAll();
        for (User voter : voters) {
            try {
                emailService.sendElectionResultEmail(voter, election, candidateResults);
            } catch (Exception e) {
                System.err.println("Failed to send election result email to " + voter.getEmail() + ": " + e.getMessage());
            }
        }

        return "redirect:/admin/elections";
    }

    @PostMapping("/admin/elections/delete/{id}")
    public String deleteElection(@PathVariable String id) {
        electionRepository.deleteById(id);
        voteRepository.deleteByElectionId(id);
        voterRecordRepository.deleteByElectionId(id);
        return "redirect:/admin/elections";
    }
}
