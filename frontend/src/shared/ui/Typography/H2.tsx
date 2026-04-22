import type { HTMLAttributes } from 'react'

type H2Props = HTMLAttributes<HTMLHeadingElement>

function H2({ className = '', ...props }: H2Props) {
  return (
    <h2
      className={`text-2xl font-semibold leading-tight text-text-primary md:text-3xl ${className}`}
      {...props}
    />
  )
}

export default H2

