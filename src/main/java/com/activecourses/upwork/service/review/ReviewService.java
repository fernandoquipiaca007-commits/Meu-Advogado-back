package com.activecourses.upwork.service.review;

import com.activecourses.upwork.dto.ReviewDTO;

import java.util.List;
import java.util.Optional;

public interface ReviewService {
    ReviewDTO createReview(int contractId, int revieweeId, int rating, String comment);
    List<ReviewDTO> getReviewsByContract(int contractId);
    List<ReviewDTO> getReviewsByUser(int userId);
    List<ReviewDTO> getMyReceivedReviews();
    List<ReviewDTO> getMyGivenReviews();
    Optional<ReviewDTO> getReviewById(int reviewId);
}
