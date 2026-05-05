package server.main.demo.scheduler;

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

    @Scheduled(cron = "0 10 9,10,12,14,15 * * *")
    public void createDemoTrades() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("[DemoTradeScheduler] demo trade batch started");
        demoTradeService.createDailyDemoTrades();
    }
}
