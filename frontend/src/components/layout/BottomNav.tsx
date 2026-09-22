'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ComponentType } from 'react';
import { cn } from '@/lib/utils/cn';
import { CountBadge } from '@/components/ui/Badge';
import {
  ChatIcon,
  CompassIcon,
  HeartIcon,
  MatchIcon,
  SparkleIcon,
  type IconProps,
} from '@/components/ui/icons';
import { useUnseenLikeCount } from '@/lib/hooks/useLikes';
import { useUnreadCount } from '@/lib/hooks/useChat';
import { useMatchCount } from '@/lib/hooks/useMatches';

interface NavItem {
  href: string;
  label: string;
  Icon: ComponentType<IconProps>;
  badge?: number;
}

/**
 * The five destinations. Deliberately five - a dating app with more tabs than that has an
 * information architecture problem, not a navigation one.
 *
 * <p>The bar floats clear of the page edge rather than being welded to it. That reads as a
 * control sitting above the content, and it leaves the last row of a list visible through
 * the blur instead of clipping it.
 */
export function BottomNav() {
  const pathname = usePathname();
  const { data: unseenLikes = 0 } = useUnseenLikeCount();
  const { data: unread = 0 } = useUnreadCount();
  const { data: matches = 0 } = useMatchCount();

  const items: NavItem[] = [
    { href: '/home', label: 'Discover', Icon: CompassIcon },
    { href: '/standouts', label: 'Standouts', Icon: SparkleIcon },
    { href: '/likes', label: 'Likes', Icon: HeartIcon, badge: unseenLikes },
    { href: '/matches', label: 'Matches', Icon: MatchIcon, badge: matches },
    { href: '/messages', label: 'Chats', Icon: ChatIcon, badge: unread },
  ];

  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-40 px-3 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] pt-2 md:hidden"
    >
      <ul className="glass mx-auto flex max-w-md items-stretch gap-0.5 rounded-[1.75rem] border border-border p-1.5 shadow-float">
        {items.map((item) => {
          const active = pathname?.startsWith(item.href) ?? false;
          const { Icon } = item;

          return (
            <li key={item.href} className="flex-1">
              <Link
                href={item.href}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'relative flex flex-col items-center gap-1 rounded-[1.35rem] py-2 text-[10px] font-semibold tracking-[0.01em]',
                  'transition-[color,background-color,transform] duration-300 ease-snap active:scale-95',
                  active
                    ? 'bg-accent-soft text-accent'
                    : 'text-ink-subtle hover:bg-surface-muted hover:text-ink-muted',
                )}
              >
                <span className="relative">
                  <Icon size={21} filled={active && item.href === '/likes'} />
                  {item.badge ? <CountBadge count={item.badge} /> : null}
                </span>
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
