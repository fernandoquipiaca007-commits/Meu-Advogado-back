package com.activecourses.upwork.controller.conflict;

import com.activecourses.upwork.dto.ConflictCheckDto;
import com.activecourses.upwork.dto.ConflictCheckRequestDto;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.service.conflict.ConflictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Conflito de Interesses", description = "Verificação e declaração de impedimentos ético-profissionais")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conflicts")
public class ConflictController {

    private final ConflictService conflictService;
    private final AuthService authService;

    @Operation(summary = "Verificar conflito de interesses", description = "Verifica ou inicializa a checagem de conflito para uma demanda",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/check")
    public ResponseEntity<ResponseDto> checkConflict(@Valid @RequestBody ConflictCheckRequestDto request) {
        ConflictCheckDto dto = conflictService.checkConflict(request.getJobId(), request.getLawyerId());
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(dto)
                .build());
    }

    @Operation(summary = "Declarar status de conflito", description = "Advogado declara seu status de conflito para uma demanda",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('LAWYER') or hasRole('FREELANCER')")
    @PostMapping("/declare")
    public ResponseEntity<ResponseDto> declareConflict(@Valid @RequestBody ConflictCheckRequestDto request) {
        ConflictCheckDto dto = conflictService.declareConflict(request);
        return ResponseEntity.ok(ResponseDto.builder()
                .status(HttpStatus.OK)
                .success(true)
                .data(dto)
                .build());
    }

    @Operation(summary = "Obter status de conflito por demanda", description = "Retorna status de conflito registrado para a demanda e o advogado autenticado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ResponseDto> getConflictByJob(@PathVariable Integer jobId) {
        Integer currentUserId = authService.getCurrentUserId();
        return conflictService.getConflictStatus(jobId, currentUserId)
                .map(dto -> ResponseEntity.ok(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(dto)
                        .build()))
                .orElse(ResponseEntity.ok(ResponseDto.builder()
                        .status(HttpStatus.OK)
                        .success(true)
                        .data(null)
                        .build()));
    }
}
