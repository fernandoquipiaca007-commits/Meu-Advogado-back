package com.activecourses.upwork.controller.review;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.ReviewDTO;
import com.activecourses.upwork.service.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Avaliações", description = "Sistema de Avaliações Jurídicas")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews/")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Criar avaliação", description = "Avalia a outra parte após conclusão do contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER') or hasRole('ADMIN')")
    @PostMapping("/create/{contractId}")
    public ResponseEntity<ResponseDto> createReview(
            @PathVariable int contractId,
            @RequestBody Map<String, Object> body) {
        int revieweeId = (int) body.get("revieweeId");
        int rating = (int) body.get("rating");
        String comment = (String) body.get("comment");

        ReviewDTO review = reviewService.createReview(contractId, revieweeId, rating, comment);
        return buildResponse(HttpStatus.CREATED, true, review, null);
    }

    @Operation(summary = "Avaliações recebidas", description = "Avaliações que outros fizeram sobre mim",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/received")
    public ResponseEntity<ResponseDto> getMyReceivedReviews() {
        var reviews = reviewService.getMyReceivedReviews();
        return buildResponse(HttpStatus.OK, true, reviews, null);
    }

    @Operation(summary = "Avaliações que fiz", description = "Avaliações que eu fiz sobre outros",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/given")
    public ResponseEntity<ResponseDto> getMyGivenReviews() {
        var reviews = reviewService.getMyGivenReviews();
        return buildResponse(HttpStatus.OK, true, reviews, null);
    }

    @Operation(summary = "Avaliações de um contrato")
    @GetMapping("/contract/{contractId}")
    public ResponseEntity<ResponseDto> getReviewsByContract(@PathVariable int contractId) {
        var reviews = reviewService.getReviewsByContract(contractId);
        return buildResponse(HttpStatus.OK, true, reviews, null);
    }

    @Operation(summary = "Avaliações de um utilizador")
    @GetMapping("/user/{userId}")
    public ResponseEntity<ResponseDto> getReviewsByUser(@PathVariable int userId) {
        var reviews = reviewService.getReviewsByUser(userId);
        return buildResponse(HttpStatus.OK, true, reviews, null);
    }

    @Operation(summary = "Detalhe da avaliação")
    @GetMapping("/{reviewId}")
    public ResponseEntity<ResponseDto> getReviewById(@PathVariable int reviewId) {
        return reviewService.getReviewById(reviewId)
                .map(r -> buildResponse(HttpStatus.OK, true, r, null))
                .orElse(buildResponse(HttpStatus.NOT_FOUND, false, null, "Review not found"));
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
