const resourceGroups = [
  {
    title: 'Home communication kit',
    description:
      'Scenario scripts, sign cards, and daily conversation checklists.',
    tagColor: 'text-brand bg-brand-subtle',
  },
  {
    title: 'School collaboration kit',
    description:
      'Notice-response guides, teacher collaboration templates, and meeting notes.',
    tagColor: 'text-point bg-point-subtle',
  },
  {
    title: 'Emotional support kit',
    description:
      'Emotion logs, family conversation routines, and counseling resources.',
    tagColor: 'text-info bg-info-subtle',
  },
]

function ResourcesPage() {
  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h2 className="text-3xl font-bold leading-tight">Resources</h2>
        <p className="text-text-secondary">
          Materials are grouped by purpose so teams can find things fast.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {resourceGroups.map((group) => (
          <article
            key={group.title}
            className="rounded-xl border border-border bg-surface p-5"
          >
            <span
              className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${group.tagColor}`}
            >
              Resource set
            </span>
            <h3 className="mt-3 text-lg font-semibold">{group.title}</h3>
            <p className="mt-2 text-sm text-text-secondary">{group.description}</p>
          </article>
        ))}
      </div>
    </section>
  )
}

export default ResourcesPage
