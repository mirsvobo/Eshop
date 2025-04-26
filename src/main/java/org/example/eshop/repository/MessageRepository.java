package org.example.eshop.repository;

import org.example.eshop.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);

     long countByConversationIdAndReadByAdminFalse(Long conversationId);
     long countByConversationIdAndReadByCustomerFalse(Long conversationId);
}