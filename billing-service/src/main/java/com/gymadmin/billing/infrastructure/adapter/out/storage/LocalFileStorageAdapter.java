package com.gymadmin.billing.infrastructure.adapter.out.storage;

import com.gymadmin.billing.domain.port.out.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    @Value("${sri.storage.base-path}")
    private String basePath;

    @Override
    public Mono<String> saveXmlFirmado(Long idComprobante, String xmlContent) {
        String relativePath = "xml/firmado/" + idComprobante + ".xml";
        return writeFile(relativePath, xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public Mono<String> saveXmlAutorizado(Long idComprobante, String xmlContent) {
        String relativePath = "xml/autorizado/" + idComprobante + ".xml";
        return writeFile(relativePath, xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public Mono<String> saveRidePdf(Long idComprobante, byte[] pdfContent) {
        String relativePath = "ride/" + idComprobante + ".pdf";
        return writeFile(relativePath, pdfContent);
    }

    @Override
    public Mono<String> readFile(String path) {
        return Mono.fromCallable(() -> {
            Path fullPath = Paths.get(basePath, path);
            byte[] bytes = Files.readAllBytes(fullPath);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<byte[]> readFileBytes(String path) {
        return Mono.fromCallable(() -> {
            Path fullPath = Paths.get(basePath, path);
            return Files.readAllBytes(fullPath);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<String> writeFile(String relativePath, byte[] content) {
        return Mono.fromCallable(() -> {
            Path fullPath = Paths.get(basePath, relativePath);
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return relativePath;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
