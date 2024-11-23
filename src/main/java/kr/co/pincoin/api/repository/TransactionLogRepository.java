package kr.co.pincoin.api.repository;

import kr.co.pincoin.api.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
}