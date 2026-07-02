package com.activecourses.upwork.controller.notification;

import com.activecourses.upwork.dto.ResponseDto;
import com.activecourses.upwork.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Notificações", description = "Sistema de Notificações")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Notificações não lidas", description = "Lista notificações não lidas do utilizador",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/unread")
    public ResponseEntity<ResponseDto> getUnread() {
        var notifications = notificationService.getUnreadNotifications();
        var count = notificationService.getUnreadCount();
        return buildResponse(HttpStatus.OK, true, Map.of(
            "notifications", notifications,
            "count", count
        ), null);
    }

    @Operation(summary = "Contagem de não lidas", description = "Número de notificações não lidas do utilizador",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/unread/count")
    public ResponseEntity<ResponseDto> getUnreadCount() {
        long count = notificationService.getUnreadCount();
        return buildResponse(HttpStatus.OK, true, Map.of("count", count), null);
    }

    @Operation(summary = "Todas as notificações", description = "Lista todas as notificações do utilizador com paginação",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<ResponseDto> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var notifications = notificationService.getMyNotificationsPaged(pageable);
        return buildResponse(HttpStatus.OK, true, Map.of(
            "notifications", notifications.getContent(),
            "totalPages", notifications.getTotalPages(),
            "totalElements", notifications.getTotalElements()
        ), null);
    }

    @Operation(summary = "Marcar como lida", description = "Marca uma notificação como lida",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ResponseDto> markAsRead(@PathVariable int notificationId) {
        notificationService.markAsRead(notificationId);
        return buildResponse(HttpStatus.OK, true, null, null);
    }

    @Operation(summary = "Marcar todas como lidas", description = "Marca todas as notificações como lidas",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/read-all")
    public ResponseEntity<ResponseDto> markAllAsRead() {
        notificationService.markAllAsRead();
        return buildResponse(HttpStatus.OK, true, null, null);
    }

    private ResponseEntity<ResponseDto> buildResponse(HttpStatus status, boolean success, Object data, Object error) {
        return ResponseEntity.status(status)
                .body(ResponseDto.builder().status(status).success(success).data(data).error(error).build());
    }
}
