package com.gymadmin.attendance.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

@Configuration
@EnableR2dbcAuditing(auditorAwareRef = "auditAwareImpl")
public class R2dbcConfig {
}
