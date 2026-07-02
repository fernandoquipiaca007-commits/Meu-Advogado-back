package com.activecourses.upwork.service.review;

import com.activecourses.upwork.dto.ReviewDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.review.ReviewRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ReviewDTO createReview(int contractId, int revieweeId, int rating, String comment) {
        Integer reviewerId = authService.getCurrentUserId();
        if (reviewerId == null) throw new IllegalStateException("Not authenticated");

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));

        // Verify reviewer is part of the contract
        boolean isParticipant = contract.getClient().getId().equals(reviewerId)
                || contract.getLawyer().getId().equals(reviewerId);
        if (!isParticipant) {
            throw new SecurityException("You can only review contracts you participated in");
        }

        // Verify reviewee is the other party
        boolean validReviewee = contract.getClient().getId().equals(revieweeId)
                || contract.getLawyer().getId().equals(revieweeId);
        if (!validReviewee) {
            throw new IllegalArgumentException("Reviewee must be a party in this contract");
        }

        // Cannot review yourself
        if (reviewerId.equals(revieweeId)) {
            throw new IllegalArgumentException("You cannot review yourself");
        }

        // Check for duplicate review
        Optional<Review> existing = reviewRepository
                .findByContractContractIdAndReviewerId(contractId, reviewerId);
        if (existing.isPresent()) {
            throw new IllegalStateException("You have already reviewed this contract");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));
        User reviewee = userRepository.findById(revieweeId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewee not found"));

        Review review = Review.builder()
                .contract(contract)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(rating)
                .comment(comment)
                .build();

        review = reviewRepository.save(review);

        // Notify reviewee about new review
        notificationService.createNotification(
                reviewee.getId(),
                NotificationType.REVIEW_RECEIVED,
                "Nova avaliação recebida",
                reviewer.getFirstName() + " " + reviewer.getLastName() + " avaliou-o com " + rating + " estrelas"
                        + (comment != null && !comment.isEmpty() ? ": \"" + comment + "\"" : "."),
                "contract",
                contract.getContractId()
        );

        return mapToDTO(review);
    }

    @Override
    public List<ReviewDTO> getReviewsByContract(int contractId) {
        return reviewRepository.findByContractContractId(contractId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ReviewDTO> getReviewsByUser(int userId) {
        return reviewRepository.findByRevieweeId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ReviewDTO> getMyReceivedReviews() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return List.of();
        return getReviewsByUser(userId);
    }

    @Override
    public List<ReviewDTO> getMyGivenReviews() {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return List.of();
        return reviewRepository.findByReviewerId(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ReviewDTO> getReviewById(int reviewId) {
        return reviewRepository.findById(reviewId).map(this::mapToDTO);
    }

    private ReviewDTO mapToDTO(Review review) {
        UserProfile reviewerProfile = review.getReviewer().getUserProfile();
        UserProfile revieweeProfile = review.getReviewee().getUserProfile();

        return ReviewDTO.builder()
                .reviewId(review.getReviewId())
                .contractId(review.getContract().getContractId())
                .contractTitle(review.getContract().getTitle())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName())
                .reviewerPhotoUrl(reviewerProfile != null ? reviewerProfile.getPhotoUrl() : null)
                .revieweeId(review.getReviewee().getId())
                .revieweeName(review.getReviewee().getFirstName() + " " + review.getReviewee().getLastName())
                .revieweePhotoUrl(revieweeProfile != null ? revieweeProfile.getPhotoUrl() : null)
                .revieweeOab(revieweeProfile != null ? revieweeProfile.getOabNumber() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
