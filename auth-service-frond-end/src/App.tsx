import { RouterProvider } from 'react-router-dom'
import { Toaster } from 'sonner'
import { PrimeReactProvider } from 'primereact/api'
import { router } from '@/ui/router'
import { TopLoader } from '@/ui/components/TopLoader'
import { UpgradeModal } from '@/ui/components/UpgradeModal'

export function App() {
  return (
    <PrimeReactProvider>
      <TopLoader />
      <RouterProvider router={router} />
      {/* Singleton modal de límite de plan — controlado por useLimitPlanModalStore */}
      <UpgradeModal />
      <Toaster
        position="top-right"
        richColors
        toastOptions={{ classNames: { toast: 'font-sans text-sm' } }}
      />
    </PrimeReactProvider>
  )
}
