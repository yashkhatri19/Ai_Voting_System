package com.chhavi.pojo;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "votes")
public class Vote {

    @Id
    private String id;

    @DBRef
    private Candidate candidate;

    @DBRef
    private Election election;

    private LocalDateTime voteTime;

    public Vote() {
    }

    public Vote(String id, Candidate candidate, Election election, LocalDateTime voteTime) {
        this.id = id;
        this.candidate = candidate;
        this.election = election;
        this.voteTime = voteTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public Election getElection() {
        return election;
    }

    public void setElection(Election election) {
        this.election = election;
    }

    public LocalDateTime getVoteTime() {
        return voteTime;
    }

    public void setVoteTime(LocalDateTime voteTime) {
        this.voteTime = voteTime;
    }

    @Override
    public String toString() {
        return "Vote [id=" + id + ", voteTime=" + voteTime + "]";
    }
}