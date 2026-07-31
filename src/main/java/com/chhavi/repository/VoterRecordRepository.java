package com.chhavi.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.chhavi.pojo.VoterRecord;

@Repository
public interface VoterRecordRepository extends MongoRepository<VoterRecord, String> {
    boolean existsByUserIdAndElectionId(String userId, String electionId);
    Optional<VoterRecord> findByUserIdAndElectionId(String userId, String electionId);
    void deleteByElectionId(String electionId);
}
