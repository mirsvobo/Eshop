package org.example.eshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "conversation", indexes = {
        @Index(name = "idx_conversation_order_id_type", columnList = "order_id, type")
})
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) // INTERNAL or EXTERNAL
    private ConversationType type;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sentAt ASC") // Zobrazí zprávy od nejstarší
    private List<Message> messages = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Enum pro typ konverzace
    public enum ConversationType {
        INTERNAL, // Pouze pro CMS
        EXTERNAL  // Mezi zákazníkem a obchodem
    }

    // Metody pro přidání zprávy (convenience method)
    public void addMessage(Message message) {
        messages.add(message);
        message.setConversation(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id=" + id +
                ", orderId=" + (order != null ? order.getId() : null) +
                ", type=" + type +
                ", messageCount=" + (messages != null ? messages.size() : 0) +
                ", createdAt=" + createdAt +
                '}';
    }
}