# gym-member-pwa — Índice de documentación

App móvil Progressive Web App para miembros del gym. Ver [gym-member-pwa/CLAUDE.md](../../gym-member-pwa/CLAUDE.md) para arquitectura, flujos y convenciones completas (el `README.md` de esta carpeta es boilerplate genérico de Vite, sin contenido específico del proyecto).

---

| Documento | Contenido |
|-----------|-----------|
| [pendientes-backlog.md](pendientes-backlog.md) | Backlog general del proyecto: OAuth, íconos PWA, reset de contraseña, QR deep-link, solicitud de membresía, i18n. Auditado 2026-07-17 |
| [spec-solicitud-membresia.md](spec-solicitud-membresia.md) | Especificación UX detallada del feature de solicitud de membresía: componentes, flujos de error, i18n keys, testing. Spec definida, implementación pendiente |
| [pendientes-checkin-qr.md](pendientes-checkin-qr.md) | Pendientes específicos del flujo de auto check-in después de login vía QR escaneado desde `auth-service-frond-end` |
| [historial-pagos-membresia.md](historial-pagos-membresia.md) | ✅ Implementado — Página `/membresia/historial`: lista todas las membresías con estado de pago, saldo pendiente, accesos (si aplica). Ver GYM-003. |

---

## Convenciones de esta carpeta

- `gym-member-pwa/CLAUDE.md` permanece en la raíz de `gym-member-pwa/` y enlaza aquí.
- Los dos documentos de pendientes cubren alcances distintos (backlog general vs. flujo QR específico) — no fusionar sin confirmar que ambos siguen vigentes.
