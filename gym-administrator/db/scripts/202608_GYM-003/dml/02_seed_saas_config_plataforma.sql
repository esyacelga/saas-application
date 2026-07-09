-- REQ-SAAS-001 — Sub-fase 1.1 — Sección 11.4
--
-- Seed de las 7 claves iniciales de datos bancarios que el owner ve al reportar
-- un pago Premium (RN-08). VALORES DE EJEMPLO — el equipo los reemplaza en producción
-- desde /platform/config antes del go-live.
--
-- Idempotente: ON CONFLICT DO NOTHING preserva cualquier valor que el equipo ya haya
-- ajustado en runtime.
INSERT INTO saas.config_plataforma (clave, valor, descripcion, creacion_usuario) VALUES
    ('pago.banco.nombre',
     'Banco Pichincha',
     'Nombre del banco donde recibir la transferencia',
     'sistema'),

    ('pago.banco.tipo_cuenta',
     'Corriente',
     'Tipo de cuenta: Corriente / Ahorros',
     'sistema'),

    ('pago.banco.numero',
     '2100XXXXXXX',
     'Número de cuenta bancaria (REEMPLAZAR EN PROD)',
     'sistema'),

    ('pago.banco.titular',
     'Gym Administrator SaaS S.A.',
     'Razón social del titular de la cuenta',
     'sistema'),

    ('pago.banco.ruc',
     '1791234567001',
     'RUC del titular (REEMPLAZAR EN PROD)',
     'sistema'),

    ('pago.banco.email_notif',
     'pagos@gym-administrator.com',
     'Correo donde el owner debe notificar el pago realizado',
     'sistema'),

    ('pago.instrucciones',
     'Realizar transferencia y adjuntar comprobante. Aprobación en <4h hábiles.',
     'Instrucciones que se muestran al owner en el modal de reporte de pago',
     'sistema')
ON CONFLICT (clave) DO NOTHING;
