const programs = [
  {
    title: 'Basic communication program',
    summary:
      'Learn essential signs and expressions used most often in family life.',
    level: 'Intro',
  },
  {
    title: 'School-age learning coaching',
    summary:
      'Guidance for homework, school notices, and parent-teacher communication.',
    level: 'Core',
  },
  {
    title: 'Family collaboration workshop',
    summary:
      'Practice role sharing and decision-making routines for family teamwork.',
    level: 'Advanced',
  },
]

function ProgramsPage() {
  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h2 className="text-3xl font-bold leading-tight">Programs</h2>
        <p className="text-text-secondary">
          Program tracks focused on real situations you can apply immediately.
        </p>
      </div>

      <div className="grid gap-4">
        {programs.map((program) => (
          <article
            key={program.title}
            className="rounded-xl border border-border bg-surface p-5"
          >
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-xl font-semibold">{program.title}</h3>
              <span className="rounded-md bg-info-subtle px-2 py-1 text-xs font-semibold text-info">
                {program.level}
              </span>
            </div>
            <p className="mt-2 text-text-secondary">{program.summary}</p>
          </article>
        ))}
      </div>
    </section>
  )
}

export default ProgramsPage
