package kr.co.pincoin.api.service;


import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.exception.ExternalAPIException;
import kr.co.pincoin.api.exception.OrderProcessingException;
import kr.co.pincoin.api.exception.PaymentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFacade {
    private final OrderService orderService;

    private final ExternalAPIService externalAPIService;

    private final PaymentService paymentService;

    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRED)
    public void processOrder(OrderRequest request) {
        try {
            // 1. DB 트랜잭션 수행
            orderService.createOrder(request);

            try {
                // 2. 첫 번째 비동기 외부 API 호출 및 로깅
                CompletableFuture<APIResponse> apiResponseFuture = externalAPIService.processAndLog(request);
                APIResponse apiResponse = apiResponseFuture.get(30, TimeUnit.SECONDS);

                try {
                    // 3. API 응답을 기반으로 DB 트랜잭션 수행
                    paymentService.processPayment(request, apiResponse);

                    // 4. 두 번째 비동기 외부 API 호출 (통보)
                    notificationService.sendNotification(request);

                } catch (Exception e) {
                    // 3번 실패 시: 2번 취소 API 호출 후 1번 롤백
                    log.error("Payment processing failed. Initiating compensation.", e);
                    externalAPIService.cancelProcess(request);
                    throw new PaymentProcessingException("Payment failed, rolled back previous operations", e);
                }
            } catch (Exception e) {
                // 2번 실패 시: 1번 롤백
                if (e instanceof PaymentProcessingException) {
                    throw e;
                }
                log.error("First external API call failed.", e);
                throw new ExternalAPIException("External API call failed, rolling back order", e);
            }
        } catch (Exception e) {
            log.error("Order processing failed completely", e);
            throw new OrderProcessingException("Order processing failed", e);
        }
    }
}