package com.activecourses.upwork.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptContractRequestDto {
    @NotNull(message = "O identificador da proposta é obrigatório")
    private Integer proposalId;

    @Builder.Default
    private String termsVersion = "v1.0";

    private String notes;

    private Boolean signatureConsent;
}
