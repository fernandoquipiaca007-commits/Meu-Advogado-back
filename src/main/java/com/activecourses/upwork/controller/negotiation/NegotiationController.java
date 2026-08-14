package com.activecourses.upwork.controller.negotiation;

import com.activecourses.upwork.dto.NegotiationMessageDTO;
import com.activecourses.upwork.dto.PageResponseDto;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.dto.SendNegotiationMessageRequest;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.negotiation.NegotiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Negociação Pré-Contratual", description = "Pre-contractual Negotiation and Masked Messaging API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/negotiations")
public class NegotiationController {

    private final NegotiationService negotiationService;
    private final AuthService authService;

    @Operation(summary = "Enviar mensagem de negociação", description = "Send a pre-contractual negotiation message with automatic PII masking",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{proposalId}/messages")
    public ResponseEntity<ResponseDto> sendMessage(
            @PathVariable int proposalId,
            @Valid @RequestBody SendNegotiationMessageRequest request) {

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.UNAUTHORIZED)
                            .success(false)
                            .error("Não autenticado.")
                            .build());
        }

        NegotiationMessageDTO messageDto = negotiationService.sendMessage(proposalId, currentUserId, request.getContent());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDto.builder()
                        .status(HttpStatus.CREATED)
                        .success(true)
                        .data(messageDto)
                        .build());
    }

    @Operation(summary = "Listar mensagens de negociação", description = "Retrieve paginated negotiation messages for proposal participants",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{proposalId}/messages")
    public ResponseEntity<ResponseDto> getMessages(
            @PathVariable int proposalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Integer currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ResponseDto.builder()
                            .status(HttpStatus.UNAUTHORIZED)
                            .success(false)
                            .error("Não autenticado.")
                            .build());
        }

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sentAt"));
        Page<NegotiationMessageDTO> messagesPage = negotiationService.getMessages(proposalId, currentUserId, pageable);

        return ResponseEntity.ok(
                ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(PageResponseDto.from(messagesPage))
                        .build()
        );
    }
}
