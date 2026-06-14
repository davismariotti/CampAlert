import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import landscapeImg from '../../assets/landscape.jpg'

export function AuthPageShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-stone-50">
      <div
        className="relative flex h-48 items-center justify-center"
        style={{
          backgroundImage: `url(${landscapeImg})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center 55%'
        }}
      >
        <div className="absolute inset-0 bg-forest-950/65" />
        <Link to="/" className="relative z-10 flex flex-col items-center gap-2">
          <img src="/logo.png" alt="CampAlert" className="h-12 w-12 rounded-2xl" />
          <span className="text-lg font-bold text-white">CampAlert</span>
        </Link>
      </div>
      <div className="px-5 py-10">
        <div className="mx-auto max-w-sm rounded-2xl bg-white p-8 shadow-sm">{children}</div>
      </div>
    </div>
  )
}
