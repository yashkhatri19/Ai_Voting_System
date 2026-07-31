package com.chhavi.pojo;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "voter_records")
@CompoundIndex(name = "user_election_idx", def = "{'userId': 1, 'electionId': 1}", unique = true)
public class VoterRecord {

    @Id
    private String id;

    private String userId;

    private String electionId;

    private LocalDateTime castTime;

    public VoterRecord() {
    }

    public VoterRecord(String userId, String electionId, LocalDateTime castTime) {
        this.userId = userId;
        this.electionId = electionId;
        this.castTime = castTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getElectionId() {
        return electionId;
    }

    public void setElectionId(String electionId) {
        this.electionId = electionId;
    }

    public LocalDateTime getCastTime() {
        return castTime;
    }

    public void setCastTime(LocalDateTime castTime) {
        this.castTime = castTime;
    }
}
