package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.model.sri.FormaPagoSri;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.model.sri.TarifaIvaSri;
import com.gymadmin.billing.domain.model.sri.TipoComprobanteSri;
import com.gymadmin.billing.domain.model.sri.TipoIdentificacionSri;
import com.gymadmin.billing.domain.model.sri.TipoImpuestoSri;
import com.gymadmin.billing.domain.port.out.CatalogoSriRepository;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lookups y validaciones sobre los 6 catálogos SRI. Encapsula el cache reactivo
 * (in-memory) para evitar el problema conocido de {@code @Cacheable} sobre
 * {@link Mono}, que memoriza el propio {@code Publisher} y no el valor.
 * <p>
 * <b>Estrategia de cache:</b> un {@link ConcurrentMap} por catálogo guarda la
 * {@link Mono} devuelta por el repositorio decorada con {@code .cache()}. Las
 * llamadas posteriores comparten el mismo resultado; el repositorio solo se
 * invoca una vez por código.
 * <p>
 * <b>Manejo de vacío:</b> se cachean tanto hits como misses. Un código
 * inexistente no vuelve a golpear la BD hasta que la app reinicie. Esto es
 * aceptable porque los catálogos SRI se cargan por seed en el mismo deploy que
 * el código que los consume.
 */
@Service
@RequiredArgsConstructor
public class CatalogoSriService {

    private final CatalogoSriRepository catalogoSriRepository;

    private final ConcurrentMap<String, Mono<TipoComprobanteSri>> cacheTipoComprobante = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<TipoIdentificacionSri>> cacheTipoIdentificacion = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<FormaPagoSri>> cacheFormaPago = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<TipoImpuestoSri>> cacheTipoImpuesto = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<TarifaIvaSri>> cacheTarifaIva = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<MotivoAnulacionNcSri>> cacheMotivoAnulacionNc = new ConcurrentHashMap<>();

    /**
     * Devuelve el tipo de comprobante o {@link NotFoundException} si el código
     * no está en el catálogo.
     */
    public Mono<TipoComprobanteSri> obtenerTipoComprobante(String codigo) {
        return cachedLookup(cacheTipoComprobante, codigo, catalogoSriRepository::findTipoComprobante)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Tipo de comprobante no reconocido: " + codigo)));
    }

    /**
     * {@link Mono#just(Object) true} si el código existe en
     * {@code sri.tipos_identificacion_comprador}; {@code false} si no.
     * Nunca falla — usado por validadores.
     */
    public Mono<Boolean> existeTipoIdentificacion(String codigo) {
        return cachedLookup(cacheTipoIdentificacion, codigo, catalogoSriRepository::findTipoIdentificacion)
                .hasElement();
    }

    /**
     * {@code true} si el código existe en {@code sri.formas_pago}; {@code false} si no.
     */
    public Mono<Boolean> existeFormaPago(String codigo) {
        return cachedLookup(cacheFormaPago, codigo, catalogoSriRepository::findFormaPago)
                .hasElement();
    }

    /**
     * Devuelve la tarifa IVA vigente a la {@code fechaEmision} indicada.
     * Falla con {@link BusinessException} si el código no existe o si la tarifa
     * existe pero no está vigente en esa fecha (típicamente el código 2 IVA 12%
     * después de 2024-03-31).
     */
    public Mono<TarifaIvaSri> obtenerTarifaIvaVigente(String codigo, LocalDate fechaEmision) {
        return cachedLookup(cacheTarifaIva, codigo, catalogoSriRepository::findTarifaIva)
                .switchIfEmpty(Mono.error(new BusinessException(
                        "Código de tarifa IVA no reconocido: " + codigo)))
                .flatMap(tarifa -> {
                    if (!tarifa.vigenteEn(fechaEmision)) {
                        return Mono.error(new BusinessException(
                                "La tarifa IVA con código " + codigo
                                        + " no está vigente en la fecha " + fechaEmision));
                    }
                    return Mono.just(tarifa);
                });
    }

    /**
     * Devuelve el motivo de anulación de NC. Falla con {@link NotFoundException}
     * si el código no está en el catálogo.
     */
    public Mono<MotivoAnulacionNcSri> obtenerMotivoAnulacion(String codigo) {
        return cachedLookup(cacheMotivoAnulacionNc, codigo, catalogoSriRepository::findMotivoAnulacionNc)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Motivo de anulación no reconocido: " + codigo)));
    }

    /**
     * Lista completa del catálogo {@code sri.motivos_anulacion_nc}. Se usa
     * desde el endpoint público {@code GET /api/v1/sri/motivos-anulacion} para
     * poblar dropdowns en la UI. No cachea porque el catálogo es de 5 filas y
     * la latencia de un SELECT es despreciable comparada con introducir un
     * segundo cache paralelo.
     */
    public Flux<MotivoAnulacionNcSri> listarMotivosAnulacion() {
        return catalogoSriRepository.listMotivosAnulacionNc();
    }

    /**
     * Devuelve el tipo de impuesto o {@link NotFoundException} si el código no existe.
     */
    public Mono<TipoImpuestoSri> obtenerTipoImpuesto(String codigo) {
        return cachedLookup(cacheTipoImpuesto, codigo, catalogoSriRepository::findTipoImpuesto)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Tipo de impuesto no reconocido: " + codigo)));
    }

    private <T> Mono<T> cachedLookup(ConcurrentMap<String, Mono<T>> cache,
                                     String codigo,
                                     java.util.function.Function<String, Mono<T>> loader) {
        if (codigo == null) {
            return Mono.empty();
        }
        return cache.computeIfAbsent(codigo, k -> loader.apply(k).cache());
    }
}
