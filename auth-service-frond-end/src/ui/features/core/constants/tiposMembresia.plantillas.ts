// Matches the FormValues type defined in CrearTipoMembresiaModal
interface FormValues {
  nombre?: string
  modo_control: 'calendario' | 'accesos'
  duracion_tipo: 'dias' | 'semanas' | 'meses' | 'años'
  duracion_valor: number
  dias_acceso?: number | null
  precio: number
}

export interface PlantillaOption {
  id: string
  emoji: string
  label: string
  desc: string
  colSpan?: number
  defaults: Partial<FormValues>
}

export const PLANTILLAS: PlantillaOption[] = [
  {
    id: 'mensual',
    emoji: '📅',
    label: 'Mensual',
    desc: '1 mes · calendario',
    defaults: { nombre: 'Mensual', modo_control: 'calendario', duracion_tipo: 'meses', duracion_valor: 1 },
  },
  {
    id: 'trimestral',
    emoji: '📅',
    label: 'Trimestral',
    desc: '3 meses · calendario',
    defaults: { nombre: 'Trimestral', modo_control: 'calendario', duracion_tipo: 'meses', duracion_valor: 3 },
  },
  {
    id: 'anual',
    emoji: '📅',
    label: 'Anual',
    desc: '12 meses · calendario',
    defaults: { nombre: 'Anual', modo_control: 'calendario', duracion_tipo: 'años', duracion_valor: 1 },
  },
  {
    id: 'accesos',
    emoji: '🎯',
    label: 'Por accesos',
    desc: 'X entradas al gym',
    defaults: { nombre: '', modo_control: 'accesos', duracion_tipo: 'meses', duracion_valor: 1, dias_acceso: 10 },
  },
  {
    id: 'custom',
    emoji: '✏️',
    label: 'Personalizado',
    desc: 'Configuro todo desde cero',
    colSpan: 2,
    defaults: { modo_control: 'calendario', duracion_tipo: 'meses', duracion_valor: 1, precio: 0 },
  },
]
