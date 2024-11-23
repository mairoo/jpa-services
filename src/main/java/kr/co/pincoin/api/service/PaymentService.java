package kr.co.pincoin.api.service;

import kr.co.pincoin.api.dto.APIResponse;
import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.entity.Payment;
import kr.co.pincoin.api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void processPayment(OrderRequest request, APIResponse apiResponse) {
        Payment payment = Payment.from(request, apiResponse);
        paymentRepository.save(payment);
        log.info("Payment processed: {}", payment.getId());
    }
}
