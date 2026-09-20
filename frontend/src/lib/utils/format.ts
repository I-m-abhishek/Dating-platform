import { formatDistanceToNowStrict, isThisWeek, isToday, isYesterday, format } from 'date-fns';

/** "3 km away", or null when we have no location for one of them. */
export function distanceLabel(km?: number | null): string | null {
  if (km === undefined || km === null) return null;
  if (km <= 1) return 'Less than a km away';
  return `${km} km away`;
}

/** Relative time for chat lists: today shows a clock, this week a weekday, older a date. */
export function conversationTimestamp(iso?: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (isToday(date)) return format(date, 'HH:mm');
  if (isYesterday(date)) return 'Yesterday';
  if (isThisWeek(date)) return format(date, 'EEE');
  return format(date, 'd MMM');
}

export function relativeTime(iso?: string | null): string {
  if (!iso) return '';
  return `${formatDistanceToNowStrict(new Date(iso))} ago`;
}

export function messageTimestamp(iso: string): string {
  return format(new Date(iso), 'HH:mm');
}

/** Day separator inside a thread. */
export function dayLabel(iso: string): string {
  const date = new Date(iso);
  if (isToday(date)) return 'Today';
  if (isYesterday(date)) return 'Yesterday';
  return format(date, 'd MMMM yyyy');
}

export function durationLabel(seconds?: number | null): string {
  if (!seconds && seconds !== 0) return '';
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, '0')}`;
}

export function heightLabel(cm?: number | null): string | null {
  if (!cm) return null;
  const totalInches = Math.round(cm / 2.54);
  const feet = Math.floor(totalInches / 12);
  const inches = totalInches % 12;
  return `${cm} cm (${feet}'${inches}")`;
}

export function priceLabel(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
}

export function compatibilityLabel(score?: number | null): string | null {
  if (score === undefined || score === null) return null;
  return `${Math.round(score * 100)}% match`;
}

/** Turns an enum constant into human copy: LONG_TERM_OPEN_TO_SHORT -> "Long term, open to short". */
export function humanise(value?: string | null): string {
  if (!value) return '';
  const words = value.toLowerCase().split('_');
  const [first = '', ...rest] = words;
  const head = first.charAt(0).toUpperCase() + first.slice(1);
  return [head, ...rest].join(' ');
}

export function initials(name?: string | null): string {
  if (!name) return '?';
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

/** "3 of 5 left today" for the comment allowance. */
export function quotaLabel(used: number, limit: number, unlimited: boolean, noun: string): string {
  if (unlimited) return `Unlimited ${noun}`;
  const remaining = Math.max(0, limit - used);
  return `${remaining} of ${limit} ${noun} left today`;
}
