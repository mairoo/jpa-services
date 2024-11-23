package kr.co.pincoin.api.service;

import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.external.ExternalAPIClient;
import kr.co.pincoin.api.logger.TransactionLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalAPIService {
    private final ExternalAPIClient externalAPIClient;

    private final TransactionLogger transactionLogger;

    public CompletableFuture<APIResponse> processAndLog(OrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            APIResponse response = externalAPIClient.processAsync(request);
            transactionLogger.logTransaction("First External API call", request);
            return response;
        });
    }

    public void cancelProcess(OrderRequest request) {
        externalAPIClient.cancelAsync(request);
        transactionLogger.logTransaction("Compensation - First API cancellation", request);
    }
}
