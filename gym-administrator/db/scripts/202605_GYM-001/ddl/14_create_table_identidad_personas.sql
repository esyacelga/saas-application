CREATE TABLE identidad.personas (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ci               VARCHAR(20)  NOT NULL,
  ci_validada      BOOLEAN      NOT NULL DEFAULT FALSE,
  sexo             CHAR(1)      CHECK (sexo IN ('M', 'F')),
  nombre           VARCHAR(150) NOT NULL,
  telefono         VARCHAR(20),
  correo           VARCHAR(150),
  foto_url         VARCHAR(255),
  fecha_nacimiento DATE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);

COMMENT ON COLUMN identidad.personas.ci_validada IS 'TRUE cuando la cédula pasó el algoritmo del dígito verificador ecuatoriano (módulo 10 del Registro Civil). FALSE por defecto: aún no validada, o el documento no es una cédula ecuatoriana (pasaporte, RUC, doc. extranjero). La lógica que puebla este campo está pendiente de implementación.';
