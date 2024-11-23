# 목표
스프링부트 자바 기반 프로젝트 서비스 계층에 트랜잭션 스크립트 패턴과 퍼사드 패턴의 적용

# curl 테스트

트랜잭션 스크립트 패턴

```
curl -X POST http://localhost:8080/orders/transaction-script \
-H "Content-Type: application/json" \
-d '{
    "orderId": "ORD-001",
    "amount": 100.00,
    "customerEmail": "test@example.com"
}'
```

퍼사드 패턴

```
curl -X POST http://localhost:8080/orders/facade \
-H "Content-Type: application/json" \
-d '{
    "orderId": "ORD-001",
    "amount": 100.00,
    "customerEmail": "test@example.com"
}'
```

# 수행 작업 및 요건

## 작업 수행 순서

1. DB 트랜잭션 수행(1)
2. 비동기 외부 API 1 호출 (DB 트랜잭션 로깅 포함)
3. 비동기 외부 API 호출결과를 가지고 DB 트랜잭션 수행(2)
4. 비동기 외부 API 2 호출 (통보 등 DB 트랜잭션 로깅 포함)

## 주요 특징
- 1~4번은 하나의 원자적 동작
- 4번의 실패는 1~3번 롤백 없음
- 3번의 실패는 2번 취소 api 동작과 1번 롤백
- 2번의 실패는 1번 롤백

# 트랜잭션 처리 상세 명세

## DB 트랜잭션 Phase A
- 작업: 초기 주문 정보 저장
- 구현: `createInitialOrder(OrderRequest)`
- 범위: JPA/DB 트랜잭션
- 특징: `@Transactional` 범위 시작점

## 외부 API Phase A
- 작업: 첫 번째 외부 시스템 API 호출
- 구현: `executeFirstExternalAPICall(OrderRequest)`
- 처리: `CompletableFuture.supplyAsync()`로 비동기 실행
- 타임아웃: 30초
- 부가 작업: 트랜잭션 로거를 통한 API 호출 기록

## DB 트랜잭션 Phase B
- 작업: API 응답 기반 결제 정보 처리
- 구현: `processPaymentWithAPIResponse(OrderRequest, APIResponse)`
- 범위: JPA/DB 트랜잭션
- 의존성: Phase A의 API 응답 데이터 필요

## 외부 API Phase B
- 작업: 알림 발송을 위한 두 번째 API 호출
- 구현: `executeSecondExternalAPICall(OrderRequest)`
- 처리: `CompletableFuture.runAsync()`로 비동기 실행
- 부가 작업: 트랜잭션 로거를 통한 API 호출 기록

# 주문 처리 프로세스

## 처리 단계
- 주문 정보 DB 저장
- 결제 API 호출 (+ 로깅)
- 결제 정보 DB 저장
- 알림용 외부 API 호출 (+ 로깅)

## 실패 처리 원칙

### 기본 원칙
- 전체 단계는 하나의 원자적 동작으로 처리됨

### 실패 시 처리 규칙
- 알림 API 실패
  - 이전 단계들은 롤백하지 않음
  - 주문/결제 프로세스에 영향 없이 계속 진행

- 결제 정보 저장 실패
  - 첫 번째 API 취소 처리
  - 주문 정보 롤백

- 첫 번째 API 실패
  - 주문 정보 롤백

- 주문 정보 저장 실패
  - 즉시 전체 프로세스 중단

# 구현 시 주의사항

## 비동기 처리
- 외부 API 호출은 모두 비동기(`CompletableFuture`) 사용
- 결제 API는 응답 대기 필요(`get()` 메소드 사용)
- 통보 API는 응답 대기 없이 실행(`runAsync`)

## 트랜잭션 로깅
- 모든 외부 API 호출은 트랜잭션 로거로 기록
- 보상 트랜잭션 수행 시에도 로깅 필수
- 실패 케이스는 error 레벨로 로깅

## 예외 처리
- 각 Phase별 적절한 예외 타입 사용
- 보상 트랜잭션 실패 시에도 메인 플로우 롤백 보장
- 최상위에서 모든 예외를 `OrderProcessingException`으로 래핑

# 소스코드 바로가기

## 서비스

- [트랜잭션 스크립트 패턴 서비스](/src/main/java/kr/co/pincoin/api/service/OrderProcessingService.java)
- [퍼사드 패턴 서비스](/src/main/java/kr/co/pincoin/api/service/OrderFacade.java)
  - [OrderService](/src/main/java/kr/co/pincoin/api/service/OrderService.java)
  - [ExternalAPIService](/src/main/java/kr/co/pincoin/api/service/ExternalAPIService.java)
  - [PaymentService](/src/main/java/kr/co/pincoin/api/service/PaymentService.java)
  - [NotificationService](/src/main/java/kr/co/pincoin/api/service/NotificationService.java)

## 외부 연동 API 인터페이스와 더미 구현체

- [ExternalAPIClient](/src/main/java/kr/co/pincoin/api/external/ExternalAPIClient.java)
- [NotificationAPIClient](/src/main/java/kr/co/pincoin/api/external/NotificationAPIClient.java)
- [DummyExternalAPIClient](/src/main/java/kr/co/pincoin/api/external/DummyExternalAPIClient.java)
- [DummyNotificationAPIClient](/src/main/java/kr/co/pincoin/api/external/DummyNotificationAPIClient.java)

## 트랜잭션 로거

- [TransactionLogger](/src/main/java/kr/co/pincoin/api/logger/TransactionLogger.java)

## 비동기 설정

- [AsyncConfig](/src/main/java/kr/co/pincoin/api/config/AsyncConfig.java)
