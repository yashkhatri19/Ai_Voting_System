package com.chhavi.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.chhavi.pojo.Candidate;

@Repository
public interface CandidateRepository extends MongoRepository<Candidate, String> {
}
