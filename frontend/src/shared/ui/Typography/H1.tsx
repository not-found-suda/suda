import type { HTMLAttributes } from 'react'

type H1Props = HTMLAttributes<HTMLHeadingElement>

function H1({ className = '', ...props }: H1Props) {
  return (
    <h1
      className={`text-3xl font-bold leading-tight text-text-primary md:text-4xl ${className}`}
      {...props}
    />
  )
}

export default H1

