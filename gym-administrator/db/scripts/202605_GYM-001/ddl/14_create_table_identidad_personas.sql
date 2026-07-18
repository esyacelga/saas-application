CREATE TABLE identidad.personas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ci               VARCHAR(20)  NOT NULL UNIQUE,
  ci_validada      BOOLEAN      NOT NULL DEFAULT FALSE,
  sexo             CHAR(1)      CHECK (sexo IN ('M', 'F')),
  nombre           VARCHAR(150) NOT NULL,
  telefono         VARCHAR(20),
  correo           VARCHAR(150),
  foto_url         VARCHAR(255),
  fecha_nacimiento DATE,
  -- Opt-in WhatsApp (ex GYM-002, consolidado en la baseline): mismo molde que ci_validada.
  acepta_whatsapp         BOOLEAN     NOT NULL DEFAULT FALSE,
  fecha_consentimiento_wa TIMESTAMPTZ,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);

COMMENT ON COLUMN identidad.personas.ci_validada IS 'TRUE cuando la cédula pasó el algoritmo del dígito verificador ecuatoriano (módulo 10 del Registro Civil, implementado en CedulaEcuatoriana). FALSE por defecto cuando: (a) el documento no es una cédula ecuatoriana (pasaporte, RUC, doc. extranjero), (b) la cédula no pasó el módulo 10, o (c) la persona fue creada por una ruta que aún no calcula el flag. Hoy solo lo puebla el INSERT de platform-service (PersonaPersistenceAdapter.resolverIdPersona). UPDATE, backfill, otras rutas de creación y exposición REST siguen pendientes — ver docs/gym-administrator/pendientes/validacion-cedula-persona.md.';
COMMENT ON COLUMN identidad.personas.acepta_whatsapp         IS 'TRUE solo cuando la persona dio opt-in explícito para recibir avisos por WhatsApp. FALSE por defecto: sin este flag NUNCA se envía WhatsApp (evita bloqueo del número por Meta). Se captura en registro público, recepción o perfil PWA.';
COMMENT ON COLUMN identidad.personas.fecha_consentimiento_wa IS 'Timestamp del momento en que la persona aceptó recibir WhatsApp (prueba mínima de opt-in ante Meta). NULL mientras acepta_whatsapp = FALSE.';
