/**
 * The distance filter's slider, and what its top position means.
 *
 * <p>The slider stops at 500 km because a linear scale beyond that is useless to drag - but
 * a filter that silently caps at 500 is wrong for anyone who genuinely wants to look
 * further. So the last stop is not "500 km", it is "500+ km": no upper bound.
 *
 * <p>{@link UNLIMITED_DISTANCE_KM} is the server's own ceiling - preferences are validated
 * as {@code @Min(1) @Max(20000)} - and 20,000 km is half the Earth's circumference, so it
 * excludes nothing but exact antipodes. Using the server's maximum rather than inventing a
 * sentinel keeps the value meaningful in the database: a row reading 20000 says "anywhere",
 * which is true, instead of a magic number that needs a lookup to interpret.
 */
export const DISTANCE_SLIDER_MIN = 1;
export const DISTANCE_SLIDER_MAX = 500;
export const UNLIMITED_DISTANCE_KM = 20000;

/** True when a stored preference means "no limit" rather than a real radius. */
export function isUnlimitedDistance(km: number | null | undefined): boolean {
  return km != null && km > DISTANCE_SLIDER_MAX;
}

/**
 * Stored kilometres to a slider position.
 *
 * <p>Anything above the slider's range pins to the top, so an account saved as "anywhere"
 * reopens showing 500+ rather than snapping back to a finite radius.
 */
export function distanceToSlider(km: number | null | undefined): number {
  if (km == null) return 80;
  if (km > DISTANCE_SLIDER_MAX) return DISTANCE_SLIDER_MAX;
  return Math.max(DISTANCE_SLIDER_MIN, Math.min(DISTANCE_SLIDER_MAX, km));
}

/** Slider position to the value sent to the server. */
export function sliderToDistance(position: number): number {
  return position >= DISTANCE_SLIDER_MAX ? UNLIMITED_DISTANCE_KM : position;
}

/** What to print beside the slider. */
export function distanceFilterLabel(position: number): string {
  return position >= DISTANCE_SLIDER_MAX ? `${DISTANCE_SLIDER_MAX}+ km` : `${position} km`;
}
