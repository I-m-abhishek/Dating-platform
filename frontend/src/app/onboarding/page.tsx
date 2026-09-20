'use client';

import { useRef, useState } from 'react';
import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { withAuth, withErrorBoundary } from '@/hoc';
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { Textarea } from '@/components/ui/Textarea';
import { accountApi } from '@/lib/api/endpoints';
import { usePhotos, useReferenceData, useUpdateProfile } from '@/lib/hooks/useProfile';
import { useAuthStore } from '@/lib/stores/authStore';
import { useUiStore } from '@/lib/stores/uiStore';
import { messageOf } from '@/lib/api/errors';
import { humanise } from '@/lib/utils/format';
import type { RelationshipIntent } from '@/lib/api/types';

const STEPS = ['Location', 'Photos', 'About you', 'Interests'] as const;
const INTENTS: RelationshipIntent[] = [
  'LONG_TERM',
  'LONG_TERM_OPEN_TO_SHORT',
  'FIGURING_IT_OUT',
  'SHORT_TERM_OPEN_TO_LONG',
  'SHORT_TERM',
  'NEW_FRIENDS',
];

/**
 * Onboarding.
 *
 * <p>Note the guard: {@code withAuth(..., { requireOnboarding: false })}. Every other page
 * bounces an un-onboarded user here; this one must not, or it would redirect to itself.
 *
 * <p>The order is deliberate. Location first because nothing can be shown without it, then
 * photos because they gate activation server side, then the words. Asking for a bio before
 * a photo is how you get a profile with a bio and no photo.
 */
function OnboardingPage() {
  const router = useRouter();
  const setAccount = useAuthStore((state) => state.setAccount);
  const toast = useUiStore((state) => state.toast);
  const { photos, upload, isUploading } = usePhotos();
  const { interests } = useReferenceData();
  const updateProfile = useUpdateProfile();
  const fileInput = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState(0);
  const [locating, setLocating] = useState(false);
  const [located, setLocated] = useState(false);
  const [bio, setBio] = useState('');
  const [intent, setIntent] = useState<RelationshipIntent | undefined>();
  const [interestIds, setInterestIds] = useState<string[]>([]);
  const [finishing, setFinishing] = useState(false);

  const shareLocation = () => {
    if (!navigator.geolocation) {
      toast({ title: 'Your browser cannot share a location', tone: 'error' });
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const account = await accountApi.updateLocation(
            position.coords.latitude,
            position.coords.longitude,
          );
          setAccount(account);
          setLocated(true);
          setStep(1);
        } catch (error) {
          toast({ title: messageOf(error), tone: 'error' });
        } finally {
          setLocating(false);
        }
      },
      () => {
        setLocating(false);
        toast({
          title: 'We could not get your location',
          description: 'You can allow it in your browser settings and try again.',
          tone: 'error',
        });
      },
      { enableHighAccuracy: false, timeout: 10000 },
    );
  };

  const finish = async () => {
    setFinishing(true);
    try {
      await updateProfile.mutateAsync({ bio, relationshipIntent: intent, interestIds });
      const account = await accountApi.completeOnboarding();
      setAccount(account);
      router.replace('/home');
    } catch (error) {
      toast({ title: messageOf(error), tone: 'error' });
    } finally {
      setFinishing(false);
    }
  };

  const toggleInterest = (id: string) => {
    setInterestIds((current) => {
      if (current.includes(id)) return current.filter((item) => item !== id);
      return current.length < 12 ? [...current, id] : current;
    });
  };

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-md flex-col px-6 py-10">
      <div className="mb-8 flex gap-1.5" aria-label={`Step ${step + 1} of ${STEPS.length}`}>
        {STEPS.map((label, index) => (
          <span
            key={label}
            className={index <= step ? 'h-1 flex-1 rounded-full bg-accent' : 'h-1 flex-1 rounded-full bg-border'}
          />
        ))}
      </div>

      <div className="flex-1 space-y-6">
        {step === 0 ? (
          <section className="space-y-3">
            <h1 className="text-xl font-semibold text-ink">Where are you?</h1>
            <p className="text-sm text-ink-muted">
              We use this to show people nearby. Others only ever see a rounded distance, never
              your location.
            </p>
            <Button fullWidth loading={locating} onClick={shareLocation}>
              {located ? 'Location saved' : 'Share my location'}
            </Button>
          </section>
        ) : null}

        {step === 1 ? (
          <section className="space-y-3">
            <h1 className="text-xl font-semibold text-ink">Add a few photos</h1>
            <p className="text-sm text-ink-muted">
              At least one to continue. Four is where profiles start doing well.
            </p>

            <div className="grid grid-cols-3 gap-2">
              {photos.map((photo) => (
                <div
                  key={photo.id}
                  className="relative aspect-[3/4] overflow-hidden rounded-2xl bg-surface-muted"
                >
                  <Image src={photo.url} alt="" fill className="object-cover" unoptimized />
                </div>
              ))}
              {photos.length < 9 ? (
                <button
                  type="button"
                  onClick={() => fileInput.current?.click()}
                  disabled={isUploading}
                  className="flex aspect-[3/4] items-center justify-center rounded-2xl border-2 border-dashed border-border text-2xl text-ink-subtle hover:border-accent hover:text-accent"
                >
                  {isUploading ? '…' : '+'}
                </button>
              ) : null}
            </div>
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void upload({ file });
                event.target.value = '';
              }}
            />
          </section>
        ) : null}

        {step === 2 ? (
          <section className="space-y-4">
            <h1 className="text-xl font-semibold text-ink">A little about you</h1>
            <Textarea
              label="Bio"
              value={bio}
              counterMax={500}
              rows={4}
              placeholder="What would you want someone to know before they message you?"
              onChange={(event) => setBio(event.target.value)}
            />
            <div className="space-y-2">
              <p className="text-sm font-medium text-ink-muted">I am looking for</p>
              <div className="flex flex-wrap gap-2">
                {INTENTS.map((value) => (
                  <Chip key={value} selected={intent === value} onClick={() => setIntent(value)}>
                    {humanise(value)}
                  </Chip>
                ))}
              </div>
            </div>
          </section>
        ) : null}

        {step === 3 ? (
          <section className="space-y-3">
            <h1 className="text-xl font-semibold text-ink">What are you into?</h1>
            <p className="text-sm text-ink-muted">
              Pick a few. These are what the matching engine compares when it picks your weekly
              match.
            </p>
            <div className="flex flex-wrap gap-2">
              {interests.map((tag) => (
                <Chip
                  key={tag.id}
                  size="sm"
                  selected={interestIds.includes(tag.id)}
                  onClick={() => toggleInterest(tag.id)}
                >
                  {tag.emoji ? <span aria-hidden>{tag.emoji}</span> : null}
                  {tag.label}
                </Chip>
              ))}
            </div>
          </section>
        ) : null}
      </div>

      <div className="flex gap-3 pt-8">
        {step > 0 ? (
          <Button variant="ghost" fullWidth onClick={() => setStep(step - 1)}>
            Back
          </Button>
        ) : null}

        {step < STEPS.length - 1 ? (
          <Button
            fullWidth
            disabled={(step === 0 && !located) || (step === 1 && photos.length === 0)}
            onClick={() => setStep(step + 1)}
          >
            Continue
          </Button>
        ) : (
          <Button fullWidth loading={finishing} onClick={() => void finish()}>
            Start matching
          </Button>
        )}
      </div>
    </div>
  );
}

// Deliberately not the usual compose(...) chain: onboarding is the one page that must NOT
// require a completed onboarding, or the guard would redirect it to itself.
const GuardedOnboardingPage = withAuth(OnboardingPage, { requireOnboarding: false });

export default withErrorBoundary(GuardedOnboardingPage);
