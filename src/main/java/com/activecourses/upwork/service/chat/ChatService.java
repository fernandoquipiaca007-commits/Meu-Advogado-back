package com.activecourses.upwork.service.chat;

import com.activecourses.upwork.dto.ChatMessageDTO;

import java.util.List;

public interface ChatService {
    ChatMessageDTO sendMessage(int contractId, String message);
    List<ChatMessageDTO> getMessages(int contractId);
    long getUnreadCount(int contractId);
    void markAsRead(int contractId);
}
