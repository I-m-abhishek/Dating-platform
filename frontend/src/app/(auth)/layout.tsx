import type { ReactNode } from 'react';
import { LogoMark, HeartIcon, ShieldCheckIcon, SparkleIcon } from '@/components/ui/icons';

const PROMISES = [
  {
    Icon: SparkleIcon,
    title: 'A match chosen for you',
    body: 'Not a deck of a thousand faces. One person at a time, picked on what you actually share.',
  },
  {
    Icon: HeartIcon,
    title: 'Prompts before photos',
    body: 'You meet a sentence, then a face. It turns out people are more interesting that way.',
  },
  {
    Icon: ShieldCheckIcon,
    title: 'Verified, and private',
    body: 'Photo verification as standard. Browse without leaving a trail behind you.',
  },
];

/**
 * The signed-out shell.
 *
 * <p>Two panels on a wide screen: the promise on the left, the form on the right. On a phone
 * the left panel is dropped entirely rather than stacked above the form - nobody scrolls
 * past marketing to reach a password field.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen bg-bg">
      <div aria-hidden className="aurora lg:hidden" />

      {/* ---- The promise ---- */}
      <section className="relative hidden w-[46%] max-w-2xl overflow-hidden bg-canvas-dark lg:flex lg:flex-col lg:justify-between">
        <div aria-hidden className="absolute inset-0">
          <div className="absolute -left-40 -top-40 h-[34rem] w-[34rem] rounded-full bg-accent/45 blur-[110px]" />
          <div className="absolute -bottom-52 -right-32 h-[36rem] w-[36rem] rounded-full bg-accent-2/40 blur-[110px]" />
        </div>

        <div className="relative z-10 flex items-center gap-2.5 p-12">
          <LogoMark size={32} />
          <span className="font-display text-[22px] font-semibold tracking-[-0.02em] text-white">
            Two &amp; Two
          </span>
        </div>

        <div className="relative z-10 px-12">
          <h2 className="max-w-md font-display text-[44px] font-semibold leading-[1.08] tracking-[-0.03em] text-white">
            Meet someone you would <em className="not-italic text-accent">actually</em> like.
          </h2>
          <p className="mt-5 max-w-sm text-[15px] leading-relaxed text-white/65">
            Two &amp; Two is built for the conversation after the match, not the swipe before it.
          </p>

          <ul className="mt-12 space-y-7">
            {PROMISES.map(({ Icon, title, body }) => (
              <li key={title} className="flex gap-4">
                <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl2 bg-white/10 text-white ring-1 ring-inset ring-white/15">
                  <Icon size={19} />
                </span>
                <span className="max-w-sm">
                  <span className="block text-[15px] font-semibold text-white">{title}</span>
                  <span className="mt-1 block text-[13.5px] leading-relaxed text-white/55">
                    {body}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative z-10 p-12 text-xs font-medium text-white/35">
          Photo-verified profiles · 18+ only
        </p>
      </section>

      {/* ---- The form ---- */}
      <div className="relative z-10 flex flex-1 flex-col justify-center px-6 py-12 sm:px-10">
        <div className="mx-auto w-full max-w-[26rem]">
          <div className="mb-9 flex flex-col items-center text-center lg:hidden">
            <LogoMark size={44} className="mb-3 animate-float" />
            <p className="font-display text-[27px] font-semibold tracking-[-0.02em] text-ink">
              Two <span className="text-gradient">&amp;</span> Two
            </p>
            <p className="mt-1.5 text-sm text-ink-muted">One good match at a time.</p>
          </div>

          {children}
        </div>
      </div>
    </div>
  );
}
