package kr.co.pincoin.api.repository;

import kr.co.pincoin.api.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}