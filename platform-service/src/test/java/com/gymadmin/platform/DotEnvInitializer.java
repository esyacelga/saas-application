package com.gymadmin.platform;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

/**
 * Loads .env file variables as Spring test properties before the context starts.
 * Referenced via @ContextConfiguration(initializers = DotEnvInitializer.class).
 */
public class DotEnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Dotenv dotenv = Dotenv.configure()
                .directory(System.getProperty("user.dir"))
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
