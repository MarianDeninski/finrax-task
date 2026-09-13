package com.finrax.interview_task.repository;

import com.finrax.interview_task.entity.Withdrawal;
import com.finrax.interview_task.entity.WithdrawalStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    List<Withdrawal> findTop100ByStatusOrderByCreatedAtAsc(WithdrawalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Withdrawal w where w.id = :id")
    Optional<Withdrawal> findByIdForUpdate(UUID id);
}
