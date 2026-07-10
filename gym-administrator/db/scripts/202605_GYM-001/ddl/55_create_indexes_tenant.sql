CREATE INDEX idx_compania_planes_compania_estado
  ON tenant.compania_planes(id_compania, estado);

CREATE INDEX idx_compania_planes_vencimiento
  ON tenant.compania_planes(fecha_fin)
  WHERE estado = 'activo';

-- RN-10: prevenir dos suscripciones "vigentes" del mismo tenant.
CREATE UNIQUE INDEX ux_compania_plan_vigente
  ON tenant.compania_planes(id_compania)
  WHERE estado IN ('activo','en_gracia');

-- Soporte para SubscriptionJobService (busca "vencidos" y "por vencer" diariamente).
CREATE INDEX idx_compania_planes_estado_fecha_fin
  ON tenant.compania_planes(estado, fecha_fin);

-- Soporte para el job de archivado automático (RN-06): tenants con sobre_limite=true.
CREATE INDEX idx_compania_planes_sobre_limite
  ON tenant.compania_planes(id_compania)
  WHERE sobre_limite = TRUE;

-- ── notificaciones_suscripcion ──────────────────────────────────────────────
-- Predicado idempotente del job de notificaciones (RN-07).
CREATE INDEX idx_notif_compania_tipo_dias
  ON tenant.notificaciones_suscripcion(id_compania_plan, dias_antes);

-- Claim con FOR UPDATE SKIP LOCKED — solo indexa filas reclamables por el worker.
CREATE INDEX idx_notif_pendientes_retry
  ON tenant.notificaciones_suscripcion(estado, proximo_intento)
  WHERE estado IN ('pendiente','reintentar');

-- Filtro por tenant en el endpoint GET /banners-activos.
CREATE INDEX idx_notif_banner_compania
  ON tenant.notificaciones_suscripcion(id_compania, canal, descartado_at)
  WHERE canal = 'banner';
