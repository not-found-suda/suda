import type { InputHTMLAttributes } from 'react'

type InputProps = InputHTMLAttributes<HTMLInputElement>

function Input({ className = '', ...props }: InputProps) {
  return (
    <input
      className={`w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-secondary focus:border-brand focus:outline-none ${className}`}
      {...props}
    />
  )
}

export default Input

