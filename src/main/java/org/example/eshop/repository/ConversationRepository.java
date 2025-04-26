package org.example.eshop.repository;

import org.example.eshop.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Najde konverzace pro danou objednávku
    List<Conversation> findByOrderIdOrderByTypeAsc(Long orderId);

    // Najde konkrétní typ konverzace pro objednávku
    Optional<Conversation> findByOrderIdAndType(Long orderId, Conversation.ConversationType type);

    // Optimalizovaná metoda pro načtení konverzace včetně zpráv
    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages m WHERE c.id = :conversationId ORDER BY m.sentAt ASC")
    Optional<Conversation> findByIdWithMessages(@Param("conversationId") Long conversationId);

    // Optimalizovaná metoda pro načtení konverzací pro objednávku včetně zpráv
    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages m WHERE c.order.id = :orderId ORDER BY c.type ASC, m.sentAt ASC")
    List<Conversation> findByOrderIdWithMessages(@Param("orderId") Long orderId);
}