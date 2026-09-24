'use client';

import { useState } from 'react';
import Image from 'next/image';
import { useParams, useRouter } from 'next/navigation';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { ProfileDetails } from '@/components/profile/ProfileDetails';
import { PhotoCommentSheet } from '@/components/profile/PhotoCommentSheet';
import { ProfileActionsSheet } from '@/components/profile/ProfileActionsSheet';
import { LikeableItem } from '@/components/likes/LikeableItem';
import { Button } from '@/components/ui/Button';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { ErrorState } from '@/components/ui/ErrorState';
import {
  ArrowLeftIcon,
  ChatIcon,
  CheckIcon,
  CloseIcon,
  CommentIcon,
  HeartIcon,
  SparkleIcon,
  StarIcon,
} from '@/components/ui/icons';
import { usePublicProfile } from '@/lib/hooks/useProfile';
import { usePaywall } from '@/lib/hooks/usePaywall';
import { useQueryClient } from '@tanstack/react-query';
import { likeApi } from '@/lib/api/endpoints';
import { queryKeys } from '@/lib/api/queryKeys';
import { useUiStore } from '@/lib/stores/uiStore';
import { compatibilityLabel, distanceLabel } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';
import type { LikeIntent, Photo, PromptAnswer } from '@/lib/api/types';

/**
 * Somebody else's profile.
 *
 * <p>Photos and prompts are interleaved rather than stacked as a gallery followed by a wall
 * of text. That is the whole point of a prompt-led profile: you meet a face, then a
 * sentence, then another face - which is how someone actually decides.
 *
 * <p>Every photo and prompt can be liked on its own, with a comment written in a box right
 * underneath it; "Super send" makes that like a priority one.
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
  const [openItem, setOpenItem] = useState<string | null>(null);
  const queryClient = useQueryClient();

  if (query.isPending) return <FullPageLoader label="Loading profile" />;
  if (query.isError) {
    return <ErrorState description={messageOf(query.error)} onRetry={() => void query.refetch()} />;
  }

  const profile = query.data;
  if (!profile) return null;

  // Falls back to "no relationship" against an older server - see PublicProfile.relationship.
  const relationship = profile.relationship ?? {
    matched: false,
    likeSent: false,
  };

  const [heroPhoto, ...morePhotos] = profile.photos;
  const compatibility = compatibilityLabel(profile.compatibilityScore);
  const distance = distanceLabel(profile.distanceKm);

  const like = async (intent: LikeIntent = {}): Promise<boolean> => {
    setBusy(true);
    try {
      const result = await likeApi.like({
        targetUserId: profile.userId,
        type: intent.superLike ? 'SUPER' : 'STANDARD',
        note: intent.note,
        targetPhotoId: intent.targetPhotoId,
        targetPromptAnswerId: intent.targetPromptAnswerId,
      });
      toast({
        title: result.matched
          ? 'It is a match'
          : intent.superLike
            ? 'Super like sent'
            : intent.note
              ? 'Like sent with your comment'
              : 'Like sent',
        description: intent.superLike && !result.matched ? 'You are at the top of their likes.' : undefined,
        tone: 'success',
      });
      setOpenItem(null);
      void queryClient.invalidateQueries({ queryKey: queryKeys.likes.quota() });
      void query.refetch();
      if (result.matched && result.match?.conversationId) {
        router.push(`/messages/${result.match.conversationId}`);
      }
      return true;
    } catch (error) {
      handleError(error);
      return false;
    } finally {
      setBusy(false);
    }
  };

  const canLike = !relationship.matched && !relationship.likeSent;
  const toggle = (key: string) => setOpenItem((current) => (current === key ? null : key));

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
    <div className="pb-44 md:pb-28">
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
            unoptimized
          />
        ) : null}

        {/*
          Floating controls over the hero rather than a solid bar - the photo is the header.
          Absolute within the hero, so they scroll away with it. They used to be sticky to
          the whole page, which left two dark pucks hovering over the profile text all the
          way down. Sticky is not an option here either way: the hero clips its children, and
          `overflow: hidden` makes it a scrollport a sticky child has nothing to stick to.
        */}
        <div className="pointer-events-none absolute inset-x-0 top-0 z-20 flex items-start justify-between px-3 pt-3">
          <button
            type="button"
            onClick={() => router.back()}
            aria-label="Go back"
            className="glass-dark pointer-events-auto flex h-11 w-11 items-center justify-center rounded-full text-white ring-1 ring-inset ring-white/20 transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
          >
            <ArrowLeftIcon size={19} />
          </button>
          <button
            type="button"
            onClick={() => setActionsOpen(true)}
            aria-label="More options"
            className="glass-dark pointer-events-auto flex h-11 w-11 items-center justify-center rounded-full text-xl leading-none text-white ring-1 ring-inset ring-white/20 transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
          >
            <span aria-hidden>···</span>
          </button>
        </div>

        <div className="absolute inset-x-0 bottom-0 z-10 flex items-end justify-between gap-4 bg-photo-scrim px-5 pb-6 pt-24">
          <div className="min-w-0 flex-1">
            {/*
              Solid pills, not the tinted Badge component: a 10%-opacity background over a
              photograph is unreadable, and these sit on whatever the user uploaded.
            */}
            <div className="flex flex-wrap items-center gap-2">
              {relationship.matched ? (
                <span className="inline-flex items-center gap-1 rounded-pill bg-success px-2.5 py-1 text-[11px] font-semibold text-white">
                  <CheckIcon size={12} strokeWidth={2.6} />
                  Matched
                </span>
              ) : null}
              {profile.photoVerified ? (
                <span className="inline-flex items-center gap-1 rounded-pill bg-white px-2.5 py-1 text-[11px] font-semibold text-ink">
                  <CheckIcon size={12} strokeWidth={2.6} />
                  Verified
                </span>
              ) : null}
              {profile.recentlyActive ? (
                <span className="glass-dark inline-flex items-center gap-1.5 rounded-pill px-2.5 py-1 text-[11px] font-semibold text-white ring-1 ring-inset ring-white/20">
                  <span className="h-1.5 w-1.5 rounded-full bg-success" />
                  Active recently
                </span>
              ) : null}
            </div>

            <h1 className="mt-3 font-display text-[36px] font-semibold leading-none tracking-[-0.025em] text-white">
              {profile.displayName}
              <span className="font-sans text-[25px] font-medium text-white/75">
                {' '}
                {profile.age}
              </span>
            </h1>

            <p className="mt-2 text-[13px] font-medium text-white/75">
              {[profile.jobTitle, profile.city, distance].filter(Boolean).join(' · ')}
            </p>
          </div>

          {/*
              Bottom right, beside the name rather than floating over the middle of the
              photo. It is a flex sibling of the name block, so a long job title shortens
              itself instead of sliding under the button.
            */}
          {heroPhoto ? (
            <button
              type="button"
              onClick={() => setCommentPhoto(heroPhoto)}
              className="glass-dark mb-1 inline-flex shrink-0 items-center gap-1.5 rounded-pill px-3.5 py-2 text-xs font-semibold text-white ring-1 ring-inset ring-white/20 transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
            >
              <CommentIcon size={15} />
              {heroPhoto.commentCount > 0 ? heroPhoto.commentCount : 'Comment'}
            </button>
          ) : null}
        </div>
      </section>

      <div className="space-y-3 p-4">
        {/* ---- why you two ---- */}
        {compatibility || profile.sharedInterests.length > 0 ? (
          <section className="hairline-gradient relative overflow-hidden rounded-card bg-accent-gradient-soft p-5">
            <div className="flex items-start gap-3.5">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl2 bg-accent-gradient text-white shadow-glow">
                <SparkleIcon size={19} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-display text-[17px] font-semibold text-ink">
                  {compatibility ?? 'Worth a look'}
                </p>
                {profile.sharedInterests.length > 0 ? (
                  <p className="mt-0.5 text-[13px] leading-snug text-ink-muted">
                    You both like {profile.sharedInterests.slice(0, 3).join(', ')}
                  </p>
                ) : null}
              </div>
            </div>
          </section>
        ) : null}

        {heroPhoto ? (
          <LikeableItem
            name={profile.displayName}
            target={{ kind: 'photo', photo: heroPhoto }}
            open={openItem === `photo-${heroPhoto.id}`}
            onToggle={() => toggle(`photo-${heroPhoto.id}`)}
            onSend={like}
            enabled={canLike}
          >
            {null}
          </LikeableItem>
        ) : null}

        {profile.bio ? (
          <p className="px-1 text-[15px] leading-relaxed text-ink-muted">{profile.bio}</p>
        ) : null}

        {/* ---- photos and prompts, alternating ---- */}
        {tail.map((block) => (
          <LikeableItem
            key={`${block.kind}-${block.value.id}`}
            name={profile.displayName}
            target={
              block.kind === 'prompt'
                ? { kind: 'prompt', prompt: block.value }
                : { kind: 'photo', photo: block.value }
            }
            open={openItem === `${block.kind}-${block.value.id}`}
            onToggle={() => toggle(`${block.kind}-${block.value.id}`)}
            onSend={like}
            enabled={canLike}
          >
            {block.kind === 'prompt' ? (
              <section className="card space-y-2 p-5">
                <p className="eyebrow">{block.value.prompt}</p>
                <p className="font-display text-[20px] leading-[1.35] text-ink">
                  {block.value.answer}
                </p>
              </section>
            ) : (
              <section className="relative aspect-[4/5] overflow-hidden rounded-card bg-surface-muted shadow-card">
                {/*
                    unoptimized, matching every other photo in the app. Routing user uploads
                    through /_next/image makes the Next process re-fetch and re-encode each
                    one server side; this was the only screen still doing it, and the only
                    screen whose photos failed to appear.
                  */}
                <Image
                  src={block.value.url}
                  alt={profile.displayName}
                  fill
                  sizes="(max-width: 768px) 100vw, 640px"
                  className="object-cover"
                  unoptimized
                />
                {block.value.caption ? (
                  /* Right padding keeps the caption clear of the comment button below it. */
                  <p className="absolute inset-x-0 bottom-0 bg-photo-scrim py-5 pl-5 pr-32 text-sm font-medium text-white/90">
                    {block.value.caption}
                  </p>
                ) : null}
                <button
                  type="button"
                  onClick={() => setCommentPhoto(block.value)}
                  className="glass-dark absolute bottom-3 right-3 inline-flex items-center gap-1.5 rounded-pill px-3.5 py-2 text-xs font-semibold text-white ring-1 ring-inset ring-white/20 transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
                >
                  <CommentIcon size={15} />
                  {block.value.commentCount > 0 ? block.value.commentCount : 'Comment'}
                </button>
              </section>
            )}
          </LikeableItem>
        ))}

        <section className="card p-5">
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

      {/*
        The action depends on where the two of you already stand, and the server decides
        that - see PublicProfileResponse.relationship. Offering "Like" to someone you
        matched with last week is the kind of thing users notice immediately.
      */}
      {/*
        Sits above the floating bottom nav, which is about 5.5rem tall at z-40 and would
        otherwise cover this bar. The calc keeps it correct on notched devices, where the
        nav grows by the safe-area inset.

        On md+ the bottom nav is replaced by the side nav, so the bar drops to the edge -
        and has to start after the sidebar. Spanning the full window put its buttons 140px
        to the left of the profile they act on, and laid its background over the sidebar.
      */}
      <div className="glass fixed inset-x-0 bottom-[calc(5.5rem+env(safe-area-inset-bottom))] z-30 border-t border-border p-4 md:bottom-0 md:left-[17.5rem]">
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
              leftIcon={<ChatIcon size={18} />}
            >
              {/*
                A flex child will not shrink below its content unless told to, so a long
                display name pushed its own label out of the button on a narrow phone.
              */}
              <span className="min-w-0 truncate">Message {profile.displayName}</span>
            </Button>
          ) : relationship.likeSent ? (
            <Button size="lg" fullWidth variant="secondary" disabled>
              Liked - waiting for them
            </Button>
          ) : (
            <>
              <Button
                variant="outline"
                size="lg"
                fullWidth
                disabled={busy}
                onClick={() => void pass()}
                leftIcon={<CloseIcon size={18} />}
              >
                Pass
              </Button>
              <Button
                variant="secondary"
                size="icon"
                disabled={busy}
                onClick={() => void like({ superLike: true })}
                aria-label={`Super like ${profile.displayName}`}
                title="Super like - puts you at the top of their likes"
                className="h-[52px] w-[52px] bg-gradient-to-br from-sky-500 to-indigo-500 text-white"
              >
                <StarIcon size={20} filled />
              </Button>
              <Button
                size="lg"
                fullWidth
                loading={busy}
                onClick={() => void like()}
                leftIcon={<HeartIcon size={18} />}
              >
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
