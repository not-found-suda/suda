import type { ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'brand' | 'outline'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

const baseClassName =
  'inline-flex items-center justify-center rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors'

const variantClassName: Record<ButtonVariant, string> = {
  brand:
    'bg-brand text-text-inverse hover:bg-brand-hover',
  outline:
    'border border-border bg-surface text-text-primary hover:border-border-strong',
}

function Button({ className = '', variant = 'brand', ...props }: ButtonProps) {
  return (
    <button
      className={`${baseClassName} ${variantClassName[variant]} ${className}`}
      {...props}
    />
  )
}

export default Button

