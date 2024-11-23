package kr.co.pincoin.api.service;

import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.entity.Order;
import kr.co.pincoin.api.entity.Payment;
import kr.co.pincoin.api.exception.ExternalAPIException;
import kr.co.pincoin.api.exception.OrderProcessingException;
import kr.co.pincoin.api.exception.PaymentProcessingException;
import kr.co.pincoin.api.external.ExternalAPIClient;
import kr.co.pincoin.api.external.NotificationAPIClient;
import kr.co.pincoin.api.logger.TransactionLogger;
import kr.co.pincoin.api.repository.OrderRepository;
import kr.co.pincoin.api.repository.PaymentRepository;
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
public class OrderProcessingService {
    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final ExternalAPIClient externalAPIClient;

    private final NotificationAPIClient notificationAPIClient;

    private final TransactionLogger transactionLogger;

    @Transactional(propagation = Propagation.REQUIRED)
    public void processOrder(OrderRequest request) {
        try {
            // 1. DB 트랜잭션 수행
            createInitialOrder(request);

            try {
                // 2. 첫 번째 비동기 외부 API 호출 및 로깅
                CompletableFuture<APIResponse> apiResponseFuture = executeFirstExternalAPICall(request);
                APIResponse apiResponse = apiResponseFuture.get(30, TimeUnit.SECONDS);

                try {
                    // 3. API 응답을 기반으로 DB 트랜잭션 수행
                    processPaymentWithAPIResponse(request, apiResponse);

                    // 4. 두 번째 비동기 외부 API 호출 (통보) 및 로깅
                    executeSecondExternalAPICall(request);

                } catch (Exception e) {
                    // 3번 실패 시: 2번 취소 API 호출 후 1번 롤백
                    log.error("Payment processing failed. Initiating compensation.", e);
                    compensateFirstExternalAPI(request);
                    throw new PaymentProcessingException("Payment failed, rolled back previous operations", e);
                }
            } catch (Exception e) {
                // 2번 실패 시: 1번 롤백
                if (e instanceof PaymentProcessingException) {
                    throw e; // 3번 실패로 인한 예외는 그대로 전파
                }
                log.error("First external API call failed.", e);
                throw new ExternalAPIException("External API call failed, rolling back order", e);
            }
        } catch (Exception e) {
            log.error("Order processing failed completely", e);
            throw new OrderProcessingException("Order processing failed", e);
        }
    }

    private void createInitialOrder(OrderRequest request) {
        Order order = Order.from(request);
        orderRepository.save(order);
        log.info("Initial order created: {}", order.getId());
    }

    private CompletableFuture<APIResponse> executeFirstExternalAPICall(OrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            APIResponse response = externalAPIClient.processAsync(request);
            transactionLogger.logTransaction("First External API call", request);
            return response;
        });
    }

    private void processPaymentWithAPIResponse(OrderRequest request, APIResponse apiResponse) {
        Payment payment = Payment.from(request, apiResponse);
        paymentRepository.save(payment);
        log.info("Payment processed with external reference: {}", apiResponse.getReferenceId());
    }

    private void executeSecondExternalAPICall(OrderRequest request) {
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

    private void compensateFirstExternalAPI(OrderRequest request) {
        try {
            externalAPIClient.cancelAsync(request);
            transactionLogger.logTransaction("Compensation - First API cancellation", request);
            log.info("Compensation completed for order: {}", request.getOrderId());
        } catch (Exception e) {
            log.error("Compensation failed but continuing with rollback", e);
        }
    }
}
