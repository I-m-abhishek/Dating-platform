'use client';

import Link from 'next/link';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { PhotoCarousel } from '@/components/profile/PhotoCarousel';
import { ProfileDetails } from '@/components/profile/ProfileDetails';
import { Badge } from '@/components/ui/Badge';
import { LinkButton } from '@/components/ui/LinkButton';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { ErrorState } from '@/components/ui/ErrorState';
import { useMyProfile } from '@/lib/hooks/useProfile';
import { useAuthStore } from '@/lib/stores/authStore';
import { messageOf } from '@/lib/api/errors';

/**
 * Your own profile, shown the way others see it.
 *
 * <p>The completeness meter is the first thing on the page. It is the single strongest
 * predictor of whether anyone matches with you, and it also gates the auto-match engine, so
 * burying it would be a disservice.
 */
function MyProfilePage() {
  const query = useMyProfile();
  const account = useAuthStore((state) => state.account);

  if (query.isPending) return <FullPageLoader />;
  if (query.isError) {
    return <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />;
  }

  const profile = query.data;
  if (!profile) return null;

  const percent = Math.round(profile.completeness * 100);

  return (
    <>
      <TopBar
        title="Your profile"
        action={
          <LinkButton href="/profile/edit" size="sm" variant="ghost">
            Edit
          </LinkButton>
        }
      />

      <div className="space-y-4 p-4">
        <section className="card space-y-2 p-4">
          <div className="flex items-baseline justify-between">
            <p className="text-sm font-medium text-ink">Profile strength</p>
            <span className="text-sm tabular-nums text-ink-muted">{percent}%</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-surface-muted">
            <div
              className="h-full rounded-full bg-accent transition-[width] duration-500"
              style={{ width: `${percent}%` }}
            />
          </div>
          {percent < 70 ? (
            <p className="text-xs text-ink-muted">
              Profiles with four photos and three prompts get noticeably more matches.{' '}
              <Link href="/profile/edit" className="text-accent underline-offset-2 hover:underline">
                Fill in the gaps
              </Link>
              .
            </p>
          ) : null}
        </section>

        <div className="flex flex-wrap gap-2">
          <Badge tone="muted">
            {account?.tier === 'FREE' ? 'Free plan' : account?.tier === 'PLUS' ? 'Plus' : 'Premium'}
          </Badge>
          {profile.photoVerified ? <Badge tone="success">Verified</Badge> : null}
          {profile.incognito ? <Badge tone="accent">Incognito</Badge> : null}
        </div>

        <PhotoCarousel photos={profile.photos} alt={profile.displayName} />

        <h2 className="text-lg font-semibold text-ink">
          {profile.displayName}, {profile.age}
        </h2>

        {profile.bio ? (
          <p className="text-[15px] leading-relaxed text-ink-muted">{profile.bio}</p>
        ) : null}

        {profile.prompts.map((prompt) => (
          <section key={prompt.id} className="card space-y-1.5 p-4">
            <p className="text-xs uppercase tracking-wide text-ink-subtle">{prompt.prompt}</p>
            <p className="text-[17px] leading-snug text-ink">{prompt.answer}</p>
          </section>
        ))}

        <div className="card p-4">
          <ProfileDetails
            jobTitle={profile.jobTitle}
            school={profile.school}
            hometown={profile.hometown}
            heightCm={profile.heightCm}
            religion={profile.religion}
            zodiacSign={profile.zodiacSign}
            relationshipIntent={profile.relationshipIntent}
            drinking={profile.drinking}
            smoking={profile.smoking}
            childrenPreference={profile.children}
            languages={profile.languages}
            interests={profile.interests}
            qualities={profile.qualities}
          />
        </div>

        <div className="flex gap-3 pb-4">
          <LinkButton href="/profile/edit" fullWidth>
            Edit profile
          </LinkButton>
          <LinkButton href="/settings" variant="outline" fullWidth>
            Settings
          </LinkButton>
        </div>
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(MyProfilePage);
