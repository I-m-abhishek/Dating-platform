import { Chip } from '@/components/ui/Chip';
import { heightLabel, humanise } from '@/lib/utils/format';
import type { Tag } from '@/lib/api/types';

export interface ProfileDetailsProps {
  jobTitle?: string;
  school?: string;
  hometown?: string;
  heightCm?: number;
  religion?: string;
  zodiacSign?: string;
  relationshipIntent?: string;
  drinking?: string;
  smoking?: string;
  /** Named childrenPreference, not children - React reserves that prop name. */
  childrenPreference?: string;
  languages?: string[];
  interests?: Tag[];
  qualities?: Tag[];
  sharedInterests?: string[];
}

/** The facts block. Anything absent is simply not rendered - no "not specified" filler. */
export function ProfileDetails(props: ProfileDetailsProps) {
  const facts: Array<[string, string | null | undefined]> = [
    ['Work', props.jobTitle],
    ['Education', props.school],
    ['From', props.hometown],
    ['Height', heightLabel(props.heightCm)],
    ['Beliefs', props.religion],
    ['Star sign', props.zodiacSign],
    ['Looking for', humanise(props.relationshipIntent) || null],
    ['Drinks', humanise(props.drinking) || null],
    ['Smokes', humanise(props.smoking) || null],
    ['Children', humanise(props.childrenPreference) || null],
    ['Speaks', props.languages?.length ? props.languages.join(', ') : null],
  ];

  const present = facts.filter(([, value]) => Boolean(value));
  const shared = new Set(props.sharedInterests ?? []);

  return (
    <div className="space-y-5">
      {present.length > 0 ? (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
          {present.map(([label, value]) => (
            <div key={label}>
              <dt className="text-xs uppercase tracking-wide text-ink-subtle">{label}</dt>
              <dd className="mt-0.5 text-sm text-ink">{value}</dd>
            </div>
          ))}
        </dl>
      ) : null}

      {props.interests?.length ? (
        <section className="space-y-2">
          <h3 className="text-xs uppercase tracking-wide text-ink-subtle">Interests</h3>
          <div className="flex flex-wrap gap-2">
            {props.interests.map((interest) => (
              <Chip key={interest.id} size="sm" selected={shared.has(interest.label)}>
                {interest.emoji ? <span aria-hidden>{interest.emoji}</span> : null}
                {interest.label}
              </Chip>
            ))}
          </div>
        </section>
      ) : null}

      {props.qualities?.length ? (
        <section className="space-y-2">
          <h3 className="text-xs uppercase tracking-wide text-ink-subtle">Qualities</h3>
          <div className="flex flex-wrap gap-2">
            {props.qualities.map((quality) => (
              <Chip key={quality.id} size="sm">
                {quality.label}
              </Chip>
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}
