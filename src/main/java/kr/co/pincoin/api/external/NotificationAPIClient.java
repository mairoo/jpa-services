package kr.co.pincoin.api.external;

import kr.co.pincoin.api.dto.OrderRequest;

public interface NotificationAPIClient {
    void notify(OrderRequest request);
}
