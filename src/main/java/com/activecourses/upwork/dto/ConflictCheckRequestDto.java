package com.activecourses.upwork.dto;

import com.activecourses.upwork.model.ConflictStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictCheckRequestDto {
    @NotNull(message = "O identificador da demanda é obrigatório")
    private Integer jobId;

    private Integer lawyerId;

    private ConflictStatus status;

    private String reason;
}
