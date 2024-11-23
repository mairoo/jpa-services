package kr.co.pincoin.api.controller;

import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.exception.ExternalAPIException;
import kr.co.pincoin.api.exception.OrderProcessingException;
import kr.co.pincoin.api.exception.PaymentProcessingException;
import kr.co.pincoin.api.service.OrderFacade;
import kr.co.pincoin.api.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 처리를 위한 REST 컨트롤러
 * 두 가지 다른 아키텍처 패턴(퍼사드와 트랜잭션 스크립트)을 통한 주문 처리를 비교 제공
 *
 * 두 패턴의 주요 차이점:
 *
 * 1. 코드 구조
 *    - 트랜잭션 스크립트: 모든 로직이 한 클래스에 집중
 *    - 퍼사드: 각 책임이 별도 서비스로 분리
 *
 * 2. 유지보수성
 *    - 트랜잭션 스크립트: 간단한 로직에 적합, 복잡해지면 유지보수 어려움
 *    - 퍼사드: 책임 분리로 유지보수 용이
 *
 * 3. 테스트 용이성
 *    - 트랜잭션 스크립트: 통합 테스트 위주
 *    - 퍼사드: 단위 테스트 용이
 *
 * 4. 확장성
 *    - 트랜잭션 스크립트: 확장이 어려움
 *    - 퍼사드: 새로운 기능 추가가 용이
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderFacade orderFacade;
    private final OrderProcessingService orderProcessingService;

    /**
     * 퍼사드 패턴을 사용한 주문 처리 엔드포인트
     * 퍼사드 패턴의 특징:
     * - 복잡한 시스템을 단순화된 인터페이스로 제공
     * - 각 서비스가 독립적인 책임을 가짐 (SRP 원칙)
     * - 높은 응집도, 낮은 결합도
     *
     * @param request 주문 요청 데이터
     * @return 처리 결과 응답
     */
    @PostMapping("/facade")
    public ResponseEntity<String> processOrderFacade(@RequestBody OrderRequest request) {
        try {
            orderFacade.processOrder(request);
            return ResponseEntity.ok("Order processed successfully with Facade pattern");
        } catch (Exception e) {
            log.error("Order processing failed with Facade pattern", e);
            return handleError(e);
        }
    }

    /**
     * 트랜잭션 스크립트 패턴을 사용한 주문 처리 엔드포인트
     * 트랜잭션 스크립트 패턴의 특징:
     * - 절차적인 코드로 비즈니스 로직을 직접 구현
     * - 단순한 CRUD 작업에 적합
     * - 모든 로직이 하나의 서비스 클래스에 집중
     *
     * @param request 주문 요청 데이터
     * @return 처리 결과 응답
     */
    @PostMapping("/transaction-script")
    public ResponseEntity<String> processOrderTransactionScript(@RequestBody OrderRequest request) {
        try {
            orderProcessingService.processOrder(request);
            return ResponseEntity.ok("Order processed successfully with Transaction Script pattern");
        } catch (Exception e) {
            log.error("Order processing failed with Transaction Script pattern", e);
            return handleError(e);
        }
    }

    private ResponseEntity<String> handleError(Exception e) {
        HttpStatus status = determineHttpStatus(e);
        String errorMessage = String.format("Order processing failed (%s): %s",
                                            determineErrorCode(e),
                                            e.getMessage());
        return ResponseEntity.status(status).body(errorMessage);
    }

    private String determineErrorCode(Exception e) {
        return switch (e) {
            case OrderProcessingException _ -> "ORDER_PROCESSING_ERROR";
            case PaymentProcessingException _ -> "PAYMENT_PROCESSING_ERROR";
            case ExternalAPIException _ -> "EXTERNAL_API_ERROR";
            default -> "UNKNOWN_ERROR";
        };
    }

    private HttpStatus determineHttpStatus(Exception e) {
        return switch (e) {
            case OrderProcessingException _ -> HttpStatus.BAD_REQUEST;
            case PaymentProcessingException _ -> HttpStatus.BAD_GATEWAY;
            case ExternalAPIException _ -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}