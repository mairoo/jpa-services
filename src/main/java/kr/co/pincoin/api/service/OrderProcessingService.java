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

/**
 * 트랜잭션 스크립트 패턴을 구현한 서비스
 * 특징:
 * - 모든 비즈니스 로직이 하나의 클래스에 집중됨
 * - 직접적인 DB 접근과 외부 API 호출을 같은 클래스에서 처리
 * - 간단한 애플리케이션에서 사용하기 좋음
 * - 코드 재사용성이 낮고 유지보수가 어려울 수 있음
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {
    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final ExternalAPIClient externalAPIClient;

    private final NotificationAPIClient notificationAPIClient;

    private final TransactionLogger transactionLogger;

    /**
     * 트랜잭션 스크립트 패턴의 주문 처리 메소드
     * <p>
     * [처리 순서와 트랜잭션 규칙]
     * 1. 초기 주문 생성 (DB 트랜잭션)
     * - 주문 정보를 데이터베이스에 저장
     * <p>
     * 2. 첫 번째 외부 API 호출 (비동기)
     * - API 호출 및 로깅 수행
     * - 실패 시: 1번 작업 롤백
     * - 타임아웃: 30초
     * <p>
     * 3. 결제 처리 (DB 트랜잭션)
     * - API 응답 결과를 기반으로 결제 정보 저장
     * - 실패 시: 2번 작업 취소 API 호출 후 1번 작업 롤백
     * <p>
     * 4. 알림 발송 (비동기)
     * - 알림 API 호출 및 로깅
     * - 실패 시: 이전 작업들 롤백하지 않음 (무시)
     * <p>
     * [트랜잭션 특징]
     * - 전체 프로세스(1~4)는 하나의 원자적 단위로 처리
     * - 계층적 롤백 처리:
     * - 4번 실패: 롤백 없음
     * - 3번 실패: 2번 보상 트랜잭션 수행 + 1번 롤백
     * - 2번 실패: 1번 롤백
     * - 1번 실패: 즉시 종료
     *
     * @param request 주문 요청 정보
     * @throws OrderProcessingException   주문 처리 실패 시
     * @throws ExternalAPIException       외부 API 호출 실패 시
     * @throws PaymentProcessingException 결제 처리 실패 시
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void processOrder(OrderRequest request) {
        try {
            // Step 1: 주문 정보 DB 저장
            // - 실패 시 OrderProcessingException 발생하여 전체 프로세스 중단
            createInitialOrder(request);

            try {
                // Step 2: 첫 번째 외부 API 비동기 호출
                // - CompletableFuture를 사용하여 비동기 처리
                // - 30초 타임아웃 설정으로 무한 대기 방지
                CompletableFuture<APIResponse> apiResponseFuture = executeFirstExternalAPICall(request);
                APIResponse apiResponse = apiResponseFuture.get(30, TimeUnit.SECONDS);

                try {
                    // Step 3: API 응답을 기반으로 결제 정보 처리
                    // - 외부 API 응답 결과를 DB에 저장
                    processPaymentWithAPIResponse(request, apiResponse);

                    // Step 4: 알림 발송을 위한 두 번째 외부 API 호출
                    // - 비동기 처리이며 실패해도 이전 단계 롤백하지 않음
                    executeSecondExternalAPICall(request);

                } catch (Exception e) {
                    // Step 3 실패 시 보상 트랜잭션
                    // 1. 로그 기록
                    // 2. 첫 번째 API 취소 호출
                    // 3. DB 트랜잭션 롤백
                    log.error("Payment processing failed. Initiating compensation.", e);
                    compensateFirstExternalAPI(request);
                    throw new PaymentProcessingException("Payment failed, rolled back previous operations", e);
                }
            } catch (Exception e) {
                // Step 2 실패 처리
                // - PaymentProcessingException의 경우 상위로 전파 (Step 3 실패)
                // - 그 외 예외는 ExternalAPIException으로 변환하여 전파
                if (e instanceof PaymentProcessingException) {
                    throw e;
                }
                log.error("First external API call failed.", e);
                throw new ExternalAPIException("External API call failed, rolling back order", e);
            }
        } catch (Exception e) {
            // 전체 프로세스 실패 처리
            // - 모든 예외를 OrderProcessingException으로 감싸서 전파
            log.error("Order processing failed completely", e);
            throw new OrderProcessingException("Order processing failed", e);
        }
    }

    /**
     * 초기 주문 정보를 생성하고 데이터베이스에 저장
     * 트랜잭션의 첫 번째 단계를 담당
     *
     * @param request 주문 요청 정보
     * @throws RuntimeException 주문 생성 실패 시 (JPA 저장 실패 등)
     */
    private void createInitialOrder(OrderRequest request) {
        // OrderRequest를 Order 엔티티로 변환
        Order order = Order.from(request);

        // 데이터베이스에 주문 정보 저장
        orderRepository.save(order);

        // 주문 생성 완료 로그 기록
        log.info("Initial order created: {}", order.getId());
    }

    /**
     * 첫 번째 외부 API를 비동기적으로 호출하고 결과를 반환
     * 트랜잭션의 두 번째 단계를 담당
     *
     * @param request 주문 요청 정보
     * @return CompletableFuture<APIResponse> API 호출 결과를 포함한 Future 객체
     */
    private CompletableFuture<APIResponse> executeFirstExternalAPICall(OrderRequest request) {
        // supplyAsync를 사용하여 비동기 작업 실행
        return CompletableFuture.supplyAsync(() -> {
            // 외부 API 비동기 호출 수행
            APIResponse response = externalAPIClient.processAsync(request);

            // API 호출 결과 로깅 (감사 추적을 위한 기록)
            transactionLogger.logTransaction("First External API call", request);

            return response;
        });
    }

    /**
     * API 응답을 기반으로 결제 정보를 처리하고 저장
     * 트랜잭션의 세 번째 단계를 담당
     *
     * @param request     주문 요청 정보
     * @param apiResponse 첫 번째 API 호출의 응답 결과
     * @throws RuntimeException 결제 정보 저장 실패 시 (JPA 저장 실패 등)
     */
    private void processPaymentWithAPIResponse(OrderRequest request, APIResponse apiResponse) {
        // API 응답과 주문 요청을 기반으로 결제 정보 생성
        Payment payment = Payment.from(request, apiResponse);

        // 결제 정보를 데이터베이스에 저장
        paymentRepository.save(payment);

        // 결제 처리 완료 로그 기록
        log.info("Payment processed with external reference: {}", apiResponse.getReferenceId());
    }

    /**
     * 알림 발송을 위한 두 번째 외부 API를 비동기적으로 호출
     * 트랜잭션의 네 번째 단계를 담당
     * 실패 시에도 이전 트랜잭션에 영향을 주지 않음
     *
     * @param request 주문 요청 정보
     */
    private void executeSecondExternalAPICall(OrderRequest request) {
        try {
            // runAsync를 사용하여 반환값이 없는 비동기 작업 실행
            CompletableFuture.runAsync(() -> {
                // 알림 API 호출
                notificationAPIClient.notify(request);

                // API 호출 결과 로깅 (감사 추적)
                transactionLogger.logTransaction("Second External API call (Notification)", request);
            });
        } catch (Exception e) {
            // 알림 발송 실패는 전체 트랜잭션에 영향을 주지 않음
            // 실패 로그만 기록하고 계속 진행
            log.error("Notification API call failed but continuing", e);
        }
    }

    /**
     * 첫 번째 API 호출에 대한 보상 트랜잭션 수행
     * 결제 처리 실패 시 호출되어 이전 API 요청을 취소
     *
     * @param request 주문 요청 정보
     */
    private void compensateFirstExternalAPI(OrderRequest request) {
        try {
            // 첫 번째 API 호출에 대한 취소 요청 전송
            externalAPIClient.cancelAsync(request);

            // 보상 트랜잭션 로깅 (감사 추적)
            transactionLogger.logTransaction("Compensation - First API cancellation", request);

            // 보상 트랜잭션 완료 로그 기록
            log.info("Compensation completed for order: {}", request.getOrderId());
        } catch (Exception e) {
            // 보상 트랜잭션 실패 시에도 메인 트랜잭션은 롤백 진행
            // 운영팀 모니터링을 위한 에러 로그 기록
            log.error("Compensation failed but continuing with rollback", e);
        }
    }
}
