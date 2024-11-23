package kr.co.pincoin.api.logger;

import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.entity.TransactionLog;
import kr.co.pincoin.api.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionLogger {
    private final TransactionLogRepository logRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransaction(String action, OrderRequest request) {
        TransactionLog transactionLog = TransactionLog.builder()
                .orderId(request.getOrderId())
                .action(action)
                .timestamp(LocalDateTime.now())
                .build();
        logRepository.save(transactionLog);
        log.info("Transaction logged: {} for order: {}", action, request.getOrderId());
    }
}
