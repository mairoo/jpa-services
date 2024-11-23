package kr.co.pincoin.api.service;

import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.external.NotificationAPIClient;
import kr.co.pincoin.api.logger.TransactionLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationAPIClient notificationAPIClient;

    private final TransactionLogger transactionLogger;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendNotification(OrderRequest request) {
        try {
            CompletableFuture.runAsync(() -> {
                notificationAPIClient.notify(request);
                transactionLogger.logTransaction("Second External API call (Notification)", request);
            });
        } catch (Exception e) {
            // 4번 실패는 이전 트랜잭션에 영향 없음
            log.error("Notification API call failed but continuing", e);
        }
    }
}

