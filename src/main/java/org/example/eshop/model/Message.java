package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_message_conversation_id_sent_at", columnList = "conversation_id, sentAt")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) // CUSTOMER or ADMIN
    private SenderType senderType;

    @Column // Může být null, pokud odesílá "systém" nebo neidentifikovaný uživatel
    private Long senderId; // ID Customer nebo Admin User

    @Column(nullable = false)
    private String senderName; // Zobrazované jméno (např. "Zákazník", "Admin XY")

    @Lob // Pro delší texty
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private boolean readByCustomer = false; // Pouze pro EXTERNAL

    @Column(nullable = false)
    private boolean readByAdmin = false;

    public enum SenderType {
        CUSTOMER,
        ADMIN
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return id != null && id.equals(message.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", conversationId=" + (conversation != null ? conversation.getId() : null) +
                ", senderType=" + senderType +
                ", senderName='" + senderName + '\'' +
                ", sentAt=" + sentAt +
                '}';
    }
}