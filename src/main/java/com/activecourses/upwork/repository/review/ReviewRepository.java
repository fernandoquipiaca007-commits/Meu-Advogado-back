package com.activecourses.upwork.repository.review;

import com.activecourses.upwork.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByContractContractId(int contractId);
    List<Review> findByReviewerId(int reviewerId);
    List<Review> findByRevieweeId(int revieweeId);
    Optional<Review> findByContractContractIdAndReviewerId(int contractId, int reviewerId);
    // Phase 6 — blind review: check if a specific reviewer→reviewee pair already submitted
    boolean existsByContractContractIdAndReviewerIdAndRevieweeId(
            Integer contractId, Integer reviewerId, Integer revieweeId);
}
