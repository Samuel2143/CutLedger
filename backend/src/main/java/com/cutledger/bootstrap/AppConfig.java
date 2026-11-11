package com.cutledger.bootstrap;

import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cutledger")
public record AppConfig(
    @NotNull URI publicBaseUrl,
    @Size(min=32) String sessionPepper,
    String demoAccessCodeHash,
    @Pattern(regexp="sandbox") String paypalEnv,
    String paypalClientId, String paypalClientSecret, String paypalMerchantId, String paypalWebhookId,
    String geminiApiKey,
    @Pattern(regexp="gemini-3\\.5-flash-lite") String aiModel,
    @Min(1) @Max(10000) int aiDailyRequestLimit
) {
    public AppConfig {
        if (publicBaseUrl != null && (publicBaseUrl.getHost() == null || publicBaseUrl.getUserInfo() != null ||
            publicBaseUrl.getQuery() != null || publicBaseUrl.getFragment() != null ||
            !(publicBaseUrl.getPath().isEmpty() || publicBaseUrl.getPath().equals("/"))))
            throw new IllegalArgumentException("PUBLIC_BASE_URL must be an origin");
    }
    public String origin() { return publicBaseUrl.toString().replaceAll("/$", ""); }
    public boolean paypalConfigured() { return present(paypalClientId) && present(paypalClientSecret) && present(paypalMerchantId); }
    public boolean aiConfigured() { return present(geminiApiKey); }
    public static boolean present(String value) { return value != null && !value.isBlank(); }
    @Override public String toString() { return "AppConfig[REDACTED]"; }
}
