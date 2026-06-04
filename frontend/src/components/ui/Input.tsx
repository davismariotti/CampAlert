import { forwardRef, type InputHTMLAttributes } from 'react'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...props }, ref) => (
    <input
      ref={ref}
      className={`w-full rounded-xl border border-forest-200 bg-white px-3 py-2 text-sm text-forest-900 placeholder:text-forest-300 focus:border-forest-500 focus:outline-none focus:ring-2 focus:ring-forest-500/30 ${className}`}
      {...props}
    />
  )
)
Input.displayName = 'Input'
