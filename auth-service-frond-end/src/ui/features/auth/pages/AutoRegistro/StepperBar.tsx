import { Check } from 'lucide-react'

interface Step {
  label: string
}

interface Props {
  steps: Step[]
  currentStep: number
}

export function StepperBar({ steps, currentStep }: Props) {
  return (
    <div className="flex items-center w-full mb-6">
      {steps.map((step, index) => {
        const stepNumber = index + 1
        const isCompleted = stepNumber < currentStep
        const isCurrent = stepNumber === currentStep

        return (
          <div key={step.label} className="flex items-center flex-1 last:flex-none">
            <div className="flex flex-col items-center">
              <div
                className="w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 transition-all"
                style={{
                  background: isCompleted ? '#f97316' : 'var(--page-surface)',
                  border: isCurrent
                    ? '2px solid #f97316'
                    : isCompleted
                      ? '2px solid #f97316'
                      : '2px solid var(--page-border)',
                }}
              >
                {isCompleted ? (
                  <Check size={14} className="text-white" />
                ) : (
                  <span
                    className="text-xs font-semibold"
                    style={{ color: isCurrent ? '#f97316' : 'var(--page-muted)' }}
                  >
                    {stepNumber}
                  </span>
                )}
              </div>
              <span
                className="text-[0.65rem] mt-1 font-medium whitespace-nowrap"
                style={{ color: isCurrent ? '#f97316' : 'var(--page-muted)' }}
              >
                {step.label}
              </span>
            </div>

            {index < steps.length - 1 && (
              <div
                className="h-0.5 flex-1 mx-1 mb-4"
                style={{ background: isCompleted ? '#f97316' : 'var(--page-border)' }}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}
