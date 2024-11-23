package kr.co.pincoin.api.external;

import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.exception.ExternalAPIException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
public class DummyExternalAPIClient implements ExternalAPIClient {
    private final Random random = new Random();

    @Override
    public APIResponse processAsync(OrderRequest request) {
        // 랜덤하게 실패 시뮬레이션 (50% 확률)
        simulateRandomDelay();
        if (random.nextDouble() < 0.5) {
            throw new ExternalAPIException("External API processing failed");
        }

        return APIResponse.builder()
                .referenceId(UUID.randomUUID().toString())
                .status("APPROVED")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public void cancelAsync(OrderRequest request) {
        simulateRandomDelay();
        log.info("Cancelled external API process for order: {}", request.getOrderId());
    }

    private void simulateRandomDelay() {
        try {
            Thread.sleep(random.nextInt(1000) + 500); // 500-1500ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
