package kr.co.pincoin.api.entity;

import jakarta.persistence.*;
import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;
    private BigDecimal amount;
    private String externalReference;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime processedAt;

    public static Payment from(OrderRequest request, APIResponse apiResponse) {
        return Payment.builder()
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .externalReference(apiResponse.getReferenceId())
                .status(convertToPaymentStatus(apiResponse.getStatus()))
                .build();
    }

    private static PaymentStatus convertToPaymentStatus(String status) {
        return switch (status.toUpperCase()) {
            case "APPROVED" -> PaymentStatus.APPROVED;
            case "REJECTED" -> PaymentStatus.REJECTED;
            default -> PaymentStatus.PENDING;
        };
    }

    @PrePersist
    public void prePersist() {
        this.processedAt = LocalDateTime.now();
    }
}
