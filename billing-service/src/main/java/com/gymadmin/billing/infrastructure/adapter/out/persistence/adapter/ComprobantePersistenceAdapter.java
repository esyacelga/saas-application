package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import com.gymadmin.billing.infrastructure.adapter.out.persistence.repository.ComprobanteR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class ComprobantePersistenceAdapter implements ComprobanteRepository {

    private final ComprobanteR2dbcRepository repository;

    @Override
    public Mono<Comprobante> save(Comprobante comprobante) {
        if (comprobante.getId() != null) {
            return repository.findById(comprobante.getId())
                    .map(existing -> mergeIntoEntity(existing, comprobante))
                    .flatMap(repository::save)
                    .map(this::toDomain);
        }
        return repository.save(toEntity(comprobante)).map(this::toDomain);
    }

    @Override
    public Mono<Comprobante> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Comprobante> findByClaveAcceso(String claveAcceso) {
        return repository.findByClaveAcceso(claveAcceso).map(this::toDomain);
    }

    @Override
    public Flux<Comprobante> findByEmpresa(Integer idCompania, Integer idSucursal, String estado, int offset, int limit) {
        return repository.findByEmpresa(idCompania, idSucursal, estado, limit, offset).map(this::toDomain);
    }

    @Override
    public Mono<Long> countByEmpresa(Integer idCompania, Integer idSucursal, String estado) {
        return repository.countByEmpresa(idCompania, idSucursal, estado);
    }

    @Override
    public Mono<Comprobante> updateEstado(Long id, String estado, String xmlFirmadoPath,
                                          String xmlAutorizadoPath, String ridePdfPath,
                                          OffsetDateTime fechaAutorizacion, String numeroAutorizacion) {
        return repository.updateEstadoById(id, estado, xmlFirmadoPath, xmlAutorizadoPath,
                        ridePdfPath, fechaAutorizacion, numeroAutorizacion)
                .then(repository.findById(id))
                .map(this::toDomain);
    }

    private Comprobante toDomain(ComprobanteEntity e) {
        return Comprobante.builder()
                .id(e.getId())
                .idCompania(e.getIdCompania())
                .idSucursal(e.getIdSucursal())
                .tipoComprobante(e.getTipoComprobante())
                .claveAcceso(e.getClaveAcceso())
                .numeroAutorizacion(e.getNumeroAutorizacion())
                .codEstablecimiento(e.getCodEstablecimiento())
                .codPuntoEmision(e.getCodPuntoEmision())
                .secuencial(e.getSecuencial())
                .fechaEmision(e.getFechaEmision())
                .ambiente(e.getAmbiente())
                .tipoIdReceptor(e.getTipoIdReceptor())
                .idReceptor(e.getIdReceptor())
                .razonSocialReceptor(e.getRazonSocialReceptor())
                .emailReceptor(e.getEmailReceptor())
                .direccionReceptor(e.getDireccionReceptor())
                .telefonoReceptor(e.getTelefonoReceptor())
                .subtotalSinImpuesto(e.getSubtotalSinImpuesto())
                .subtotalIva0(e.getSubtotalIva0())
                .subtotalNoObjetoIva(e.getSubtotalNoObjetoIva())
                .subtotalExentoIva(e.getSubtotalExentoIva())
                .totalDescuento(e.getTotalDescuento())
                .totalIce(e.getTotalIce())
                .totalIva(e.getTotalIva())
                .propina(e.getPropina())
                .total(e.getTotal())
                .moneda(e.getMoneda())
                .idMembresia(e.getIdMembresia())
                .idVenta(e.getIdVenta())
                .idComprobanteRef(e.getIdComprobanteRef())
                .estado(e.getEstado())
                .fechaAutorizacion(e.getFechaAutorizacion())
                .xmlFirmadoPath(e.getXmlFirmadoPath())
                .xmlAutorizadoPath(e.getXmlAutorizadoPath())
                .ridePdfPath(e.getRidePdfPath())
                .idUsuarioRegistro(e.getIdUsuarioRegistro())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private ComprobanteEntity toEntity(Comprobante c) {
        return ComprobanteEntity.builder()
                .id(c.getId())
                .idCompania(c.getIdCompania())
                .idSucursal(c.getIdSucursal())
                .tipoComprobante(c.getTipoComprobante())
                .claveAcceso(c.getClaveAcceso())
                .numeroAutorizacion(c.getNumeroAutorizacion())
                .codEstablecimiento(c.getCodEstablecimiento())
                .codPuntoEmision(c.getCodPuntoEmision())
                .secuencial(c.getSecuencial())
                .fechaEmision(c.getFechaEmision())
                .ambiente(c.getAmbiente())
                .tipoIdReceptor(c.getTipoIdReceptor())
                .idReceptor(c.getIdReceptor())
                .razonSocialReceptor(c.getRazonSocialReceptor())
                .emailReceptor(c.getEmailReceptor())
                .direccionReceptor(c.getDireccionReceptor())
                .telefonoReceptor(c.getTelefonoReceptor())
                .subtotalSinImpuesto(c.getSubtotalSinImpuesto())
                .subtotalIva0(c.getSubtotalIva0())
                .subtotalNoObjetoIva(c.getSubtotalNoObjetoIva())
                .subtotalExentoIva(c.getSubtotalExentoIva())
                .totalDescuento(c.getTotalDescuento())
                .totalIce(c.getTotalIce())
                .totalIva(c.getTotalIva())
                .propina(c.getPropina())
                .total(c.getTotal())
                .moneda(c.getMoneda())
                .idMembresia(c.getIdMembresia())
                .idVenta(c.getIdVenta())
                .idComprobanteRef(c.getIdComprobanteRef())
                .estado(c.getEstado())
                .fechaAutorizacion(c.getFechaAutorizacion())
                .xmlFirmadoPath(c.getXmlFirmadoPath())
                .xmlAutorizadoPath(c.getXmlAutorizadoPath())
                .ridePdfPath(c.getRidePdfPath())
                .idUsuarioRegistro(c.getIdUsuarioRegistro())
                .build();
    }

    private ComprobanteEntity mergeIntoEntity(ComprobanteEntity existing, Comprobante c) {
        if (c.getEstado() != null)             existing.setEstado(c.getEstado());
        if (c.getNumeroAutorizacion() != null) existing.setNumeroAutorizacion(c.getNumeroAutorizacion());
        if (c.getFechaAutorizacion() != null)  existing.setFechaAutorizacion(c.getFechaAutorizacion());
        if (c.getXmlFirmadoPath() != null)     existing.setXmlFirmadoPath(c.getXmlFirmadoPath());
        if (c.getXmlAutorizadoPath() != null)  existing.setXmlAutorizadoPath(c.getXmlAutorizadoPath());
        if (c.getRidePdfPath() != null)        existing.setRidePdfPath(c.getRidePdfPath());
        if (c.getTotalIva() != null)           existing.setTotalIva(c.getTotalIva());
        if (c.getTotal() != null)              existing.setTotal(c.getTotal());
        return existing;
    }
}
