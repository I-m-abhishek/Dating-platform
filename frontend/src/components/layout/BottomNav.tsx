'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils/cn';
import { CountBadge } from '@/components/ui/Badge';
import { useUnseenLikeCount } from '@/lib/hooks/useLikes';
import { useUnreadCount } from '@/lib/hooks/useChat';
import { useMatchCount } from '@/lib/hooks/useMatches';

interface NavItem {
  href: string;
  label: string;
  icon: string;
  badge?: number;
}

/**
 * The five destinations. Deliberately five - a dating app with more tabs than that has an
 * information architecture problem, not a navigation one.
 */
export function BottomNav() {
  const pathname = usePathname();
  const { data: unseenLikes = 0 } = useUnseenLikeCount();
  const { data: unread = 0 } = useUnreadCount();
  const { data: matches = 0 } = useMatchCount();

  const items: NavItem[] = [
    { href: '/home', label: 'Discover', icon: '◎' },
    { href: '/standouts', label: 'Standouts', icon: '✦' },
    { href: '/likes', label: 'Likes', icon: '♡', badge: unseenLikes },
    { href: '/matches', label: 'Matches', icon: '◈', badge: matches },
    { href: '/messages', label: 'Chats', icon: '✉', badge: unread },
  ];

  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface/95 backdrop-blur-md md:hidden"
    >
      <ul className="mx-auto flex max-w-lg items-stretch">
        {items.map((item) => {
          const active = pathname?.startsWith(item.href) ?? false;
          return (
            <li key={item.href} className="flex-1">
              <Link
                href={item.href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'relative flex flex-col items-center gap-0.5 py-2.5 text-[11px] transition-colors',
                  active ? 'text-accent' : 'text-ink-subtle hover:text-ink-muted',
                )}
              >
                <span className="relative text-lg leading-none" aria-hidden>
                  {item.icon}
                  {item.badge ? <CountBadge count={item.badge} /> : null}
                </span>
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
      {/* Keeps the bar clear of the iOS home indicator. */}
      <div className="h-[env(safe-area-inset-bottom)]" />
    </nav>
  );
}
