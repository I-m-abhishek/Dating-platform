'use client';

import Image from 'next/image';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type PointerEvent,
} from 'react';
import { ProfileDetails } from '@/components/profile/ProfileDetails';
import { LikeTargetSheet, type LikeTarget } from '@/components/likes/LikeTargetSheet';
import { LikeableItem } from '@/components/likes/LikeableItem';
import { Button } from '@/components/ui/Button';
import { Sheet } from '@/components/ui/Sheet';
import {
  CloseIcon,
  CommentIcon,
  HeartIcon,
  ImageIcon,
  InfoIcon,
  MapPinIcon,
  ShieldCheckIcon,
  StarIcon,
} from '@/components/ui/icons';
import { useLikeQuota } from '@/lib/hooks/useLikes';
import { cn } from '@/lib/utils/cn';
import { compatibilityLabel, distanceLabel } from '@/lib/utils/format';
import type { FeedCard, LikeIntent, Photo, PromptAnswer } from '@/lib/api/types';

export interface SwipeDeckProps {
  cards: FeedCard[];
  /** A like: plain, super, or on one photo / prompt with a comment. */
  onLike: (card: FeedCard, intent: LikeIntent) => void;
  onPass: (card: FeedCard) => void;
}

/** Right is a like, up is a super like, left is a pass. */
type Direction = 'left' | 'right' | 'up';

/** How far a card must travel (px) before letting go counts as a swipe. */
const SWIPE_DISTANCE = 110;
/** ...or how fast (px/ms) a shorter flick must be moving. */
const SWIPE_VELOCITY = 0.55;
/** Below this, a press is a tap on the photo, not a drag. */
const TAP_SLOP = 6;
const EXIT_MS = 280;

/**
 * The Discover deck: one profile at a time, with the next ones stacked behind.
 *
 * <p>Everything needed to decide sits on the photo itself - name, age, work, distance, a
 * line of bio or a prompt, shared interests - so the decision takes one screen, not a
 * scroll through a whole profile. The full profile is one tap away (the info button).
 *
 * <p>Swipe right to like, up to super like, left to pass, or use the buttons; tap the left
 * or right side of the photo to step through photos. The comment button on the photo (and
 * the reply button on a prompt) opens that photo or prompt with a comment box beneath it,
 * so a like can say what caught your eye. Per-card UI state (current photo, drag, exit) is stored
 * with the card's id, so it resets by itself when the next card comes to the top.
 */
export function SwipeDeck({ cards, onLike, onPass }: SwipeDeckProps) {
  const top = cards[0];
  const topId = top?.userId;

  const [photo, setPhoto] = useState<{ id?: string; index: number }>({
    index: 0,
  });
  const [drag, setDrag] = useState<{
    id?: string;
    x: number;
    y: number;
    active: boolean;
  }>({
    x: 0,
    y: 0,
    active: false,
  });
  const [exit, setExit] = useState<{ id?: string; dir: Direction } | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [likeTarget, setLikeTarget] = useState<LikeTarget | null>(null);

  const photoIndex = photo.id === topId ? photo.index : 0;
  const dragX = drag.id === topId ? drag.x : 0;
  const dragY = drag.id === topId ? drag.y : 0;
  const dragging = drag.id === topId && drag.active;
  const exiting = exit && exit.id === topId ? exit.dir : null;

  const gesture = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    lastX: number;
    lastT: number;
    velocity: number;
    moved: boolean;
  } | null>(null);
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (exitTimer.current) clearTimeout(exitTimer.current);
    },
    [],
  );

  const fling = useCallback(
    (dir: Direction, intent?: LikeIntent) => {
      if (!top || exit?.id === top.userId) return;
      setDetailsOpen(false);
      setLikeTarget(null);
      setExit({ id: top.userId, dir });
      // Let the card fly off before it leaves the list, or it would just vanish.
      exitTimer.current = setTimeout(() => {
        if (dir === 'left') onPass(top);
        else onLike(top, intent ?? { superLike: dir === 'up' });
      }, EXIT_MS);
    },
    [exit, onLike, onPass, top],
  );

  const stepPhoto = useCallback(
    (delta: number) => {
      if (!top) return;
      const count = top.photos.length;
      if (count < 2) return;
      setPhoto({
        id: top.userId,
        index: Math.min(Math.max(photoIndex + delta, 0), count - 1),
      });
    },
    [photoIndex, top],
  );

  if (!top) return null;

  const currentPhoto = top.photos[photoIndex];

  /** A like from a comment box: super flies up, a normal like flies right. */
  const sendIntent = async (intent: LikeIntent) => {
    fling(intent.superLike ? 'up' : 'right', intent);
    return true;
  };

  // ---- gestures -------------------------------------------------------

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (exiting || (event.target as HTMLElement).closest('button, a')) return;
    gesture.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      lastX: event.clientX,
      lastT: event.timeStamp,
      velocity: 0,
      moved: false,
    };
    try {
      event.currentTarget.setPointerCapture(event.pointerId);
    } catch {
      // The pointer is already gone (released between events); the gesture still works
      // without capture, it just stops tracking if the pointer leaves the element.
    }
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const current = gesture.current;
    if (!current || current.pointerId !== event.pointerId) return;
    const dx = event.clientX - current.startX;
    const dy = event.clientY - current.startY;
    if (!current.moved && Math.hypot(dx, dy) < TAP_SLOP) return;
    current.moved = true;
    const dt = Math.max(1, event.timeStamp - current.lastT);
    current.velocity = (event.clientX - current.lastX) / dt;
    current.lastX = event.clientX;
    current.lastT = event.timeStamp;
    setDrag({ id: topId, x: dx, y: dy, active: true });
  };

  const onPointerUp = (event: PointerEvent<HTMLDivElement>) => {
    const current = gesture.current;
    if (!current || current.pointerId !== event.pointerId) return;
    gesture.current = null;

    if (!current.moved) {
      // A tap: left side goes back a photo, the rest goes forward.
      const box = event.currentTarget.getBoundingClientRect();
      stepPhoto(event.clientX - box.left < box.width * 0.35 ? -1 : 1);
      return;
    }

    const dx = event.clientX - current.startX;
    const dy = event.clientY - current.startY;
    // Mostly upward and far enough: a super like.
    if (dy < -SWIPE_DISTANCE && Math.abs(dy) > Math.abs(dx) * 1.2) {
      fling('up');
      setDrag({ id: topId, x: dx, y: dy, active: false });
      return;
    }
    // Speed only counts if the finger was still moving when it let go - drag, pause and
    // release is a deliberate "put it back", not a flick.
    const stillMoving = event.timeStamp - current.lastT < 80;
    const flick = stillMoving && Math.abs(current.velocity) > SWIPE_VELOCITY && Math.abs(dx) > 40;
    const swiped = Math.abs(dx) > SWIPE_DISTANCE || flick;
    if (swiped) fling(dx > 0 ? 'right' : 'left');
    // A swiped card keeps its offset (the exit transform takes over); otherwise it springs back.
    setDrag({
      id: topId,
      x: swiped ? dx : 0,
      y: swiped ? dragY : 0,
      active: false,
    });
  };

  const onPointerCancel = () => {
    gesture.current = null;
    setDrag({ id: topId, x: 0, y: 0, active: false });
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowRight') stepPhoto(1);
    if (event.key === 'ArrowLeft') stepPhoto(-1);
  };

  // ---- presentation ---------------------------------------------------

  const upward = dragY < 0 && Math.abs(dragY) > Math.abs(dragX);
  const progress = upward ? 0 : Math.min(Math.abs(dragX) / SWIPE_DISTANCE, 1);
  const superProgress = upward ? Math.min(-dragY / SWIPE_DISTANCE, 1) : 0;
  const topStyle: CSSProperties = exiting
    ? {
        transform:
          exiting === 'up'
            ? `translate(${dragX * 0.3}px, -140%)`
            : `translate(${exiting === 'right' ? 140 : -140}%, ${dragY * 0.3}px) rotate(${exiting === 'right' ? 18 : -18}deg)`,
        transition: `transform ${EXIT_MS}ms cubic-bezier(0.4, 0, 1, 1), opacity ${EXIT_MS}ms`,
        opacity: 0.6,
      }
    : {
        transform: upward
          ? `translate(${dragX * 0.3}px, ${dragY}px)`
          : `translate(${dragX}px, ${dragY * 0.3}px) rotate(${dragX / 22}deg)`,
        transition: dragging ? 'none' : 'transform 380ms cubic-bezier(0.2, 0.9, 0.3, 1.15)',
      };
  const likeStamp = exiting === 'right' ? 1 : dragX > 0 ? progress : 0;
  const nopeStamp = exiting === 'left' ? 1 : dragX < 0 ? progress : 0;
  const superStamp = exiting === 'up' ? 1 : superProgress;
  // The card underneath grows into place as the top one is pulled away.
  const lift = exiting ? 1 : Math.max(progress, superProgress);

  return (
    <div className="mx-auto flex w-full max-w-[440px] flex-col items-center gap-5">
      <div className="relative h-[calc(100dvh-19rem)] max-h-[660px] min-h-[420px] w-full md:h-[calc(100dvh-13rem)]">
        {cards[2] ? (
          <BackCard
            card={cards[2]}
            style={{
              transform: `translateY(${26 - lift * 13}px) scale(${0.9 + lift * 0.05})`,
            }}
          />
        ) : null}
        {cards[1] ? (
          <BackCard
            card={cards[1]}
            style={{
              transform: `translateY(${13 - lift * 13}px) scale(${0.95 + lift * 0.05})`,
            }}
          />
        ) : null}

        <div
          key={top.userId}
          role="group"
          aria-roledescription="profile card"
          aria-label={`${top.displayName}, ${top.age}. Photo ${photoIndex + 1} of ${Math.max(top.photos.length, 1)}`}
          tabIndex={0}
          onKeyDown={onKeyDown}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerCancel}
          style={{ ...topStyle, touchAction: 'pan-y' }}
          className="absolute inset-0 z-10 cursor-grab select-none overflow-hidden rounded-[1.75rem] bg-surface-muted shadow-[0_24px_60px_-24px_rgb(0_0_0/0.55)] ring-1 ring-black/5 will-change-transform focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent active:cursor-grabbing"
        >
          <CardPhoto photo={currentPhoto} alt={top.displayName} priority />

          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/90 via-black/35 via-45% to-transparent to-70%"
          />
          <div
            aria-hidden
            className="pointer-events-none absolute inset-x-0 top-0 h-28 bg-gradient-to-b from-black/45 to-transparent"
          />

          {top.photos.length > 1 ? (
            <div className="pointer-events-none absolute inset-x-3 top-3 flex gap-1.5">
              {top.photos.map((item, index) => (
                <span
                  key={item.id}
                  className={cn(
                    'h-[3px] flex-1 rounded-full transition-colors duration-300',
                    index === photoIndex ? 'bg-white' : 'bg-white/35',
                  )}
                />
              ))}
            </div>
          ) : null}

          <div className="pointer-events-none absolute left-3 top-6 flex flex-wrap items-center gap-1.5">
            {compatibilityLabel(top.compatibilityScore) ? (
              <span className="rounded-full bg-accent-gradient px-2.5 py-1 text-[11.5px] font-semibold text-white shadow-sm">
                {compatibilityLabel(top.compatibilityScore)}
              </span>
            ) : null}
            {top.superLikedYou ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-gradient-to-r from-sky-500 to-indigo-500 px-2.5 py-1 text-[11.5px] font-semibold text-white shadow-sm">
                <StarIcon size={12} filled />
                Super liked you
              </span>
            ) : null}
            {top.recentlyActive ? (
              <span className="glass-dark inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11.5px] font-medium text-white">
                <span className="h-1.5 w-1.5 rounded-full bg-success" />
                Active now
              </span>
            ) : null}
          </div>

          <Stamp label="LIKE" opacity={likeStamp} side="left" />
          <Stamp label="NOPE" opacity={nopeStamp} side="right" />
          <Stamp label="SUPER" opacity={superStamp} side="bottom" />

          <CardInfo
            card={top}
            photoIndex={photoIndex}
            onOpenDetails={() => setDetailsOpen(true)}
            onCommentPhoto={currentPhoto ? () => setLikeTarget({ kind: 'photo', photo: currentPhoto }) : undefined}
            onReplyPrompt={(prompt) => setLikeTarget({ kind: 'prompt', prompt })}
          />
        </div>
      </div>

      <ActionBar
        disabled={Boolean(exiting)}
        onPass={() => fling('left')}
        onSuperLike={() => fling('up')}
        onLike={() => fling('right')}
        name={top.displayName}
      />

      <ProfileSheet
        key={top.userId}
        card={top}
        open={detailsOpen}
        onClose={() => setDetailsOpen(false)}
        onPass={() => fling('left')}
        onSuperLike={() => fling('up')}
        onLike={() => fling('right')}
        onSend={sendIntent}
      />

      <LikeTargetSheet
        name={top.displayName}
        target={likeTarget}
        onClose={() => setLikeTarget(null)}
        onSend={sendIntent}
      />
    </div>
  );
}

/** The bottom of the card: who they are, at a glance. */
function CardInfo({
  card,
  photoIndex,
  onOpenDetails,
  onCommentPhoto,
  onReplyPrompt,
}: {
  card: FeedCard;
  photoIndex: number;
  onOpenDetails: () => void;
  onCommentPhoto?: () => void;
  onReplyPrompt: (prompt: PromptAnswer) => void;
}) {
  const distance = distanceLabel(card.distanceKm);
  const shared = new Set(card.sharedInterests);
  // Shared interests first - they are the easiest thing to start a conversation with.
  const chips = [
    ...card.interests.filter((tag) => shared.has(tag.label)).map((tag) => ({ tag, shared: true })),
    ...card.interests
      .filter((tag) => !shared.has(tag.label))
      .map((tag) => ({ tag, shared: false })),
  ].slice(0, 3);

  // First photo: the bio. Later photos: one prompt each, the way a profile reads.
  const prompt =
    photoIndex > 0 ? card.prompts[(photoIndex - 1) % Math.max(card.prompts.length, 1)] : undefined;
  const line = photoIndex === 0 ? (card.bio ?? card.prompts[0]?.answer) : prompt?.answer;
  const eyebrow = photoIndex === 0 ? (card.bio ? null : card.prompts[0]?.prompt) : prompt?.prompt;
  // The prompt actually on screen, if any - that is the one "Reply" answers.
  const shownPrompt = photoIndex === 0 ? (card.bio ? undefined : card.prompts[0]) : prompt;

  return (
    <div className="absolute inset-x-0 bottom-0 flex items-end gap-3 p-5 text-white">
      <div className="pointer-events-none min-w-0 flex-1 space-y-2">
        <h2 className="flex items-center gap-2 font-display text-[30px] font-semibold leading-none tracking-[-0.02em] drop-shadow-sm">
          <span className="truncate">{card.displayName}</span>
          <span className="font-sans text-[24px] font-medium text-white/80">{card.age}</span>
          {card.photoVerified ? (
            <span className="shrink-0 text-sky-300" title="Photo verified">
              <ShieldCheckIcon size={20} />
              <span className="sr-only">Photo verified</span>
            </span>
          ) : null}
        </h2>

        {card.jobTitle || distance ? (
          <p className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[13.5px] font-medium text-white/85">
            {card.jobTitle ? <span className="truncate">{card.jobTitle}</span> : null}
            {card.jobTitle && distance ? <span className="text-white/40">·</span> : null}
            {distance ? (
              <span className="inline-flex items-center gap-1">
                <MapPinIcon size={14} />
                {distance}
              </span>
            ) : null}
          </p>
        ) : null}

        {line ? (
          <div className="space-y-0.5">
            {eyebrow ? (
              <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-white/60">
                {eyebrow}
              </p>
            ) : null}
            <p className="line-clamp-2 text-[14.5px] leading-snug text-white/90">{line}</p>
            {shownPrompt ? (
              <button
                type="button"
                onClick={() => onReplyPrompt(shownPrompt)}
                className="pointer-events-auto mt-1 inline-flex items-center gap-1 rounded-full bg-white/15 px-2.5 py-1 text-[11.5px] font-semibold text-white ring-1 ring-inset ring-white/25 backdrop-blur-md transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
              >
                <CommentIcon size={12} />
                Reply to prompt
              </button>
            ) : null}
          </div>
        ) : null}

        {chips.length > 0 ? (
          <div className="flex flex-wrap gap-1.5 pt-0.5">
            {chips.map(({ tag, shared: isShared }) => (
              <span
                key={tag.id}
                className={cn(
                  'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[12px] font-medium backdrop-blur-md',
                  isShared
                    ? 'bg-white text-ink'
                    : 'bg-white/15 text-white ring-1 ring-inset ring-white/20',
                )}
              >
                {tag.emoji ? <span aria-hidden>{tag.emoji}</span> : null}
                {tag.label}
              </span>
            ))}
          </div>
        ) : null}
      </div>

      <div className="mb-0.5 flex shrink-0 flex-col gap-2.5">
        {onCommentPhoto ? (
          <button
            type="button"
            onClick={onCommentPhoto}
            aria-label={`Like and comment on this photo of ${card.displayName}`}
            title="Like & comment on this photo"
            className="glass-dark flex h-10 w-10 items-center justify-center rounded-full text-white ring-1 ring-inset ring-white/25 transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
          >
            <CommentIcon size={19} />
          </button>
        ) : null}
        <button
          type="button"
          onClick={onOpenDetails}
          aria-label={`See ${card.displayName}'s full profile`}
          className="glass-dark flex h-10 w-10 items-center justify-center rounded-full text-white ring-1 ring-inset ring-white/25 transition-transform duration-200 ease-snap hover:scale-105 active:scale-95"
        >
          <InfoIcon size={20} />
        </button>
      </div>
    </div>
  );
}

function CardPhoto({ photo, alt, priority }: { photo?: Photo; alt: string; priority?: boolean }) {
  if (!photo) {
    return (
      <div className="flex h-full w-full flex-col items-center justify-center gap-2 text-ink-subtle">
        <ImageIcon size={28} />
        <span className="text-sm font-medium">No photos yet</span>
      </div>
    );
  }
  return (
    <Image
      src={photo.url}
      alt={alt}
      fill
      draggable={false}
      sizes="(max-width: 768px) 100vw, 440px"
      className="pointer-events-none object-cover"
      priority={priority}
      unoptimized
    />
  );
}

/** A card waiting underneath. Just the photo - it is not interactive until it is on top. */
function BackCard({ card, style }: { card: FeedCard; style: CSSProperties }) {
  return (
    <div
      aria-hidden
      style={{
        ...style,
        transition: 'transform 300ms cubic-bezier(0.2, 0.9, 0.3, 1)',
      }}
      className="absolute inset-0 overflow-hidden rounded-[1.75rem] bg-surface-muted shadow-card ring-1 ring-black/5"
    >
      <CardPhoto photo={card.photos[0]} alt="" />
      <div className="absolute inset-0 bg-black/10" />
    </div>
  );
}

function Stamp({
  label,
  opacity,
  side,
}: {
  label: string;
  opacity: number;
  side: 'left' | 'right' | 'bottom';
}) {
  if (opacity <= 0) return null;
  const like = label === 'LIKE';
  const superLike = label === 'SUPER';
  return (
    <span
      aria-hidden
      style={{ opacity }}
      className={cn(
        'pointer-events-none absolute top-14 rounded-xl border-[3px] px-3 py-1 font-display text-3xl font-bold tracking-wider',
        side === 'left' && 'left-6 -rotate-12',
        side === 'right' && 'right-6 rotate-12',
        side === 'bottom' && '!top-auto bottom-40 left-1/2 -translate-x-1/2 -rotate-6',
        superLike
          ? 'border-sky-400 text-sky-400'
          : like
            ? 'border-emerald-400 text-emerald-400'
            : 'border-rose-500 text-rose-500',
      )}
    >
      {label}
    </span>
  );
}

function ActionBar({
  disabled,
  onPass,
  onSuperLike,
  onLike,
  name,
}: {
  disabled: boolean;
  onPass: () => void;
  onSuperLike: () => void;
  onLike: () => void;
  name: string;
}) {
  const quota = useLikeQuota();
  const superLeft = quota.data?.superLikesRemaining;

  return (
    <div className="flex items-center justify-center gap-5">
      <button
        type="button"
        onClick={onPass}
        disabled={disabled}
        aria-label={`Pass on ${name}`}
        title="Pass"
        className="flex h-16 w-16 items-center justify-center rounded-full bg-surface text-rose-500 shadow-card ring-1 ring-border transition-all duration-200 ease-snap hover:scale-105 hover:shadow-lift active:scale-95 disabled:opacity-50"
      >
        <CloseIcon size={28} strokeWidth={2.4} />
      </button>

      {/* Smaller and in the middle, as on every deck: the rarer, stronger signal. */}
      <button
        type="button"
        onClick={onSuperLike}
        disabled={disabled}
        aria-label={`Super like ${name}${superLeft != null && superLeft >= 0 ? `, ${superLeft} left today` : ''}`}
        title="Super like - puts you at the top of their likes"
        className="relative flex h-12 w-12 items-center justify-center rounded-full bg-surface text-sky-500 shadow-card ring-1 ring-border transition-all duration-200 ease-snap hover:scale-110 hover:bg-gradient-to-br hover:from-sky-500 hover:to-indigo-500 hover:text-white active:scale-95 disabled:opacity-50"
      >
        <StarIcon size={22} filled />
        {superLeft != null && superLeft >= 0 ? (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-sky-500 px-1 text-[10.5px] font-semibold text-white">
            {superLeft}
          </span>
        ) : null}
      </button>

      <button
        type="button"
        onClick={onLike}
        disabled={disabled}
        aria-label={`Like ${name}`}
        title="Like"
        className="sheen flex h-16 w-16 items-center justify-center rounded-full bg-accent-gradient text-white shadow-glow transition-all duration-200 ease-snap hover:scale-105 hover:shadow-glow-lg active:scale-95 disabled:opacity-50"
      >
        <HeartIcon size={28} filled />
      </button>
    </div>
  );
}

/**
 * Everything the card leaves out: every photo and prompt, the bio, the facts. Each photo
 * and prompt has its own "like & comment" box underneath, like a full profile page.
 */
function ProfileSheet({
  card,
  open,
  onClose,
  onPass,
  onSuperLike,
  onLike,
  onSend,
}: {
  card: FeedCard;
  open: boolean;
  onClose: () => void;
  onPass: () => void;
  onSuperLike: () => void;
  onLike: () => void;
  onSend: (intent: LikeIntent) => Promise<boolean>;
}) {
  const [openItem, setOpenItem] = useState<string | null>(null);

  // Photos and prompts alternate, the way a profile is read.
  const items: Array<{ key: string; target: LikeTarget }> = [];
  const count = Math.max(card.photos.length, card.prompts.length);
  for (let i = 0; i < count; i++) {
    const photo = card.photos[i];
    if (photo) items.push({ key: `photo-${photo.id}`, target: { kind: 'photo', photo } });
    const prompt = card.prompts[i];
    if (prompt) items.push({ key: `prompt-${prompt.id}`, target: { kind: 'prompt', prompt } });
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={`${card.displayName}, ${card.age}`}
      description={
        [card.jobTitle, distanceLabel(card.distanceKm)].filter(Boolean).join(' · ') || undefined
      }
      size="tall"
    >
      <div className="space-y-6">
        {card.highlights.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {card.highlights.map((highlight) => (
              <span
                key={highlight}
                className="rounded-full bg-accent-gradient-soft px-3 py-1 text-xs font-semibold text-accent"
              >
                {highlight}
              </span>
            ))}
          </div>
        ) : null}

        {card.bio ? <p className="text-[15px] leading-relaxed text-ink">{card.bio}</p> : null}

        {items.map(({ key, target }) => (
          <LikeableItem
            key={key}
            name={card.displayName}
            target={target}
            open={openItem === key}
            onToggle={() => setOpenItem((current) => (current === key ? null : key))}
            onSend={onSend}
            enabled
          >
            {target.kind === 'photo' ? (
              <SheetPhoto photo={target.photo} alt={card.displayName} />
            ) : (
              <section className="space-y-1.5 rounded-xl2 bg-surface-muted p-4">
                <p className="eyebrow">{target.prompt.prompt}</p>
                <p className="font-display text-[19px] leading-[1.35] text-ink">{target.prompt.answer}</p>
              </section>
            )}
          </LikeableItem>
        ))}

        <ProfileDetails
          jobTitle={card.jobTitle}
          school={card.school}
          heightCm={card.heightCm}
          interests={card.interests}
          qualities={card.qualities}
          sharedInterests={card.sharedInterests}
        />

        <div className="sticky -bottom-8 -mx-6 -mb-8 flex gap-3 border-t border-border bg-surface px-6 pb-8 pt-4 sm:-bottom-6 sm:-mb-6 sm:pb-6">
          <Button
            variant="outline"
            size="lg"
            fullWidth
            onClick={onPass}
            leftIcon={<CloseIcon size={18} />}
          >
            Pass
          </Button>
          <Button
            variant="secondary"
            size="icon"
            onClick={onSuperLike}
            aria-label={`Super like ${card.displayName}`}
            className="h-[52px] w-[52px] bg-gradient-to-br from-sky-500 to-indigo-500 text-white"
          >
            <StarIcon size={20} filled />
          </Button>
          <Button size="lg" fullWidth onClick={onLike} leftIcon={<HeartIcon size={18} />}>
            Like
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

function SheetPhoto({ photo, alt }: { photo: Photo; alt: string }) {
  return (
    <div className="relative aspect-[4/5] w-full overflow-hidden rounded-xl2 bg-surface-muted shadow-card">
      <Image src={photo.url} alt={alt} fill sizes="(max-width: 768px) 100vw, 480px" className="object-cover" unoptimized />
    </div>
  );
}
