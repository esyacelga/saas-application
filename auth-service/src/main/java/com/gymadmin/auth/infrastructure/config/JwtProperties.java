package com.gymadmin.auth.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
@Getter @Setter
public class JwtProperties {
    private String secret;
    private long expiryStaffSeconds = 28800;
    private long expiryClienteSeconds = 604800;
    private long refreshExpirySeconds = 2592000;
}
