package com.activecourses.upwork.repository.delivery;

import com.activecourses.upwork.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {
    List<Review> findByContractContractId(Integer contractId);
    List<Review> findByRevieweeId(Integer revieweeId);
    boolean existsByContractContractIdAndReviewerIdAndRevieweeId(
            Integer contractId, Integer reviewerId, Integer revieweeId);
}
