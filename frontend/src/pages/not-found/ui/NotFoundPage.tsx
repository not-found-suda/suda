import { Link } from 'react-router-dom'

function NotFoundPage() {
  return (
    <section className="rounded-xl border border-border bg-surface p-8 text-center">
      <p className="text-sm font-semibold text-text-secondary">404</p>
      <h2 className="mt-2 text-3xl font-bold leading-tight">페이지를 찾을 수 없습니다.</h2>
      <p className="mt-3 text-text-secondary">
        주소가 변경되었거나 존재하지 않는 페이지입니다.
      </p>
      <div className="mt-6">
        <Link
          to="/"
          className="inline-flex rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-text-inverse transition-colors hover:bg-brand-hover"
        >
          홈으로 이동
        </Link>
      </div>
    </section>
  )
}

export default NotFoundPage
