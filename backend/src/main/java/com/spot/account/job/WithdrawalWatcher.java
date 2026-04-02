package com.spot.account.job;

import com.spot.account.model.WithdrawalStatus;
import com.spot.account.repo.WithdrawalRepo;
import com.spot.account.service.WithdrawalService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WithdrawalWatcher {
    private final WithdrawalRepo withdrawalRepo;
    private final WithdrawalService withdrawalService;

    public WithdrawalWatcher(WithdrawalRepo withdrawalRepo, WithdrawalService withdrawalService) {
        this.withdrawalRepo = withdrawalRepo;
        this.withdrawalService = withdrawalService;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        var pendings = withdrawalRepo.findByStatusOrderByUpdatedAtAsc(WithdrawalStatus.PENDING, PageRequest.of(0, 50));
        for (var w : pendings) {
            if (Duration.between(w.getCreatedAt(), now).getSeconds() >= 5) {
                withdrawalService.progressToProcessing(w);
            }
        }
        var procs = withdrawalRepo.findByStatusOrderByUpdatedAtAsc(WithdrawalStatus.PROCESSING, PageRequest.of(0, 50));
        for (var w : procs) {
            if (Duration.between(w.getUpdatedAt(), now).getSeconds() >= 5) {
                withdrawalService.finishDone(w);
            }
        }
    }
}
