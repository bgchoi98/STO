package server.main.demo.config;

import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class DemoTradeProperties {

    private final boolean enabled = true;
    private final long minOrderAmount = 10_000L;
    private final long maxOrderAmount = 50_000L;
    private final int priceTickRange = 2;
}
