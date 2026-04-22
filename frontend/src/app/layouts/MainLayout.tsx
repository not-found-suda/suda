import { NavLink, Outlet } from 'react-router-dom'
import { ROUTES } from '@/shared/constants/routes'
import { H1 } from '@/shared/ui'

const navigation = [
  { label: 'Home', to: ROUTES.home },
  { label: 'Programs', to: ROUTES.programs },
  { label: 'Resources', to: ROUTES.resources },
  { label: 'FAQ', to: ROUTES.faq },
]

function MainLayout() {
  return (
    <div className="min-h-screen bg-bg text-text-primary">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-4 px-4 py-5 md:px-6">
          <p className="text-xs font-medium tracking-wide text-text-secondary">
            CODA SUPPORT PLATFORM
          </p>
          <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <H1 className="text-2xl md:text-3xl">Family Education Support</H1>
            <nav className="flex flex-wrap gap-2" aria-label="Main menu">
              {navigation.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === ROUTES.home}
                  className={({ isActive }) =>
                    [
                      'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-brand text-text-inverse'
                        : 'bg-surface text-text-secondary hover:bg-brand-subtle hover:text-text-primary',
                    ].join(' ')
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-4 py-8 md:px-6 md:py-10">
        <Outlet />
      </main>
    </div>
  )
}

export default MainLayout

