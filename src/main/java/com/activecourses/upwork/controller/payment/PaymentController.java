package com.activecourses.upwork.controller.payment;

import com.activecourses.upwork.dto.PaymentDTO;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Pagamentos", description = "Gestão de Pagamentos Jurídicos")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Criar pagamento", description = "Cria um pagamento a partir de uma milestone concluída",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    @PostMapping("/create/{milestoneId}")
    public ResponseEntity<ResponseDto> createPayment(
            @PathVariable int milestoneId,
            @RequestBody(required = false) Map<String, String> body) {
        String description = body != null ? body.get("description") : null;
        PaymentDTO payment = paymentService.createPayment(milestoneId, description);
        return buildResponse(HttpStatus.CREATED, true, payment, null);
    }

    @Operation(summary = "Meus pagamentos", description = "Lista pagamentos onde o utilizador é cliente ou advogado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<ResponseDto> getMyPayments() {
        var payments = paymentService.getMyPayments();
        return buildResponse(HttpStatus.OK, true, payments, null);
    }

    @Operation(summary = "Pagamentos de um contrato")
    @GetMapping("/contract/{contractId}")
    public ResponseEntity<ResponseDto> getPaymentsByContract(@PathVariable int contractId) {
        var payments = paymentService.getPaymentsByContract(contractId);
        return buildResponse(HttpStatus.OK, true, payments, null);
    }

    @Operation(summary = "Detalhe do pagamento")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseDto> getPaymentById(@PathVariable int paymentId) {
        return paymentService.getPaymentById(paymentId)
                .map(p -> buildResponse(HttpStatus.OK, true, p, null))
                .orElse(buildResponse(HttpStatus.NOT_FOUND, false, null, "Payment not found"));
    }

    @Operation(summary = "Confirmar pagamento", description = "Marca pagamento como concluído",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT')")
    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<ResponseDto> completePayment(@PathVariable int paymentId) {
        PaymentDTO payment = paymentService.completePayment(paymentId);
        return buildResponse(HttpStatus.OK, true, payment, null);
    }

    @Operation(summary = "Reembolsar pagamento", description = "Marca pagamento como reembolsado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ResponseDto> refundPayment(@PathVariable int paymentId) {
        PaymentDTO payment = paymentService.refundPayment(paymentId);
        return buildResponse(HttpStatus.OK, true, payment, null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
