package com.gymadmin.auth.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter @Setter
public class AppProperties {
    private int bcryptRounds = 12;
    private int maxLoginAttempts = 5;
    private int loginLockoutMinutes = 15;
    private int pwdResetTokenExpiryHours = 1;
    private String frontendUrl = "http://localhost:5173";
    private Cors cors = new Cors();

    @Getter @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
        private boolean allowAll = false;
    }
}
