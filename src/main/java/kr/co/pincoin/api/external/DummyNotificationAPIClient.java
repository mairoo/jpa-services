package kr.co.pincoin.api.external;

import kr.co.pincoin.api.dto.OrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@Slf4j
public class DummyNotificationAPIClient implements NotificationAPIClient {
    private final Random random = new Random();

    @Override
    public void notify(OrderRequest request) {
        // 랜덤하게 실패 시뮬레이션 (50% 확률)
        if (random.nextDouble() < 0.5) {
            throw new RuntimeException("Notification sending failed");
        }
        log.info("Notification sent for order: {}", request.getOrderId());
    }
}
