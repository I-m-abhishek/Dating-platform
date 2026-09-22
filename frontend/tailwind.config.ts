import type { Config } from 'tailwindcss';

/**
 * The design system.
 *
 * A warm rose and a cool violet travel together as one gradient accent, gold marks anything
 * premium, and the rest is ink, surface and space. Every colour resolves through a CSS
 * variable so a component never needs a dark: variant to change colour - only to change
 * contrast, which is rare.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        bg: 'rgb(var(--bg) / <alpha-value>)',
        'bg-deep': 'rgb(var(--bg-deep) / <alpha-value>)',
        surface: 'rgb(var(--surface) / <alpha-value>)',
        'surface-muted': 'rgb(var(--surface-muted) / <alpha-value>)',
        'surface-raised': 'rgb(var(--surface-raised) / <alpha-value>)',
        border: 'rgb(var(--border) / <alpha-value>)',
        'border-strong': 'rgb(var(--border-strong) / <alpha-value>)',
        ink: 'rgb(var(--ink) / <alpha-value>)',
        'ink-muted': 'rgb(var(--ink-muted) / <alpha-value>)',
        'ink-subtle': 'rgb(var(--ink-subtle) / <alpha-value>)',
        accent: 'rgb(var(--accent) / <alpha-value>)',
        'accent-2': 'rgb(var(--accent-2) / <alpha-value>)',
        'accent-3': 'rgb(var(--accent-3) / <alpha-value>)',
        'accent-soft': 'rgb(var(--accent-soft) / <alpha-value>)',
        'accent-ink': 'rgb(var(--accent-ink) / <alpha-value>)',
        gold: 'rgb(var(--gold) / <alpha-value>)',
        'canvas-dark': 'rgb(var(--canvas-dark) / <alpha-value>)',
        success: 'rgb(var(--success) / <alpha-value>)',
        danger: 'rgb(var(--danger) / <alpha-value>)',
      },
      fontFamily: {
        sans: ['var(--font-sans)'],
        display: ['var(--font-display)'],
      },
      borderRadius: {
        card: '1.5rem',
        xl2: '1.25rem',
        pill: '9999px',
      },
      boxShadow: {
        card: '0 1px 2px rgb(24 20 28 / 0.04), 0 10px 30px -18px rgb(24 20 28 / 0.28)',
        lift: '0 2px 4px rgb(24 20 28 / 0.05), 0 22px 50px -24px rgb(24 20 28 / 0.35)',
        float: '0 18px 55px -20px rgb(24 20 28 / 0.4)',
        sheet: '0 -10px 60px -18px rgb(24 20 28 / 0.4)',
        // Coloured glows belong to the accent, so they shift with the theme too.
        glow: '0 8px 26px -10px rgb(var(--accent) / 0.75)',
        'glow-lg': '0 16px 44px -14px rgb(var(--accent) / 0.65)',
        inset: 'inset 0 1px 0 0 rgb(255 255 255 / 0.08)',
      },
      backgroundImage: {
        'accent-gradient': 'linear-gradient(135deg, rgb(var(--accent)), rgb(var(--accent-2)))',
        'accent-gradient-soft':
          'linear-gradient(135deg, rgb(var(--accent) / 0.14), rgb(var(--accent-2) / 0.14))',
        'gold-gradient': 'linear-gradient(135deg, rgb(var(--accent-3)), rgb(var(--gold)))',
        // Sits under text laid over a photo, so the caption stays legible on any image.
        'photo-scrim':
          'linear-gradient(to top, rgb(0 0 0 / 0.85) 0%, rgb(0 0 0 / 0.45) 34%, transparent 68%)',
      },
      transitionTimingFunction: {
        spring: 'cubic-bezier(0.16, 1, 0.3, 1)',
        snap: 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'slide-up': {
          from: { transform: 'translateY(16px)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' },
        },
        'sheet-in': {
          from: { transform: 'translateY(100%)' },
          to: { transform: 'translateY(0)' },
        },
        'scale-in': {
          from: { transform: 'scale(0.94)', opacity: '0' },
          to: { transform: 'scale(1)', opacity: '1' },
        },
        shimmer: { '100%': { transform: 'translateX(100%)' } },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-7px)' },
        },
        // The halo that leaves an avatar when something new has arrived.
        'pulse-ring': {
          '0%': { transform: 'scale(0.9)', opacity: '0.7' },
          '70%': { transform: 'scale(1.35)', opacity: '0' },
          '100%': { transform: 'scale(1.35)', opacity: '0' },
        },
        'gradient-pan': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
        'heart-pop': {
          '0%': { transform: 'scale(0.6)', opacity: '0' },
          '45%': { transform: 'scale(1.25)', opacity: '1' },
          '100%': { transform: 'scale(1)', opacity: '1' },
        },
        'typing-dot': {
          '0%, 60%, 100%': { transform: 'translateY(0)', opacity: '0.45' },
          '30%': { transform: 'translateY(-4px)', opacity: '1' },
        },
      },
      animation: {
        'fade-in': 'fade-in 180ms ease-out',
        'slide-up': 'slide-up 260ms cubic-bezier(0.16, 1, 0.3, 1)',
        'sheet-in': 'sheet-in 320ms cubic-bezier(0.16, 1, 0.3, 1)',
        'scale-in': 'scale-in 220ms cubic-bezier(0.16, 1, 0.3, 1)',
        shimmer: 'shimmer 1.6s infinite',
        float: 'float 5s ease-in-out infinite',
        'pulse-ring': 'pulse-ring 2.2s cubic-bezier(0.24, 0, 0.38, 1) infinite',
        'gradient-pan': 'gradient-pan 6s ease infinite',
        'heart-pop': 'heart-pop 420ms cubic-bezier(0.16, 1, 0.3, 1)',
        'typing-dot': 'typing-dot 1.2s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};

export default config;
