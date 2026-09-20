'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils/cn';
import { Avatar } from '@/components/ui/Avatar';
import { useAuthStore } from '@/lib/stores/authStore';
import { useMyProfile } from '@/lib/hooks/useProfile';
import { useUnseenLikeCount } from '@/lib/hooks/useLikes';
import { useUnreadCount } from '@/lib/hooks/useChat';

interface NavItem {
  href: string;
  label: string;
  icon: string;
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
    { href: '/home', label: 'Discover', icon: '◎' },
    { href: '/standouts', label: 'Standouts', icon: '✦' },
    { href: '/likes', label: 'Likes', icon: '♡', badge: unseenLikes },
    { href: '/matches', label: 'Matches', icon: '◈' },
    { href: '/messages', label: 'Messages', icon: '✉', badge: unread },
    { href: '/profile', label: 'Profile', icon: '☺' },
    { href: '/settings', label: 'Settings', icon: '⚙' },
  ];

  const avatar = profile?.photos.find((photo) => photo.primaryPhoto)?.url ?? profile?.photos[0]?.url;

  return (
    <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-border bg-surface px-4 py-6 md:flex">
      <Link href="/home" className="mb-8 px-2 text-lg font-semibold tracking-tight text-ink">
        Two <span className="text-accent">&amp;</span> Two
      </Link>

      <nav aria-label="Main" className="flex-1">
        <ul className="space-y-1">
          {items.map((item) => {
            const active = pathname?.startsWith(item.href) ?? false;
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm transition-colors',
                    active
                      ? 'bg-accent-soft font-medium text-accent'
                      : 'text-ink-muted hover:bg-surface-muted hover:text-ink',
                  )}
                >
                  <span aria-hidden className="text-base">{item.icon}</span>
                  <span className="flex-1">{item.label}</span>
                  {item.badge ? (
                    <span className="rounded-full bg-accent px-1.5 py-0.5 text-[10px] font-semibold text-accent-ink">
                      {item.badge > 99 ? '99+' : item.badge}
                    </span>
                  ) : null}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <Link
        href="/profile"
        className="mt-4 flex items-center gap-3 rounded-2xl p-2 transition-colors hover:bg-surface-muted"
      >
        <Avatar src={avatar} name={account?.displayName} size={38} />
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-ink">{account?.displayName}</p>
          <p className="truncate text-xs text-ink-subtle">
            {account?.tier === 'FREE' ? 'Free plan' : account?.tier === 'PLUS' ? 'Plus' : 'Premium'}
          </p>
        </div>
      </Link>
    </aside>
  );
}
