import MainLayout from '@/app/layouts/MainLayout'
import { FaqPage, HomePage, NotFoundPage, ProgramsPage, ResourcesPage } from '@/pages'
import { ROUTES } from '@/shared/constants/routes'
import { Route, Routes } from 'react-router-dom'

function AppRouter() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path={ROUTES.home} element={<HomePage />} />
        <Route path={ROUTES.programs} element={<ProgramsPage />} />
        <Route path={ROUTES.resources} element={<ResourcesPage />} />
        <Route path={ROUTES.faq} element={<FaqPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default AppRouter
