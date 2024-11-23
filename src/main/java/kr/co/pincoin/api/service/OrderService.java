package kr.co.pincoin.api.service;

import kr.co.pincoin.api.dto.OrderRequest;
import kr.co.pincoin.api.entity.Order;
import kr.co.pincoin.api.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void createOrder(OrderRequest request) {
        Order order = Order.from(request);
        orderRepository.save(order);
        log.info("Order created: {}", order.getId());
    }
}
