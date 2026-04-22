import { login } from '@/features/auth/api/authApi'
import { Button, Input } from '@/shared/ui'
import type { FormEvent } from 'react'
import { useState } from 'react'

function LoginForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [message, setMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsLoading(true)
    setMessage('')

    try {
      await login({ email, password })
      setMessage('Login success.')
    } catch {
      setMessage('Login failed. Please check your credentials.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <form
      className="grid gap-3"
      onSubmit={(event) => {
        void handleSubmit(event)
      }}
    >
      <label className="grid gap-1 text-sm">
        <span>Email</span>
        <Input
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </label>
      <label className="grid gap-1 text-sm">
        <span>Password</span>
        <Input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </label>
      <Button type="submit" disabled={isLoading} className="disabled:opacity-60">
        {isLoading ? 'Signing in...' : 'Sign in'}
      </Button>
      {message ? (
        <p className="text-sm text-text-secondary">{message}</p>
      ) : null}
    </form>
  )
}

export default LoginForm

