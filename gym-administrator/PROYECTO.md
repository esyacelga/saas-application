# Sistema de Gestión de Gimnasio
## Panel Administrativo (Administrador)

### Objetivo Principal
Administrar y controlar todas las operaciones del gimnasio de forma fácil, intuitiva y automatizada, mejorando la experiencia del cliente y aumentando la retención.

---

## Módulos del Sistema

### 1. Dashboard (Principal)
- Resumen general del gimnasio
- Clientes activos
- Próximos a vencer (1 día)
- Membresías vencidas
- Clientes en riesgo de abandono
- Ingresos del mes actual
- Asistencia de hoy
- Gráficas y estadísticas
- Alertas y notificaciones

> Diseño visual con indicadores (colores, tarjetas, gráficos y globos de alertas)

**Buscar, filtrar y acciones rápidas:** Ver, Editar, Mensaje, Historial

---

### 2. Clientes
- Registro de nuevo usuario
- **Datos personales:** Nombre, Teléfono, CI, Correo, Residencia
- Foto
- Peso, Altura
- Objetivos
- Fecha de ingreso
- Lesiones
- **Historial del cliente:**
  - Asistencia
  - Faltas
  - Promociones usadas

**Estado del cliente:**
- Activo
- Próximo a vencer
- Vencido
- Congelado
- En riesgo de abandono

---

### 3. Membresías
- Nueva membresía (Registrar)
- Renovar membresía (de historial de meses con nosotros)
- Anular membresía (en caso de error)
- Congelar membresía (Viaje, Lesión, Enfermedad)
- **Tipos de membresía:** Por días, semanas, meses, años
- **Control de vencimientos:**
  - Vencidos
  - Al día
  - Próximos a vencer (3 días)

**Globos de alertas:**
- Número de personas próximas a vencer (1 a...)
- Globo 0 cuando todos están al día

**Descuentos y beneficios automáticos:**
- 1 mes sin faltas → 10% desc. próximo mes
- 3 meses sin faltas → Nutricionista por 1 mes
- 6 meses sin faltas → Trofeo + imagen de su cambio

**Alertas automáticas por WhatsApp:**
- 3 días antes
- El día de finalización

---

### 4. Asistencia y Seguimiento
- Registro automático con QR
- Control de días asistidos (30 días)
- Mensajes automáticos por ausencia: 2 días → WhatsApp
- **Mensajes motivacionales (10 diferentes al azar)**
- Seguimientos y alertas al admin para realizar seguimiento

**Recuperación Automática:**
- 5 días → WhatsApp
- 10 días → Llamada
- 15 días → Promoción para el siguiente mes
- Cliente en riesgo de abandono

---

### 5. Finanzas

#### Control de Ingresos
- Mes actual
- Próximo mes
- Ventas de Suplementos e Implementos
- Entrenamiento personalizado
- Otros ingresos

#### Control de Egresos
- Implementos de aseo
- Luz
- Agua
- Internet
- Seguridad
- Transporte
- Sueldos
- Descuentos membresías
- Otros egresos

> Reportes y gráficos de ingresos vs egresos

---

### 6. Promociones y Beneficios
- **Promoción 2x1** (20 x dos personas)
- Seguimiento de usuarios en promociones
- No aplica a descuentos por no faltar en todo el mes
- No aplica a nutricionista por 60 días sin falta
- **Otras promociones personalizadas**
- Campañas y beneficios especiales

> Configurar reglas y condiciones de cada promoción

---

### 7. Usuarios y Permisos
**Roles de usuarios:**
- Dueño (acceso total)
- Empleados

**Permisos específicos:**
- Recepción: no puede borrar pagos
- Entrenador: no ve finanzas
- Contador: solo ve reportes
- Personalizado por rol

**Control de accesos:**
- Clave única por usuario
- Bitácora de acciones

> Seguridad y control total de la información

---

### 8. Configuración del Sistema
- Datos del gimnasio (Nombre, Logo, Dirección, Teléfono, WhatsApp)
- Configuración de mensajes automáticos (WhatsApp)
- Reglas de asistencia y recuperación
- **Configuración de promociones y beneficios**
- Métodos de pago
- Parámetros generales del sistema
- Respaldo de información

> Personalizar el sistema según las necesidades del gimnasio

---

## Flujo General del Sistema

```
Nuevo Cliente              Asignar Membresía           Ingreso al Gimnasio
Registro de datos    →    Seleccionar plan,      →    Cliente escanea QR
y objetivos               duración y precio            y se registra asistencia
        ↓
Seguimiento y Mensajes           Beneficios y Promociones       Reportes y Análisis
Sistema monitorea       →       Descuentos, premios      →     Decisiones basadas
asistencia y progreso           y motivación                    en datos
        ↑
  Alertas y Mensajes
  Automatización por WhatsApp
```

---

## Resultados Esperados
- Más organización y control
- Reducción de ausencias y abandono
- Clientes más motivados y fidelizados
- Incremento de ingresos
- Toma de decisiones basada en datos
