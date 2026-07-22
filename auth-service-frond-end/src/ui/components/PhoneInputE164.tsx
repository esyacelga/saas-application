/**
 * PhoneInputE164 — Shared international phone input with E.164 output.
 *
 * Uses react-phone-number-input (libphonenumber-js) — the same engine WhatsApp
 * Web uses. Output is always E.164 (e.g. +593999123456) or undefined.
 *
 * Exports:
 *   - PhoneInputE164             — controlled primitive (for custom wiring)
 *   - PhoneInputE164Controller   — react-hook-form Controller wrapper (preferred)
 *   - parsePhoneToE164           — helper to hydrate legacy DB values (Opción A)
 */

import { Controller, type Control, type FieldValues, type Path } from 'react-hook-form'
import PhoneInput, { parsePhoneNumber } from 'react-phone-number-input'
import type { Value as E164Value } from 'react-phone-number-input'
import { cn } from '@/lib/utils'

// ── Helper: parse legacy raw values to E.164 ─────────────────────────────────

/**
 * Attempt to parse a raw phone string (any format) to E.164.
 *
 * Opción A logic:
 *   - If the value is empty / null / undefined → { value: undefined, parsedOk: true }
 *   - If libphonenumber-js can parse it (with 'EC' as default country) → { value: E164, parsedOk: true }
 *   - Otherwise (truly unparseable legacy garbage) → { value: undefined, parsedOk: false }
 *     The caller should show a warning and leave the field empty to force re-entry.
 *
 * Examples:
 *   "0999123456"  → { value: "+593999123456", parsedOk: true }
 *   "+593999123456" → { value: "+593999123456", parsedOk: true }
 *   "+593 99 912 3456" → { value: "+593999123456", parsedOk: true }
 *   "593999123456" → { value: "+593999123456", parsedOk: true }
 *   "garbage-123" → { value: undefined, parsedOk: false }
 */
export function parsePhoneToE164(raw: string | null | undefined): {
  value: string | undefined
  parsedOk: boolean
} {
  if (!raw || !raw.trim()) {
    return { value: undefined, parsedOk: true }
  }
  try {
    const parsed = parsePhoneNumber(raw.trim(), 'EC')
    if (parsed && parsed.isValid()) {
      return { value: parsed.format('E.164'), parsedOk: true }
    }
  } catch {
    // parsePhoneNumber can throw on completely unparseable input
  }
  return { value: undefined, parsedOk: false }
}

// ── Base component props ──────────────────────────────────────────────────────

interface PhoneInputE164Props {
  value: string | undefined
  onChange: (value: string | undefined) => void
  defaultCountry?: string
  disabled?: boolean
  id?: string
  className?: string
  placeholder?: string
  error?: boolean
}

// ── CSS for the PhoneInput — applied via className on the wrapper ─────────────
//
// react-phone-number-input renders:
//   <div class="PhoneInput">
//     <div class="PhoneInputCountry">
//       <select class="PhoneInputCountrySelect">
//       <div class="PhoneInputCountryIcon">
//     <input class="PhoneInputInput">
//
// We override all of these using CSS vars so they adapt to every theme.

const PHONE_INPUT_WRAPPER = cn(
  // The library uses .PhoneInput as the root — we style children via these classes
  '[&_.PhoneInputInput]:w-full',
  '[&_.PhoneInputInput]:px-3',
  '[&_.PhoneInputInput]:py-2',
  '[&_.PhoneInputInput]:text-sm',
  '[&_.PhoneInputInput]:rounded-r-lg',
  '[&_.PhoneInputInput]:focus:outline-none',
  '[&_.PhoneInputInput]:focus:ring-2',
  '[&_.PhoneInputInput]:focus:ring-orange-500',
  '[&_.PhoneInputInput]:transition-colors',

  '[&_.PhoneInputCountry]:flex',
  '[&_.PhoneInputCountry]:items-center',
  '[&_.PhoneInputCountry]:gap-1',
  '[&_.PhoneInputCountry]:rounded-l-lg',
  '[&_.PhoneInputCountry]:pl-2',
  '[&_.PhoneInputCountry]:pr-1',

  '[&_.PhoneInputCountrySelect]:cursor-pointer',
  '[&_.PhoneInputCountrySelect]:bg-transparent',
  '[&_.PhoneInputCountrySelect]:text-sm',
  '[&_.PhoneInputCountrySelect]:focus:outline-none',
  '[&_.PhoneInputCountrySelect]:border-0',
  '[&_.PhoneInputCountrySelect]:appearance-none',
  '[&_.PhoneInputCountrySelect]:p-0',
)

// ── PhoneInputE164 (primitive) ────────────────────────────────────────────────

export function PhoneInputE164({
  value,
  onChange,
  defaultCountry = 'EC',
  disabled = false,
  id,
  className,
  placeholder,
  error = false,
}: PhoneInputE164Props) {
  return (
    <div
      className={cn(
        'flex rounded-lg overflow-hidden',
        PHONE_INPUT_WRAPPER,
        className,
      )}
      style={{
        background: 'var(--input-bg)',
        border: `1px solid ${error ? '#f87171' : 'var(--input-border)'}`,
        color: 'var(--input-text)',
      }}
    >
      <PhoneInput
        international
        countryCallingCodeEditable={false}
        defaultCountry={defaultCountry as never}
        value={(value as E164Value) ?? ''}
        onChange={(v) => onChange(v ?? undefined)}
        disabled={disabled}
        id={id}
        placeholder={placeholder}
        style={
          {
            // Pass CSS vars into the library's inline-style slots
            '--PhoneInput-color--focus': 'transparent',
            '--PhoneInputCountrySelectArrow-color': 'var(--input-text)',
            '--PhoneInputCountrySelectArrow-opacity': '0.6',
            '--PhoneInputCountryFlag-aspectRatio': '3/2',
            '--PhoneInputCountryFlag-height': '1em',
            '--PhoneInputCountryFlag-borderColor': 'rgba(0,0,0,0.1)',
          } as React.CSSProperties
        }
        inputStyle={{
          background: 'var(--input-bg)',
          color: 'var(--input-text)',
          border: 'none',
          borderLeft: '1px solid var(--input-border)',
        }}
        countrySelectProps={{
          style: {
            background: 'var(--input-bg)',
            color: 'var(--input-text)',
          },
        }}
      />
    </div>
  )
}

// ── PhoneInputE164Controller (react-hook-form wrapper) ────────────────────────

interface PhoneInputE164ControllerProps<T extends FieldValues> {
  name: Path<T>
  control: Control<T>
  defaultCountry?: string
  disabled?: boolean
  placeholder?: string
  className?: string
}

export function PhoneInputE164Controller<T extends FieldValues>({
  name,
  control,
  defaultCountry = 'EC',
  disabled,
  placeholder,
  className,
}: PhoneInputE164ControllerProps<T>) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <PhoneInputE164
          value={field.value as string | undefined}
          onChange={field.onChange}
          defaultCountry={defaultCountry}
          disabled={disabled}
          placeholder={placeholder}
          error={!!fieldState.error}
          className={className}
          id={String(name)}
        />
      )}
    />
  )
}
