'use client';

import { useEffect, useRef, useState } from 'react';
import Image from 'next/image';
import { compose, withAuth, withErrorBoundary } from '@/hoc';
import { TopBar } from '@/components/layout/TopBar';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Chip } from '@/components/ui/Chip';
import { FullPageLoader } from '@/components/ui/FullPageLoader';
import { useMyProfile, usePhotos, useReferenceData, useUpdateProfile } from '@/lib/hooks/useProfile';
import { profileApi } from '@/lib/api/endpoints';
import { useUiStore } from '@/lib/stores/uiStore';
import { humanise } from '@/lib/utils/format';
import { messageOf } from '@/lib/api/errors';
import type { ChildrenPreference, LifestyleChoice, RelationshipIntent } from '@/lib/api/types';

const INTENTS: RelationshipIntent[] = [
  'LONG_TERM',
  'LONG_TERM_OPEN_TO_SHORT',
  'FIGURING_IT_OUT',
  'SHORT_TERM_OPEN_TO_LONG',
  'SHORT_TERM',
  'NEW_FRIENDS',
];
const LIFESTYLE: LifestyleChoice[] = ['YES', 'SOMETIMES', 'NO', 'PREFER_NOT_TO_SAY'];
const CHILDREN: ChildrenPreference[] = [
  'WANT_SOMEDAY',
  'DONT_WANT',
  'HAVE_AND_WANT_MORE',
  'HAVE_AND_DONT_WANT_MORE',
  'OPEN_TO_CHILDREN',
  'NOT_SURE',
];

const MAX_INTERESTS = 12;
const MAX_QUALITIES = 6;

/**
 * Profile editor.
 *
 * <p>Saves the whole draft in one PATCH rather than per field. Auto-save per keystroke
 * reads well in a demo and is miserable in practice: it fires validation errors while you
 * are still typing and makes undo impossible.
 */
function EditProfilePage() {
  const profileQuery = useMyProfile();
  const update = useUpdateProfile();
  const { photos, upload, isUploading, remove } = usePhotos();
  const { interests, qualities, prompts } = useReferenceData();
  const toast = useUiStore((state) => state.toast);
  const fileInput = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState({ bio: '', jobTitle: '', school: '', hometown: '', heightCm: '' });
  const [intent, setIntent] = useState<RelationshipIntent | undefined>();
  const [drinking, setDrinking] = useState<LifestyleChoice | undefined>();
  const [smoking, setSmoking] = useState<LifestyleChoice | undefined>();
  const [children, setChildren] = useState<ChildrenPreference | undefined>();
  const [interestIds, setInterestIds] = useState<string[]>([]);
  const [qualityIds, setQualityIds] = useState<string[]>([]);

  // Seed the draft once the profile arrives.
  useEffect(() => {
    const profile = profileQuery.data;
    if (!profile) return;
    setForm({
      bio: profile.bio ?? '',
      jobTitle: profile.jobTitle ?? '',
      school: profile.school ?? '',
      hometown: profile.hometown ?? '',
      heightCm: profile.heightCm ? String(profile.heightCm) : '',
    });
    setIntent(profile.relationshipIntent);
    setDrinking(profile.drinking);
    setSmoking(profile.smoking);
    setChildren(profile.children);
    setInterestIds(profile.interests.map((tag) => tag.id));
    setQualityIds(profile.qualities.map((tag) => tag.id));
  }, [profileQuery.data]);

  if (profileQuery.isPending) return <FullPageLoader />;

  const toggle = (list: string[], setList: (next: string[]) => void, id: string, max: number) => {
    if (list.includes(id)) {
      setList(list.filter((item) => item !== id));
      return;
    }
    if (list.length >= max) {
      toast({ title: `Pick at most ${max}`, tone: 'error' });
      return;
    }
    setList([...list, id]);
  };

  const onSave = () => {
    update.mutate({
      bio: form.bio,
      jobTitle: form.jobTitle,
      school: form.school,
      hometown: form.hometown,
      heightCm: form.heightCm ? Number(form.heightCm) : undefined,
      relationshipIntent: intent,
      drinking,
      smoking,
      children,
      interestIds,
      qualityIds,
    });
  };

  const onAnswerPrompt = async (promptId: string, answer: string) => {
    try {
      await profileApi.upsertPrompt(promptId, answer);
      toast({ title: 'Prompt saved', tone: 'success' });
      void profileQuery.refetch();
    } catch (error) {
      toast({ title: messageOf(error), tone: 'error' });
    }
  };

  return (
    <>
      <TopBar
        showBack
        title="Edit profile"
        action={
          <Button size="sm" loading={update.isPending} onClick={onSave}>
            Save
          </Button>
        }
      />

      <div className="space-y-8 p-4 pb-24">
        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">Photos</h2>
          <div className="grid grid-cols-3 gap-2">
            {photos.map((photo) => (
              <div
                key={photo.id}
                className="relative aspect-[3/4] overflow-hidden rounded-2xl bg-surface-muted"
              >
                <Image src={photo.url} alt="" fill className="object-cover" unoptimized />
                <button
                  type="button"
                  onClick={() => remove(photo.id)}
                  aria-label="Remove photo"
                  className="absolute right-1.5 top-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-black/55 text-xs text-white"
                >
                  ×
                </button>
                {photo.primaryPhoto ? (
                  <span className="absolute bottom-1.5 left-1.5 rounded-pill bg-black/55 px-2 py-0.5 text-[10px] text-white">
                    Main
                  </span>
                ) : null}
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
          <p className="text-xs text-ink-subtle">The first photo is what people see first.</p>
        </section>

        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">About you</h2>
          <Textarea
            label="Bio"
            value={form.bio}
            counterMax={500}
            rows={4}
            onChange={(event) => setForm({ ...form, bio: event.target.value })}
          />
          <Input
            label="Work"
            value={form.jobTitle}
            onChange={(event) => setForm({ ...form, jobTitle: event.target.value })}
          />
          <Input
            label="Education"
            value={form.school}
            onChange={(event) => setForm({ ...form, school: event.target.value })}
          />
          <Input
            label="Hometown"
            value={form.hometown}
            onChange={(event) => setForm({ ...form, hometown: event.target.value })}
          />
          <Input
            label="Height (cm)"
            type="number"
            min={120}
            max={250}
            value={form.heightCm}
            onChange={(event) => setForm({ ...form, heightCm: event.target.value })}
          />
        </section>

        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">Looking for</h2>
          <div className="flex flex-wrap gap-2">
            {INTENTS.map((value) => (
              <Chip key={value} selected={intent === value} onClick={() => setIntent(value)}>
                {humanise(value)}
              </Chip>
            ))}
          </div>
        </section>

        <section className="space-y-4">
          <h2 className="text-sm font-medium text-ink-muted">Lifestyle</h2>
          <LifestyleRow label="Drinking" value={drinking} onChange={setDrinking} />
          <LifestyleRow label="Smoking" value={smoking} onChange={setSmoking} />
          <div className="space-y-2">
            <p className="text-xs uppercase tracking-wide text-ink-subtle">Children</p>
            <div className="flex flex-wrap gap-2">
              {CHILDREN.map((value) => (
                <Chip
                  key={value}
                  size="sm"
                  selected={children === value}
                  onClick={() => setChildren(value)}
                >
                  {humanise(value)}
                </Chip>
              ))}
            </div>
          </div>
        </section>

        <section className="space-y-3">
          <div className="flex items-baseline justify-between">
            <h2 className="text-sm font-medium text-ink-muted">Interests</h2>
            <span className="text-xs text-ink-subtle">
              {interestIds.length}/{MAX_INTERESTS}
            </span>
          </div>
          <p className="text-xs text-ink-subtle">
            These feed the matching engine - shared interests raise your compatibility score.
          </p>
          <div className="flex flex-wrap gap-2">
            {interests.map((tag) => (
              <Chip
                key={tag.id}
                size="sm"
                selected={interestIds.includes(tag.id)}
                onClick={() => toggle(interestIds, setInterestIds, tag.id, MAX_INTERESTS)}
              >
                {tag.emoji ? <span aria-hidden>{tag.emoji}</span> : null}
                {tag.label}
              </Chip>
            ))}
          </div>
        </section>

        <section className="space-y-3">
          <div className="flex items-baseline justify-between">
            <h2 className="text-sm font-medium text-ink-muted">Qualities</h2>
            <span className="text-xs text-ink-subtle">
              {qualityIds.length}/{MAX_QUALITIES}
            </span>
          </div>
          <div className="flex flex-wrap gap-2">
            {qualities.map((tag) => (
              <Chip
                key={tag.id}
                size="sm"
                selected={qualityIds.includes(tag.id)}
                onClick={() => toggle(qualityIds, setQualityIds, tag.id, MAX_QUALITIES)}
              >
                {tag.label}
              </Chip>
            ))}
          </div>
        </section>

        <section className="space-y-3">
          <h2 className="text-sm font-medium text-ink-muted">Prompts</h2>
          <p className="text-xs text-ink-subtle">Answer up to three. These start conversations.</p>
          {prompts.slice(0, 8).map((prompt) => {
            const existing = profileQuery.data?.prompts.find((item) => item.promptId === prompt.id);
            return (
              <PromptEditor
                key={prompt.id}
                text={prompt.text}
                initial={existing?.answer ?? ''}
                onSave={(answer) => void onAnswerPrompt(prompt.id, answer)}
              />
            );
          })}
        </section>

        <Button fullWidth size="lg" loading={update.isPending} onClick={onSave}>
          Save changes
        </Button>
      </div>
    </>
  );
}

function LifestyleRow({
  label,
  value,
  onChange,
}: {
  label: string;
  value?: LifestyleChoice;
  onChange: (next: LifestyleChoice) => void;
}) {
  return (
    <div className="space-y-2">
      <p className="text-xs uppercase tracking-wide text-ink-subtle">{label}</p>
      <div className="flex flex-wrap gap-2">
        {LIFESTYLE.map((choice) => (
          <Chip key={choice} size="sm" selected={value === choice} onClick={() => onChange(choice)}>
            {humanise(choice)}
          </Chip>
        ))}
      </div>
    </div>
  );
}

function PromptEditor({
  text,
  initial,
  onSave,
}: {
  text: string;
  initial: string;
  onSave: (answer: string) => void;
}) {
  const [answer, setAnswer] = useState(initial);
  const dirty = answer.trim() !== initial.trim() && answer.trim().length > 0;

  return (
    <div className="card space-y-2 p-3">
      <p className="text-xs uppercase tracking-wide text-ink-subtle">{text}</p>
      <Textarea
        value={answer}
        counterMax={300}
        rows={2}
        onChange={(event) => setAnswer(event.target.value)}
        aria-label={text}
      />
      {dirty ? (
        <Button size="sm" variant="outline" onClick={() => onSave(answer.trim())}>
          Save answer
        </Button>
      ) : null}
    </div>
  );
}

export default compose(withErrorBoundary, withAuth)(EditProfilePage);
