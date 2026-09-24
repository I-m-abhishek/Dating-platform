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
import { CrownIcon, LockIcon, ShieldCheckIcon } from '@/components/ui/icons';
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

  if (query.isPending) return <FullPageLoader label="Loading your profile" />;
  if (query.isError) {
    return <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />;
  }

  const profile = query.data;
  if (!profile) return null;

  const percent = Math.round(profile.completeness * 100);
  const strong = percent >= 70;

  return (
    <>
      <TopBar
        title="Your profile"
        action={
          <LinkButton href="/profile/edit" size="sm" variant="outline">
            Edit
          </LinkButton>
        }
      />

      <div className="space-y-5 p-4">
        <section className="card space-y-3 p-5">
          <div className="flex items-baseline justify-between">
            <p className="text-sm font-semibold text-ink">Profile strength</p>
            <span className="font-display text-[22px] font-semibold tabular-nums text-gradient">
              {percent}%
            </span>
          </div>

          <div className="h-2 overflow-hidden rounded-full bg-surface-muted">
            <div
              className="h-full rounded-full bg-accent-gradient transition-[width] duration-700 ease-snap"
              style={{ width: `${percent}%` }}
            />
          </div>

          {!strong ? (
            <p className="text-[13px] leading-relaxed text-ink-muted">
              Profiles with four photos and three prompts get noticeably more matches.{' '}
              <Link
                href="/profile/edit"
                className="font-semibold text-accent underline-offset-2 hover:underline"
              >
                Fill in the gaps
              </Link>
              .
            </p>
          ) : (
            <p className="text-[13px] text-ink-muted">
              Looking good. This is the version other people see.
            </p>
          )}
        </section>

        <div className="flex flex-wrap gap-2">
          <Badge tone={account?.tier === 'FREE' ? 'muted' : 'gold'}>
            {account?.tier !== 'FREE' ? <CrownIcon size={12} /> : null}
            {account?.tier === 'FREE' ? 'Free plan' : account?.tier === 'PLUS' ? 'Plus' : 'Premium'}
          </Badge>
          {profile.photoVerified ? (
            <Badge tone="success">
              <ShieldCheckIcon size={12} />
              Verified
            </Badge>
          ) : null}
          {profile.incognito ? (
            <Badge tone="accent">
              <LockIcon size={12} />
              Incognito
            </Badge>
          ) : null}
        </div>

        <PhotoCarousel
          photos={profile.photos}
          alt={profile.displayName}
          aspect="tall"
          className="rounded-card shadow-card"
          overlay={
            <h2 className="font-display text-[30px] font-semibold leading-none tracking-[-0.02em] text-white">
              {profile.displayName}
              <span className="font-sans text-[22px] font-medium text-white/75">
                {' '}
                {profile.age}
              </span>
            </h2>
          }
        />

        {profile.bio ? (
          <p className="px-1 text-[15px] leading-relaxed text-ink-muted">{profile.bio}</p>
        ) : null}

        {profile.prompts.map((prompt) => (
          <section key={prompt.id} className="card space-y-2 p-5">
            <p className="eyebrow">{prompt.prompt}</p>
            <p className="font-display text-[20px] leading-[1.35] text-ink">{prompt.answer}</p>
          </section>
        ))}

        <div className="card p-5">
          <ProfileDetails
            jobTitle={profile.jobTitle}
            school={profile.school}
            hometown={profile.hometown}
            heightCm={profile.heightCm}
            religion={profile.religion}
            zodiacSign={profile.zodiacSign}
            interestedIn={account?.interestedIn}
            relationshipIntent={profile.relationshipIntent}
            drinking={profile.drinking}
            smoking={profile.smoking}
            childrenPreference={profile.children}
            languages={profile.languages}
            interests={profile.interests}
            qualities={profile.qualities}
          />
        </div>

        <div className="flex gap-3 pb-2">
          <LinkButton href="/profile/edit" size="lg" fullWidth>
            Edit profile
          </LinkButton>
          <LinkButton href="/settings" size="lg" variant="outline" fullWidth>
            Settings
          </LinkButton>
        </div>
      </div>
    </>
  );
}

export default compose(withErrorBoundary, withAuth)(MyProfilePage);
