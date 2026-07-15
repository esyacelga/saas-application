-- GYM-002 · Consentimiento (opt-in) para avisos por WhatsApp.
-- Story incremental sobre la baseline GYM-001 (no se edita el CREATE TABLE del baseline).
-- Dos columnas por tabla, mismo molde que identidad.personas.ci_validada:
--   acepta_whatsapp          -> opt-in explícito (DEFAULT FALSE = nadie recibe hasta aceptar).
--   fecha_consentimiento_wa  -> sella cuándo se aceptó (prueba mínima ante Meta), NULL hasta entonces.
-- Backfill = NO-consentido: el DEFAULT FALSE deja todo registro existente sin opt-in, sin UPDATE masivo.

-- Socio (identidad.personas)
ALTER TABLE identidad.personas
  ADD COLUMN acepta_whatsapp         BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN fecha_consentimiento_wa TIMESTAMPTZ;

COMMENT ON COLUMN identidad.personas.acepta_whatsapp         IS 'TRUE solo cuando la persona dio opt-in explícito para recibir avisos por WhatsApp. FALSE por defecto: sin este flag NUNCA se envía WhatsApp (evita bloqueo del número por Meta). Se captura en registro público, recepción o perfil PWA.';
COMMENT ON COLUMN identidad.personas.fecha_consentimiento_wa IS 'Timestamp del momento en que la persona aceptó recibir WhatsApp (prueba mínima de opt-in ante Meta). NULL mientras acepta_whatsapp = FALSE.';

-- Dueño (tenant.companias)
ALTER TABLE tenant.companias
  ADD COLUMN acepta_whatsapp         BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN fecha_consentimiento_wa TIMESTAMPTZ;

COMMENT ON COLUMN tenant.companias.acepta_whatsapp         IS 'TRUE solo cuando el dueño de la compañía dio opt-in explícito para recibir avisos de vencimiento de suscripción por WhatsApp. FALSE por defecto: sin este flag NUNCA se envía WhatsApp. Se captura en onboarding o config de notificaciones.';
COMMENT ON COLUMN tenant.companias.fecha_consentimiento_wa IS 'Timestamp del momento en que el dueño aceptó recibir WhatsApp (prueba mínima de opt-in ante Meta). NULL mientras acepta_whatsapp = FALSE.';
