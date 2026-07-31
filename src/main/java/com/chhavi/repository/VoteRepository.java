package com.chhavi.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.chhavi.pojo.Vote;

@Repository
public interface VoteRepository extends MongoRepository<Vote, String> {
    long countByElectionId(String electionId);
    long countByElectionIdAndCandidateId(String electionId, String candidateId);
    void deleteByElectionId(String electionId);
    void deleteByCandidateId(String candidateId);
    boolean existsByCandidateId(String candidateId);
}
