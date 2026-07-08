package com.gymadmin.platform.infrastructure.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties props) {
        log.info("Cloudinary cloud_name cargado: '{}'", props.cloudName());
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", props.cloudName(),
                "api_key",    props.apiKey(),
                "api_secret", props.apiSecret(),
                "secure",     true
        ));
    }
}
