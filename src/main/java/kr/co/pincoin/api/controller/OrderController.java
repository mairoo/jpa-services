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

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderFacade orderFacade;
    private final OrderProcessingService orderProcessingService;

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