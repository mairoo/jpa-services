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

/**
 * 퍼사드 패턴을 구현한 서비스
 * 특징:
 * - 각 책임이 개별 서비스로 분리됨 (SRP 원칙)
 * - 높은 응집도와 낮은 결합도
 * - 테스트가 용이하고 유지보수가 쉬움
 * - 복잡한 시스템에 적합
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFacade {
    private final OrderService orderService;

    private final ExternalAPIService externalAPIService;

    private final PaymentService paymentService;

    private final NotificationService notificationService;

    /**
     * 퍼사드 패턴의 주문 처리 메소드
     * 각 단계별 처리를 전문 서비스에 위임하여 처리
     *
     * [처리 순서와 트랜잭션 규칙]
     * 1. OrderService를 통한 주문 생성 (DB 트랜잭션)
     *    - 주문 정보를 데이터베이스에 저장
     *
     * 2. ExternalAPIService를 통한 첫 번째 외부 API 처리 (비동기)
     *    - API 호출 및 로깅 수행
     *    - 실패 시: 1번 작업 롤백
     *    - 타임아웃: 30초
     *
     * 3. PaymentService를 통한 결제 처리 (DB 트랜잭션)
     *    - API 응답 결과를 기반으로 결제 정보 저장
     *    - 실패 시: 2번 작업 취소 API 호출 후 1번 작업 롤백
     *
     * 4. NotificationService를 통한 알림 발송 (비동기)
     *    - 알림 API 호출 및 로깅
     *    - 실패 시: 이전 작업들 롤백하지 않음 (무시)
     *
     * [트랜잭션 특징]
     * - 전체 프로세스(1~4)는 하나의 원자적 단위로 처리
     * - 각 단계는 독립된 서비스가 담당하지만, 트랜잭션 정책은 동일:
     *   - 4번 실패: 롤백 없음
     *   - 3번 실패: 2번 보상 트랜잭션 수행 + 1번 롤백
     *   - 2번 실패: 1번 롤백
     *   - 1번 실패: 즉시 종료
     *
     * @param request 주문 요청 정보
     * @throws OrderProcessingException 주문 처리 실패 시
     * @throws ExternalAPIException 외부 API 호출 실패 시
     * @throws PaymentProcessingException 결제 처리 실패 시
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void processOrder(OrderRequest request) {
        try {
            // Step 1: OrderService를 통한 주문 생성
            // - 주문 정보의 유효성 검증 및 DB 저장
            // - OrderService가 세부적인 주문 생성 로직을 캡슐화
            orderService.createOrder(request);

            try {
                // Step 2: ExternalAPIService를 통한 외부 API 처리
                // - API 호출, 로깅, 응답 처리를 서비스가 전담
                // - 30초 타임아웃 적용
                CompletableFuture<APIResponse> apiResponseFuture = externalAPIService.processAndLog(request);
                APIResponse apiResponse = apiResponseFuture.get(30, TimeUnit.SECONDS);

                try {
                    // Step 3: PaymentService를 통한 결제 처리
                    // - 결제 정보 유효성 검증 및 DB 저장
                    // - 결제 관련 비즈니스 로직을 서비스가 캡슐화
                    paymentService.processPayment(request, apiResponse);

                    // Step 4: NotificationService를 통한 알림 발송
                    // - 알림 전송 로직을 서비스가 캡슐화
                    // - 실패해도 이전 단계 롤백하지 않음
                    notificationService.sendNotification(request);

                } catch (Exception e) {
                    // Step 3 실패 시 보상 트랜잭션
                    // - ExternalAPIService를 통한 API 취소 처리
                    // - 결제 실패 예외를 전파하여 DB 트랜잭션 롤백
                    log.error("Payment processing failed. Initiating compensation.", e);
                    externalAPIService.cancelProcess(request);
                    throw new PaymentProcessingException("Payment failed, rolled back previous operations", e);
                }
            } catch (Exception e) {
                // Step 2 실패 처리
                // - PaymentProcessingException은 상위로 전파
                // - 다른 예외는 ExternalAPIException으로 변환
                if (e instanceof PaymentProcessingException) {
                    throw e;
                }
                log.error("First external API call failed.", e);
                throw new ExternalAPIException("External API call failed, rolling back order", e);
            }
        } catch (Exception e) {
            // 전체 프로세스 실패 처리
            // - 모든 예외를 OrderProcessingException으로 변환하여 전파
            log.error("Order processing failed completely", e);
            throw new OrderProcessingException("Order processing failed", e);
        }
    }
}