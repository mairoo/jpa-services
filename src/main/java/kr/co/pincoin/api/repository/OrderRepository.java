package kr.co.pincoin.api.repository;

import kr.co.pincoin.api.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}