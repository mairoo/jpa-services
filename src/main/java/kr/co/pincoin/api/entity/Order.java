package kr.co.pincoin.api.entity;

import jakarta.persistence.*;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;

    public static Order from(OrderRequest request) {
        return Order.builder()
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .status(OrderStatus.CREATED)
                .build();
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}