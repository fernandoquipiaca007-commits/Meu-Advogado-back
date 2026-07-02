package com.activecourses.upwork.controller.chat;

import com.activecourses.upwork.dto.ChatMessageDTO;
import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.chat.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Chat", description = "Mensagens entre Clientes e Advogados")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Enviar mensagem", description = "Envia mensagem num contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @PostMapping("/send/{contractId}")
    public ResponseEntity<ResponseDto> sendMessage(
            @PathVariable int contractId,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        ChatMessageDTO sent = chatService.sendMessage(contractId, message);
        return buildResponse(HttpStatus.CREATED, true, sent, null);
    }

    @Operation(summary = "Mensagens do contrato", description = "Lista mensagens de um contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @GetMapping("/messages/{contractId}")
    public ResponseEntity<ResponseDto> getMessages(@PathVariable int contractId) {
        var messages = chatService.getMessages(contractId);
        var unread = chatService.getUnreadCount(contractId);
        return buildResponse(HttpStatus.OK, true, Map.of("messages", messages, "unreadCount", unread), null);
    }

    @Operation(summary = "Marcar como lidas", description = "Marca mensagens de um contrato como lidas",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @PostMapping("/read/{contractId}")
    public ResponseEntity<ResponseDto> markAsRead(@PathVariable int contractId) {
        chatService.markAsRead(contractId);
        return buildResponse(HttpStatus.OK, true, null, null);
    }

    @Operation(summary = "Contagem não lidas", description = "Número de mensagens não lidas num contrato",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CLIENT') or hasRole('LAWYER')")
    @GetMapping("/unread/{contractId}")
    public ResponseEntity<ResponseDto> getUnreadCount(@PathVariable int contractId) {
        long count = chatService.getUnreadCount(contractId);
        return buildResponse(HttpStatus.OK, true, Map.of("count", count), null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
