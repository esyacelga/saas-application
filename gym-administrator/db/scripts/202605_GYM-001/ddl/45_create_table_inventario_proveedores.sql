CREATE TABLE inventario.proveedores (
  id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_compania      INT          NOT NULL,
  id_sucursal      INT          NOT NULL,
  nombre           VARCHAR(150) NOT NULL,
  telefono         VARCHAR(20),
  correo           VARCHAR(150),
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150)
);
