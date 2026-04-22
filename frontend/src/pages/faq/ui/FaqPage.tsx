const faqs = [
  {
    question: '이 사이트는 누구를 위한 서비스인가요?',
    answer:
      '청각장애인 부모 가정에서 자라는 CODA 아동과 보호자, 교사를 위한 교육 정보 플랫폼입니다.',
  },
  {
    question: '자료는 어떤 순서로 활용하면 좋나요?',
    answer:
      '가정의 현재 상황을 먼저 점검한 뒤, 입문 자료부터 단계적으로 학습하는 방식을 권장합니다.',
  },
  {
    question: '학교나 상담 연계가 필요할 때는 어떻게 하나요?',
    answer:
      '상담 준비 문서와 가정·학교 소통 가이드를 먼저 참고한 뒤 필요한 기관과 연계해 보세요.',
  },
]

function FaqPage() {
  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h2 className="text-3xl font-bold leading-tight">자주 묻는 질문</h2>
        <p className="text-text-secondary">
          서비스 이용 전 가장 많이 궁금해하는 내용을 정리했습니다.
        </p>
      </div>

      <div className="space-y-3">
        {faqs.map((faq) => (
          <article
            key={faq.question}
            className="rounded-xl border border-border bg-surface p-5"
          >
            <h3 className="text-lg font-semibold">{faq.question}</h3>
            <p className="mt-2 text-text-secondary">{faq.answer}</p>
          </article>
        ))}
      </div>
    </section>
  )
}

export default FaqPage
