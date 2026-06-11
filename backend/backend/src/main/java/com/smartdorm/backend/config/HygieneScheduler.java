package com.smartdorm.backend.config;

import com.smartdorm.backend.service.HygieneService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class HygieneScheduler {

    private final HygieneService hygieneService;

    @Scheduled(cron = "0 30 23 * * ?")
    public void dailyClose() {
        hygieneService.dailySystemClose(LocalDate.now());
    }
}

