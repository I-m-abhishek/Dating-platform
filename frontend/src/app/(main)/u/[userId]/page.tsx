'use client';

import { useState } from 'react';
import Image from 'next/image';
import { useParams, useRouter } from 'next/navigation';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { ProfileDetails } from '@/components/profile/ProfileDetails';
import { PhotoCommentSheet } from '@/components/profile/PhotoCommentSheet';
import { ProfileActionsSheet } from '@/components/profile/ProfileActionsSheet';
import { Button } from '@/components/ui/Button';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { ErrorState } from '@/components/ui/ErrorState';
import { usePublicProfile } from '@/lib/hooks/useProfile';
import { usePaywall } from '@/lib/hooks/usePaywall';
import { likeApi } from '@/lib/api/endpoints';
import { useUiStore } from '@/lib/stores/uiStore';
import { compatibilityLabel, distanceLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';
import type { Photo, PromptAnswer } from '@/lib/api/types';

/**
 * Somebody else's profile.
 *
 * <p>Photos and prompts are interleaved rather than stacked as a gallery followed by a wall
 * of text. That is the whole point of a prompt-led profile: you meet a face, then a
 * sentence, then another face - which is how someone actually decides.
 */
function PublicProfilePage() {
  const params = useParams<{ userId: string }>();
  const router = useRouter();
  const query = usePublicProfile(params.userId);
  const { handleError } = usePaywall();
  const toast = useUiStore((state) => state.toast);

  const [commentPhoto, setCommentPhoto] = useState<Photo | null>(null);
  const [actionsOpen, setActionsOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  if (query.isPending) return <FullPageLoader />;
  if (query.isError) {
    return <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />;
  }

  const profile = query.data;
  if (!profile) return null;

  // Falls back to "no relationship" against an older server - see PublicProfile.relationship.
  const relationship = profile.relationship ?? { matched: false, likeSent: false };

  const [heroPhoto, ...morePhotos] = profile.photos;
  const compatibility = compatibilityLabel(profile.compatibilityScore);
  const distance = distanceLabel(profile.distanceKm);

  const like = async () => {
    setBusy(true);
    try {
      const result = await likeApi.like({ targetUserId: profile.userId });
      toast({ title: result.matched ? 'It is a match' : 'Like sent', tone: 'success' });
      if (result.matched && result.match?.conversationId) {
        router.push(`/messages/${result.match.conversationId}`);
      }
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  };

  const pass = async () => {
    setBusy(true);
    try {
      await likeApi.pass(profile.userId);
      router.back();
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  };

  // Alternate the remaining photos with the remaining prompts so neither clumps together.
  const tail: Array<{ kind: 'photo'; value: Photo } | { kind: 'prompt'; value: PromptAnswer }> = [];
  const maxTail = Math.max(morePhotos.length, profile.prompts.length);
  for (let i = 0; i < maxTail; i++) {
    const prompt = profile.prompts[i];
    if (prompt) tail.push({ kind: 'prompt', value: prompt });
    const photo = morePhotos[i];
    if (photo) tail.push({ kind: 'photo', value: photo });
  }

  return (
    <div className="pb-36 md:pb-28">
      {/* Floating controls over the hero rather than a solid bar - the photo is the header. */}
      <div className="pointer-events-none sticky top-0 z-30 flex items-start justify-between p-3">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="Go back"
          className="pointer-events-auto flex h-10 w-10 items-center justify-center rounded-full bg-black/40 text-lg text-white backdrop-blur-md transition-colors hover:bg-black/55"
        >
          ←
        </button>
        <button
          type="button"
          onClick={() => setActionsOpen(true)}
          aria-label="More options"
          className="pointer-events-auto flex h-10 w-10 items-center justify-center rounded-full bg-black/40 text-lg text-white backdrop-blur-md transition-colors hover:bg-black/55"
        >
          ⋯
        </button>
      </div>

      <div className="-mt-16">
        {/* ---- hero ---- */}
        <section className="relative aspect-[4/5] w-full overflow-hidden bg-surface-muted">
          {heroPhoto ? (
            <Image
              src={heroPhoto.url}
              alt={profile.displayName}
              fill
              sizes="(max-width: 768px) 100vw, 640px"
              className="object-cover"
              priority
            />
          ) : null}

          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 via-black/25 to-transparent px-5 pb-5 pt-20">
            {/*
              Solid pills, not the tinted Badge component: a 10%-opacity background over a
              photograph is unreadable, and these sit on whatever the user uploaded.
            */}
            <div className="flex flex-wrap items-center gap-2">
              {relationship.matched ? (
                <span className="rounded-pill bg-success px-2.5 py-1 text-xs font-medium text-white">
                  Matched
                </span>
              ) : null}
              {profile.photoVerified ? (
                <span className="rounded-pill bg-white px-2.5 py-1 text-xs font-medium text-ink">
                  ✓ Verified
                </span>
              ) : null}
              {profile.recentlyActive ? (
                <span className="inline-flex items-center gap-1.5 rounded-pill bg-black/45 px-2.5 py-1 text-xs text-white backdrop-blur-sm">
                  <span className="h-1.5 w-1.5 rounded-full bg-success" />
                  Active recently
                </span>
              ) : null}
            </div>

            <h1 className="mt-2 text-3xl font-semibold tracking-tight text-white">
              {profile.displayName} <span className="font-normal opacity-90">{profile.age}</span>
            </h1>

            <p className="mt-1 text-sm text-white/80">
              {[profile.jobTitle, profile.city, distance].filter(Boolean).join(' · ')}
            </p>
          </div>

          {heroPhoto ? (
            <button
              type="button"
              onClick={() => setCommentPhoto(heroPhoto)}
              className="absolute right-4 top-20 rounded-pill bg-black/45 px-3 py-1.5 text-xs text-white backdrop-blur-sm"
            >
              💬 {heroPhoto.commentCount > 0 ? heroPhoto.commentCount : 'Comment'}
            </button>
          ) : null}
        </section>

        <div className="space-y-3 p-4">
          {/* ---- why you two ---- */}
          {compatibility || profile.sharedInterests.length > 0 ? (
            <section className="rounded-card bg-accent-soft p-4">
              <div className="flex items-baseline justify-between gap-3">
                <p className="text-sm font-semibold text-accent">
                  {compatibility ?? 'Worth a look'}
                </p>
                {profile.relationship?.matchedAt ? (
                  <span className="text-xs text-accent/70">Matched</span>
                ) : null}
              </div>
              {profile.sharedInterests.length > 0 ? (
                <p className="mt-1 text-sm text-ink-muted">
                  You both like {profile.sharedInterests.slice(0, 3).join(', ')}
                </p>
              ) : null}
            </section>
          ) : null}

          {profile.bio ? (
            <p className="px-1 text-[15px] leading-relaxed text-ink-muted">{profile.bio}</p>
          ) : null}

          {/* ---- photos and prompts, alternating ---- */}
          {tail.map((block) =>
            block.kind === 'prompt' ? (
              <section key={block.value.id} className="card space-y-1.5 p-4">
                <p className="text-xs uppercase tracking-wide text-ink-subtle">
                  {block.value.prompt}
                </p>
                <p className="text-[17px] leading-snug text-ink">{block.value.answer}</p>
              </section>
            ) : (
              <section
                key={block.value.id}
                className="relative aspect-[4/5] overflow-hidden rounded-card bg-surface-muted"
              >
                <Image
                  src={block.value.url}
                  alt={profile.displayName}
                  fill
                  sizes="(max-width: 768px) 100vw, 640px"
                  className="object-cover"
                />
                {block.value.caption ? (
                  <p className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-4 pb-4 pt-10 text-sm text-white">
                    {block.value.caption}
                  </p>
                ) : null}
                <button
                  type="button"
                  onClick={() => setCommentPhoto(block.value)}
                  className="absolute bottom-3 right-3 rounded-pill bg-black/45 px-3 py-1.5 text-xs text-white backdrop-blur-sm"
                >
                  💬 {block.value.commentCount > 0 ? block.value.commentCount : 'Comment'}
                </button>
              </section>
            ),
          )}

          <section className="card p-4">
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
              sharedInterests={profile.sharedInterests}
            />
          </section>
        </div>
      </div>

      {/*
        The action depends on where the two of you already stand, and the server decides
        that - see PublicProfileResponse.relationship. Offering "Like" to someone you
        matched with last week is the kind of thing users notice immediately.
      */}
      {/*
        Sits above the bottom nav, which is 57px tall at z-40 and would otherwise cover
        most of this bar. The calc keeps it correct on notched devices, where the nav grows
        by the safe-area inset. On md+ there is no bottom nav, so it returns to the edge.
      */}
      <div className="fixed inset-x-0 bottom-[calc(3.5rem+env(safe-area-inset-bottom))] z-30 border-t border-border bg-surface/95 p-4 backdrop-blur-md md:bottom-0">
        <div className="mx-auto flex max-w-2xl gap-3">
          {relationship.matched ? (
            <Button
              size="lg"
              fullWidth
              onClick={() =>
                router.push(
                  relationship.conversationId
                    ? `/messages/${relationship.conversationId}`
                    : '/matches',
                )
              }
            >
              Message {profile.displayName}
            </Button>
          ) : relationship.likeSent ? (
            <Button size="lg" fullWidth variant="secondary" disabled>
              Liked - waiting for them
            </Button>
          ) : (
            <>
              <Button variant="outline" size="lg" fullWidth disabled={busy} onClick={() => void pass()}>
                Pass
              </Button>
              <Button size="lg" fullWidth loading={busy} onClick={() => void like()}>
                Like
              </Button>
            </>
          )}
        </div>
      </div>

      <PhotoCommentSheet photo={commentPhoto} onClose={() => setCommentPhoto(null)} />

      <ProfileActionsSheet
        open={actionsOpen}
        onClose={() => setActionsOpen(false)}
        userId={profile.userId}
        displayName={profile.displayName}
        matchId={relationship.matchId}
      />
    </div>
  );
}

export default compose(withErrorBoundary, withAuth)(PublicProfilePage);
