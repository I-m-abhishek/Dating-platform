'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ComponentType } from 'react';
import { cn } from '@/lib/utils/cn';
import { Avatar } from '@/components/ui/Avatar';
import {
  ChatIcon,
  CompassIcon,
  CrownIcon,
  HeartIcon,
  LogoMark,
  MatchIcon,
  SettingsIcon,
  SparkleIcon,
  UserIcon,
  type IconProps,
} from '@/components/ui/icons';
import { useAuthStore } from '@/lib/stores/authStore';
import { useMyProfile } from '@/lib/hooks/useProfile';
import { useUnseenLikeCount } from '@/lib/hooks/useLikes';
import { useUnreadCount } from '@/lib/hooks/useChat';

interface NavItem {
  href: string;
  label: string;
  Icon: ComponentType<IconProps>;
  badge?: number;
}

/** The desktop counterpart of {@link BottomNav}. Same destinations, more room. */
export function SideNav() {
  const pathname = usePathname();
  const account = useAuthStore((state) => state.account);
  const { data: profile } = useMyProfile();
  const { data: unseenLikes = 0 } = useUnseenLikeCount();
  const { data: unread = 0 } = useUnreadCount();

  const items: NavItem[] = [
    { href: '/home', label: 'Discover', Icon: CompassIcon },
    { href: '/standouts', label: 'Standouts', Icon: SparkleIcon },
    { href: '/likes', label: 'Likes', Icon: HeartIcon, badge: unseenLikes },
    { href: '/matches', label: 'Matches', Icon: MatchIcon },
    { href: '/messages', label: 'Messages', Icon: ChatIcon, badge: unread },
    { href: '/profile', label: 'Profile', Icon: UserIcon },
    { href: '/settings', label: 'Settings', Icon: SettingsIcon },
  ];

  const avatar = profile?.photos.find((photo) => photo.primaryPhoto)?.url ?? profile?.photos[0]?.url;
  const tierLabel =
    account?.tier === 'FREE' ? 'Free plan' : account?.tier === 'PLUS' ? 'Plus' : 'Premium';

  return (
    <aside className="sticky top-0 z-30 hidden h-screen w-[17.5rem] shrink-0 flex-col border-r border-border bg-surface/60 px-4 py-6 backdrop-blur-xl md:flex">
      <Link
        href="/home"
        className="mb-8 flex items-center gap-2.5 px-2 transition-opacity hover:opacity-80"
      >
        <LogoMark size={30} />
        <span className="font-display text-[21px] font-semibold tracking-[-0.02em] text-ink">
          Two <span className="text-gradient">&amp;</span> Two
        </span>
      </Link>

      <nav aria-label="Main" className="flex-1">
        <ul className="space-y-1">
          {items.map((item) => {
            const active = pathname?.startsWith(item.href) ?? false;
            const { Icon } = item;

            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'group relative flex items-center gap-3 rounded-2xl px-3 py-2.5 text-[14px] font-semibold',
                    'transition-[background-color,color,transform] duration-300 ease-snap',
                    active
                      ? 'bg-accent-soft text-accent'
                      : 'text-ink-muted hover:translate-x-0.5 hover:bg-surface-muted hover:text-ink',
                  )}
                >
                  {/* The lit edge on the active tab. Absolute, so it never shifts the label. */}
                  {active ? (
                    <span
                      aria-hidden
                      className="absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-r-full bg-accent-gradient"
                    />
                  ) : null}

                  <Icon size={20} filled={active && item.href === '/likes'} />
                  <span className="flex-1">{item.label}</span>

                  {item.badge ? (
                    <span className="rounded-full bg-accent-gradient px-1.5 py-0.5 text-[10px] font-bold text-white">
                      {item.badge > 99 ? '99+' : item.badge}
                    </span>
                  ) : null}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/*
        The upsell only exists for accounts that can act on it. Showing "Go Premium" to
        someone who already pays is the fastest way to make a paid product feel cheap.
      */}
      {account?.tier === 'FREE' ? (
        <Link
          href="/plans"
          className="hairline-gradient group mt-4 block overflow-hidden rounded-2xl bg-accent-gradient-soft p-4 transition-transform duration-300 ease-snap hover:-translate-y-0.5"
        >
          <span className="flex items-center gap-2 text-[13px] font-bold text-ink">
            <CrownIcon size={17} className="text-accent" />
            Go Premium
          </span>
          <span className="mt-1 block text-xs leading-snug text-ink-muted">
            See everyone who likes you, and get a new match every day.
          </span>
        </Link>
      ) : null}

      <Link
        href="/profile"
        className="mt-3 flex items-center gap-3 rounded-2xl p-2 transition-colors hover:bg-surface-muted"
      >
        <Avatar src={avatar} name={account?.displayName} size={38} />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-ink">{account?.displayName}</p>
          <p className="truncate text-xs font-medium text-ink-subtle">{tierLabel}</p>
        </div>
      </Link>
    </aside>
  );
}
