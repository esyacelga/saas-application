package com.gymadmin.finance;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class DotEnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Dotenv dotenv = Dotenv.configure()
                .directory("C:/Respos/own-aplications/finance-service")
                .filename(".env")
                .ignoreIfMissing()
                .load();

        dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE).forEach(entry ->
                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                        applicationContext,
                        entry.getKey() + "=" + entry.getValue()
                )
        );
    }
}
