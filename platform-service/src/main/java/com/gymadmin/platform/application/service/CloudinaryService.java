package com.gymadmin.platform.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public Mono<String> subirLogo(byte[] bytes, Long idCompania) {
        return Mono.fromCallable(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", "gym-admin/logos",
                    "public_id", "compania-" + idCompania,
                    "overwrite", true,
                    "resource_type", "image"
            ));
            return (String) result.get("secure_url");
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
