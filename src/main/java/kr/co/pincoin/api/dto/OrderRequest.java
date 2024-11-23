package kr.co.pincoin.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String orderId;
    private BigDecimal amount;
    private String customerEmail;

    public static OrderRequest sample() {
        return OrderRequest.builder()
                .orderId(UUID.randomUUID().toString())
                .amount(new BigDecimal("100.00"))
                .customerEmail("test@example.com")
                .build();
    }
}
