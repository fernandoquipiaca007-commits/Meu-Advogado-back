package com.activecourses.upwork.repository.chat;

import com.activecourses.upwork.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {
    List<ChatMessage> findByContractContractIdOrderByCreatedAtAsc(int contractId);
    long countByContractContractIdAndIsReadFalseAndSenderIdNot(int contractId, int senderId);
}
