import { Link } from 'react-router-dom'

const highlights = [
  {
    title: 'Family communication',
    description:
      'Practical communication guides for daily situations at home.',
    className: 'bg-brand-subtle',
  },
  {
    title: 'Learning support',
    description:
      'Age-based language and school support checklists for CODA families.',
    className: 'bg-point-subtle',
  },
  {
    title: 'Emotional care',
    description:
      'Simple routines to support emotional stability for parents and children.',
    className: 'bg-info-subtle',
  },
]

function HomePage() {
  return (
    <section className="space-y-8">
      <div className="space-y-4">
        <span className="inline-flex rounded-full bg-brand-subtle px-3 py-1 text-xs font-semibold text-brand">
          Learning together platform
        </span>
        <h2 className="text-3xl font-bold leading-tight md:text-4xl">
          Find what you need quickly and apply it right away.
        </h2>
        <p className="max-w-3xl text-text-secondary">
          This platform gathers education resources for children in deaf-parent
          families and helps families and schools collaborate with confidence.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {highlights.map((item) => (
          <article
            key={item.title}
            className={`rounded-xl border border-border p-5 ${item.className}`}
          >
            <h3 className="text-lg font-semibold">{item.title}</h3>
            <p className="mt-2 text-sm text-text-secondary">{item.description}</p>
          </article>
        ))}
      </div>

      <div className="flex flex-wrap gap-3">
        <Link
          to="/programs"
          className="rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-text-inverse transition-colors hover:bg-brand-hover"
        >
          View programs
        </Link>
        <Link
          to="/resources"
          className="rounded-lg border border-border bg-surface px-4 py-2.5 text-sm font-semibold text-text-primary transition-colors hover:border-border-strong"
        >
          Browse resources
        </Link>
      </div>
    </section>
  )
}

export default HomePage
