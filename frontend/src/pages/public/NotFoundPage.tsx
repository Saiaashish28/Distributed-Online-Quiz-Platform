import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="text-5xl font-bold text-slate-300">404</p>
      <p className="text-lg font-medium text-slate-800">Page not found</p>
      <Button asChild>
        <Link to="/">Go home</Link>
      </Button>
    </div>
  )
}
