CREATE TABLE seguridad.rol_permisos (
  id_rol           INT NOT NULL REFERENCES seguridad.roles(id),
  id_permiso       INT NOT NULL REFERENCES seguridad.permisos(id),
  eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha   TIMESTAMPTZ,
  modifica_usuario VARCHAR(150),
  PRIMARY KEY (id_rol, id_permiso)
);
