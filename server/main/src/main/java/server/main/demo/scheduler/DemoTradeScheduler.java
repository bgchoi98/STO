package server.main.demo.scheduler;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import server.main.demo.config.DemoTradeProperties;
import server.main.demo.service.DemoTradeService;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoTradeScheduler {

    private final DemoTradeProperties properties;
    private final DemoTradeService demoTradeService;

    // 빈등록 확인
    @PostConstruct
    public void init() {
        log.info("[DemoTradeScheduler] registered. enabled={}", properties.isEnabled());
    }

    @Scheduled(cron = "0 10 9,10,12,14,15 * * *")
    public void createDemoTrades() {
        if (!properties.isEnabled()) {
            log.info("[DemoTradeScheduler] skipped. enabled=false");
            return;
        }

        log.info("[DemoTradeScheduler] demo trade batch started");
        demoTradeService.createDailyDemoTrades();
    }
}
