package kr.co.pincoin.api.external;

import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;

public interface ExternalAPIClient {
    APIResponse processAsync(OrderRequest request);

    void cancelAsync(OrderRequest request);
}