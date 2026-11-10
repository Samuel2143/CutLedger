package com.cutledger.bootstrap;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class RuntimeConfig {
    public RuntimeConfig(AppConfig config, Environment environment) {
        boolean demo = java.util.Arrays.asList(environment.getActiveProfiles()).contains("demo");
        boolean localOrigin = "http".equals(config.publicBaseUrl().getScheme()) &&
            java.util.Set.of("localhost", "127.0.0.1").contains(config.publicBaseUrl().getHost());
        if (!"https".equals(config.publicBaseUrl().getScheme()) && (!localOrigin || demo))
            throw new IllegalStateException("HTTPS is required outside local mode");
        if (demo && (!config.paypalConfigured() || !config.aiConfigured() || !AppConfig.present(config.paypalWebhookId()) ||
            !AppConfig.present(config.demoAccessCodeHash()) || !config.demoAccessCodeHash().startsWith("$2")))
            throw new IllegalStateException("Demo integrations and hashed access gate must be configured");
    }
    @Bean com.cutledger.interpretation.InterpretationProvider interpretationProvider(AppConfig config,com.fasterxml.jackson.databind.ObjectMapper json,DomainJson domain){return new com.cutledger.interpretation.GeminiHttp(config,json,domain);}
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean com.cutledger.payment.PaymentProvider paymentProvider(AppConfig config,com.fasterxml.jackson.databind.ObjectMapper json,Clock clock){return new com.cutledger.payment.PayPalHttp(config,json,clock);}
}
