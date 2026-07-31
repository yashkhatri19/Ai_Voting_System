package com.chhavi.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.chhavi.pojo.Candidate;
import com.chhavi.pojo.Election;
import com.chhavi.pojo.User;
import com.chhavi.pojo.Vote;
import com.chhavi.pojo.VoterRecord;
import com.chhavi.repository.CandidateRepository;
import com.chhavi.repository.ElectionRepository;
import com.chhavi.repository.UserRepository;
import com.chhavi.repository.VoteRepository;
import com.chhavi.repository.VoterRecordRepository;

@Service
public class VotingService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final VoteRepository voteRepository;
    private final VoterRecordRepository voterRecordRepository;
    private final com.chhavi.utils.EmailService emailService;

    public VotingService(UserRepository userRepository, CandidateRepository candidateRepository,
                         ElectionRepository electionRepository, VoteRepository voteRepository,
                         VoterRecordRepository voterRecordRepository,
                         com.chhavi.utils.EmailService emailService) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.electionRepository = electionRepository;
        this.voteRepository = voteRepository;
        this.voterRecordRepository = voterRecordRepository;
        this.emailService = emailService;
    }

    public void castVote(String email, String candidateId) {
        // 1. Load and verify user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"VOTER".equals(user.getRole())) {
            throw new IllegalArgumentException("Only voters are allowed to vote.");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalArgumentException("Your account is not active.");
        }

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Please verify your email before voting.");
        }

        // 2. Load and verify active election
        Election election = electionRepository.findByStatus("ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException("There is no active election at this time."));

        // 3. Load and verify candidate
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid candidate selected."));

        // 4. Double vote check
        if (voterRecordRepository.existsByUserIdAndElectionId(user.getId(), election.getId())) {
            throw new IllegalArgumentException("You have already voted in this election.");
        }

        // 5. Cast vote (Anonymously)
        Vote vote = new Vote();
        vote.setCandidate(candidate);
        vote.setElection(election);
        vote.setVoteTime(LocalDateTime.now());
        voteRepository.save(vote);

        // Record the fact that the user has voted
        VoterRecord record = new VoterRecord(user.getId(), election.getId(), LocalDateTime.now());
        voterRecordRepository.save(record);

        // Update user state as voted
        user.setHasVoted(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Send confirmation email
        try {
            emailService.sendVoteConfirmationEmail(user, election, candidate);
        } catch (Exception e) {
            System.err.println("Failed to send vote confirmation email: " + e.getMessage());
        }
    }
}
