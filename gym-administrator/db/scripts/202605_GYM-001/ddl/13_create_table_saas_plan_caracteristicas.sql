CREATE TABLE saas.plan_caracteristicas (
  id_plan           INT NOT NULL REFERENCES saas.planes(id),
  id_caracteristica INT NOT NULL REFERENCES saas.caracteristicas(id),
  eliminado         BOOLEAN      NOT NULL DEFAULT FALSE,
  creacion_fecha    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  creacion_usuario  VARCHAR(150) NOT NULL DEFAULT 'sistema',
  modifica_fecha    TIMESTAMPTZ,
  modifica_usuario  VARCHAR(150),
  PRIMARY KEY (id_plan, id_caracteristica)
);
