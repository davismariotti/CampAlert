import { useState } from 'react'
import { Link } from 'react-router-dom'
import landscapeImg from '../assets/landscape.jpg'
import { LoginModal } from '../components/LoginModal'

const steps = [
  {
    num: '01',
    title: 'Search for any campground',
    body: 'Find any Recreation.gov campground by name — national parks, national forests, lakesides.'
  },
  {
    num: '02',
    title: 'Set your dates and group size',
    body: 'Pick a start date, number of nights, how many people, and optionally a specific loop.'
  },
  {
    num: '03',
    title: 'We watch around the clock',
    body: 'CampAlert checks availability every 2 minutes, every day, without you lifting a finger.'
  },
  {
    num: '04',
    title: 'Get a text the moment it opens',
    body: 'When a cancellation frees up your site, you get an SMS instantly — before anyone else sees it.'
  }
]

export function LandingPage() {
  const [showLogin, setShowLogin] = useState(false)

  return (
    <div className="min-h-screen bg-white text-forest-900">
      {/* Nav — logo only + subtle sign-in */}
      <header className="flex h-14 items-center justify-between px-5">
        <Link to="/" className="flex items-center gap-2">
          <img src="/logo.png" alt="CampAlert" className="h-8 w-8 rounded" />
          <span className="font-semibold">CampAlert</span>
        </Link>
        <button
          type="button"
          onClick={() => setShowLogin(true)}
          className="text-sm text-forest-500 hover:text-forest-800"
        >
          Sign in
        </button>
      </header>

      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}

      {/* Hero — full-bleed photo, centered text */}
      <section
        className="relative flex min-h-[560px] items-center justify-center text-center"
        style={{ backgroundImage: `url(${landscapeImg})`, backgroundSize: 'cover', backgroundPosition: 'center 40%' }}
      >
        <div className="absolute inset-0 bg-forest-950/70" />
        <div className="relative z-10 px-5 py-20">
          <h1 className="mb-4 text-4xl font-bold tracking-tight text-white sm:text-5xl">
            Never miss a campsite opening.
          </h1>
          <p className="mx-auto mb-10 max-w-sm text-base text-white/75 sm:max-w-md sm:text-lg">
            CampAlert watches Recreation.gov for you and texts you the moment a site opens up.
          </p>
          <Link
            to="/register"
            className="inline-block rounded-2xl bg-amber-500 px-8 py-4 text-base font-semibold text-white hover:bg-amber-600"
          >
            Get started — it's free
          </Link>
        </div>
      </section>

      {/* Problem */}
      <section className="px-5 py-16 text-center">
        <div className="mx-auto max-w-md">
          <h2 className="mb-4 text-2xl font-bold tracking-tight">Campgrounds fill up in seconds.</h2>
          <p className="leading-relaxed text-forest-600">
            Yosemite, Olympic, Glacier — the sites everyone wants are gone the day they open. But people cancel all the
            time. Those sites come back. The problem is, nobody's watching.
          </p>
        </div>
      </section>

      {/* SMS mockup */}
      <section className="bg-stone-50 px-5 py-16">
        <div className="mx-auto max-w-sm">
          <p className="mb-6 text-center text-sm font-semibold uppercase tracking-widest text-forest-400">
            What you'll receive
          </p>

          {/* Phone frame */}
          <div className="mx-auto max-w-xs rounded-3xl border-4 border-forest-900 bg-white p-4 shadow-xl">
            {/* Status bar */}
            <div className="mb-3 flex items-center justify-between px-1 text-xs text-stone-400">
              <span>9:41 AM</span>
              <span>●●●</span>
            </div>
            {/* Sender label */}
            <div className="mb-2 text-center text-xs font-medium text-stone-400">CampAlert</div>
            {/* SMS bubble */}
            <div className="rounded-2xl rounded-tl-sm bg-stone-100 px-4 py-3">
              <p className="text-sm text-stone-800">
                <span className="font-semibold">Yosemite Valley Campground</span> has availability for your dates (Jun
                15–18, 4 people).
              </p>
              <p className="mt-2 text-xs font-medium text-forest-600">
                Book now → recreation.gov/camping/campgrounds/70928
              </p>
            </div>
            <p className="mt-2 px-1 text-right text-xs text-stone-300">Delivered</p>
          </div>

          <p className="mt-6 text-center text-sm text-forest-500">
            You get this text before the site shows up in any search.
          </p>
        </div>
      </section>

      {/* How it works — vertical steps */}
      <section className="px-5 py-16">
        <div className="mx-auto max-w-lg">
          <p className="mb-2 text-sm font-semibold uppercase tracking-widest text-forest-400">How it works</p>
          <h2 className="mb-10 text-2xl font-bold tracking-tight">Set it up in two minutes.</h2>

          <div className="flex flex-col">
            {steps.map((step, i) => (
              <div key={step.num} className="flex gap-5">
                {/* Step number + connector line */}
                <div className="flex flex-col items-center">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-forest-100 text-sm font-bold text-forest-700">
                    {step.num}
                  </div>
                  {i < steps.length - 1 && <div className="mt-1 w-px flex-1 bg-forest-100" />}
                </div>
                {/* Content */}
                <div className={`pb-10 ${i === steps.length - 1 ? 'pb-0' : ''}`}>
                  <h3 className="mb-1 font-semibold text-forest-900">{step.title}</h3>
                  <p className="text-sm leading-relaxed text-forest-600">{step.body}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Final CTA */}
      <section className="bg-forest-950 px-5 py-20 text-center">
        <div className="mx-auto max-w-sm">
          <h2 className="mb-4 text-3xl font-bold tracking-tight text-white">
            Stop refreshing.
            <br />
            Start camping.
          </h2>
          <p className="mb-8 text-base text-white/60">
            Free to use. No credit card. Just set an alert and let us do the watching.
          </p>
          <Link
            to="/register"
            className="inline-block rounded-2xl bg-amber-500 px-8 py-4 text-base font-semibold text-white hover:bg-amber-600"
          >
            Get started free
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-forest-950 px-5 pb-8 pt-2">
        <div className="mx-auto flex max-w-sm flex-col items-center gap-2 border-t border-white/10 pt-6 text-center">
          <div className="flex gap-4 text-sm text-white/30">
            <Link to="/terms" className="hover:text-white/60">
              Terms
            </Link>
            <Link to="/privacy" className="hover:text-white/60">
              Privacy
            </Link>
          </div>
          <p className="text-xs text-white/20">
            © {new Date().getFullYear()} CampAlert · Not affiliated with Recreation.gov
          </p>
        </div>
      </footer>
    </div>
  )
}
