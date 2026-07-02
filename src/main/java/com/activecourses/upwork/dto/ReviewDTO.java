package com.activecourses.upwork.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
    private Integer reviewId;
    private Integer contractId;
    private String contractTitle;
    private Integer reviewerId;
    private String reviewerName;
    private String reviewerPhotoUrl;
    private Integer revieweeId;
    private String revieweeName;
    private String revieweePhotoUrl;
    private String revieweeOab;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
