package com.spot.account.job;

import com.spot.account.model.DepositStatus;
import com.spot.account.repo.DepositRepo;
import com.spot.account.service.DepositService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DepositWatcher {
    private final DepositRepo depositRepo;
    private final DepositService depositService;

    public DepositWatcher(DepositRepo depositRepo, DepositService depositService) {
        this.depositRepo = depositRepo;
        this.depositService = depositService;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void tick() {
        var pending = depositRepo.findByStatusOrderByCreatedAtAsc(DepositStatus.PENDING, PageRequest.of(0, 50));
        Instant now = Instant.now();
        for (var d : pending) {
            if (Duration.between(d.getCreatedAt(), now).getSeconds() >= 5) {
                depositService.confirmDeposit(d);
            }
        }
    }
}
