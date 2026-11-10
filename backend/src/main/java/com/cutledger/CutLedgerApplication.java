package com.cutledger;

import com.cutledger.bootstrap.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppConfig.class)
public class CutLedgerApplication {
    public static void main(String[] args) { SpringApplication.run(CutLedgerApplication.class, args); }
}
