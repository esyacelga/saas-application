CREATE TABLE saas.planes (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre           VARCHAR(100)  NOT NULL,
  descripcion      TEXT,
  precio_mensual   DECIMAL(10,2) NOT NULL,
  activo           BOOLEAN       NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN       NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150)  NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
