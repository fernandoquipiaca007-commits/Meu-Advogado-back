package com.activecourses.upwork.service.chat;

import com.activecourses.upwork.dto.ChatMessageDTO;
import com.activecourses.upwork.model.*;
import com.activecourses.upwork.repository.chat.ChatMessageRepository;
import com.activecourses.upwork.repository.contract.ContractRepository;
import com.activecourses.upwork.repository.user.UserRepository;
import com.activecourses.upwork.service.authentication.AuthService;
import com.activecourses.upwork.model.NotificationType;
import com.activecourses.upwork.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ChatMessageDTO sendMessage(int contractId, String message) {
        Integer senderId = authService.getCurrentUserId();
        if (senderId == null) throw new IllegalStateException("Not authenticated");

        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found"));

        // Verify sender is a participant
        boolean isParticipant = contract.getClient().getId().equals(senderId)
                || contract.getLawyer().getId().equals(senderId);
        if (!isParticipant) {
            throw new SecurityException("You can only send messages in contracts you participate in");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChatMessage chatMessage = ChatMessage.builder()
                .contract(contract)
                .sender(sender)
                .message(message.trim())
                .build();

        chatMessage = chatMessageRepository.save(chatMessage);

        // Notify the other party
        Integer otherPartyId = contract.getClient().getId().equals(senderId)
                ? contract.getLawyer().getId()
                : contract.getClient().getId();

        notificationService.createNotification(
                otherPartyId,
                NotificationType.NEW_MESSAGE,
                "Nova mensagem",
                sender.getFirstName() + " " + sender.getLastName()
                        + " enviou uma mensagem em: " + contract.getTitle(),
                "contract",
                contract.getContractId()
        );

        return mapToDTO(chatMessage);
    }

    @Override
    public List<ChatMessageDTO> getMessages(int contractId) {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return Collections.emptyList();

        // Verify participation
        Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null) return Collections.emptyList();

        boolean isParticipant = contract.getClient().getId().equals(userId)
                || contract.getLawyer().getId().equals(userId);
        if (!isParticipant) return Collections.emptyList();

        return chatMessageRepository.findByContractContractIdOrderByCreatedAtAsc(contractId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(int contractId) {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return 0;
        return chatMessageRepository
                .countByContractContractIdAndIsReadFalseAndSenderIdNot(contractId, userId);
    }

    @Override
    @Transactional
    public void markAsRead(int contractId) {
        Integer userId = authService.getCurrentUserId();
        if (userId == null) return;

        List<ChatMessage> unreadMessages = chatMessageRepository
                .findByContractContractIdOrderByCreatedAtAsc(contractId).stream()
                .filter(m -> !m.isRead() && !m.getSender().getId().equals(userId))
                .collect(Collectors.toList());

        unreadMessages.forEach(m -> {
            m.setRead(true);
            m.setReadAt(LocalDateTime.now());
        });
        chatMessageRepository.saveAll(unreadMessages);
    }

    private ChatMessageDTO mapToDTO(ChatMessage chatMessage) {
        UserProfile profile = chatMessage.getSender().getUserProfile();
        return ChatMessageDTO.builder()
                .messageId(chatMessage.getMessageId())
                .contractId(chatMessage.getContract().getContractId())
                .senderId(chatMessage.getSender().getId())
                .senderName(chatMessage.getSender().getFirstName() + " " + chatMessage.getSender().getLastName())
                .senderPhotoUrl(profile != null ? profile.getPhotoUrl() : null)
                .message(chatMessage.getMessage())
                .isRead(chatMessage.isRead())
                .readAt(chatMessage.getReadAt())
                .createdAt(chatMessage.getCreatedAt())
                .build();
    }
}
