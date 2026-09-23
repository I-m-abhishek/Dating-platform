'use client';

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
} from 'react';

export interface DraggableOptions {
  /** Off: no positioning at all, the element lays out normally. */
  enabled: boolean;
  /** Where it appears each time it is enabled. */
  start?: 'top-right' | 'top-left';
  /**
   * Where it settles when let go: 'sides' keeps the height and moves to the nearer edge (a
   * floating call window); 'corners' parks it in the nearest corner (a self-view tile).
   */
  snap?: 'sides' | 'corners';
  margin?: number;
  /** Space kept clear at the top and bottom, e.g. for a header or a bottom nav bar. */
  insetTop?: number;
  insetBottom?: number;
}

interface Point {
  x: number;
  y: number;
}

/** Below this many pixels of movement a press is a tap, not a drag. */
const DRAG_THRESHOLD = 5;

/**
 * Makes a fixed-position element draggable with mouse or touch (pointer events), kept inside
 * the viewport and snapped to an edge or corner when released.
 *
 * <p>Presses that start on a button, link or input are left alone so those controls keep
 * working. A tap that is not a drag still produces a normal click; {@link consumeDrag} lets
 * a click handler ignore the click that ends a drag.
 */
export function useDraggable<T extends HTMLElement>({
  enabled,
  start = 'top-right',
  snap = 'sides',
  margin = 12,
  insetTop = 0,
  insetBottom = 0,
}: DraggableOptions) {
  const ref = useRef<T | null>(null);
  const [position, setPosition] = useState<Point | null>(null);
  const [dragging, setDragging] = useState(false);
  // No transition on the very first placement, or it would visibly fly in from 0,0.
  const [animate, setAnimate] = useState(false);
  const positionRef = useRef<Point | null>(null);
  positionRef.current = position;
  const gesture = useRef<{
    id: number;
    startX: number;
    startY: number;
    origin: Point;
    moved: boolean;
  } | null>(null);
  const justDragged = useRef(false);

  const limits = useCallback(() => {
    const element = ref.current;
    const width = element?.offsetWidth ?? 0;
    const height = element?.offsetHeight ?? 0;
    const minY = margin + insetTop;
    return {
      minX: margin,
      maxX: Math.max(margin, window.innerWidth - width - margin),
      minY,
      maxY: Math.max(minY, window.innerHeight - height - margin - insetBottom),
    };
  }, [margin, insetTop, insetBottom]);

  const clamp = useCallback(
    (point: Point): Point => {
      const bounds = limits();
      return {
        x: Math.min(Math.max(point.x, bounds.minX), bounds.maxX),
        y: Math.min(Math.max(point.y, bounds.minY), bounds.maxY),
      };
    },
    [limits],
  );

  const settle = useCallback(
    (point: Point): Point => {
      const bounds = limits();
      const inside = clamp(point);
      const x = inside.x - bounds.minX <= bounds.maxX - inside.x ? bounds.minX : bounds.maxX;
      if (snap === 'sides') return { x, y: inside.y };
      const y = inside.y - bounds.minY <= bounds.maxY - inside.y ? bounds.minY : bounds.maxY;
      return { x, y };
    },
    [clamp, limits, snap],
  );

  // Place it each time it is switched on; forget the position when switched off.
  useLayoutEffect(() => {
    if (!enabled) {
      setPosition(null);
      setAnimate(false);
      return;
    }
    const bounds = limits();
    setPosition({
      x: start === 'top-right' ? bounds.maxX : bounds.minX,
      y: bounds.minY,
    });
    const frame = requestAnimationFrame(() => setAnimate(true));
    return () => cancelAnimationFrame(frame);
  }, [enabled, limits, start]);

  // Stay on screen when the window is resized or the element itself changes size.
  useEffect(() => {
    if (!enabled) return;
    const refit = () => {
      if (!gesture.current) setPosition((current) => (current ? settle(current) : current));
    };
    window.addEventListener('resize', refit);
    const observer = new ResizeObserver(refit);
    if (ref.current) observer.observe(ref.current);
    return () => {
      window.removeEventListener('resize', refit);
      observer.disconnect();
    };
  }, [enabled, settle]);

  const onPointerDown = useCallback(
    (event: ReactPointerEvent<T>) => {
      justDragged.current = false;
      const origin = positionRef.current;
      if (!enabled || !origin) return;
      if ((event.target as HTMLElement).closest('button, a, input, textarea, select')) return;
      gesture.current = {
        id: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        origin,
        moved: false,
      };
      try {
        event.currentTarget.setPointerCapture(event.pointerId);
      } catch {
        // The pointer is already gone (released between events); the gesture still works
        // without capture, it just stops tracking if the pointer leaves the element.
      }
    },
    [enabled],
  );

  const onPointerMove = useCallback(
    (event: ReactPointerEvent<T>) => {
      const current = gesture.current;
      if (!current || current.id !== event.pointerId) return;
      const dx = event.clientX - current.startX;
      const dy = event.clientY - current.startY;
      if (!current.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
      if (!current.moved) {
        current.moved = true;
        setDragging(true);
      }
      setPosition(clamp({ x: current.origin.x + dx, y: current.origin.y + dy }));
    },
    [clamp],
  );

  const onPointerEnd = useCallback(
    (event: ReactPointerEvent<T>) => {
      const current = gesture.current;
      if (!current || current.id !== event.pointerId) return;
      gesture.current = null;
      if (current.moved) {
        justDragged.current = true;
        setDragging(false);
        setPosition((point) => (point ? settle(point) : point));
      }
    },
    [settle],
  );

  /** True if the click being handled is the end of a drag (and resets the flag). */
  const consumeDrag = useCallback(() => {
    const dragged = justDragged.current;
    justDragged.current = false;
    return dragged;
  }, []);

  let style: CSSProperties | undefined;
  if (enabled) {
    style = position
      ? {
          position: 'fixed',
          left: 0,
          top: 0,
          transform: `translate3d(${position.x}px, ${position.y}px, 0)`,
          transition:
            animate && !dragging ? 'transform 240ms cubic-bezier(0.2, 0.8, 0.2, 1)' : 'none',
          touchAction: 'none',
          cursor: dragging ? 'grabbing' : 'grab',
        }
      : // Measured before its first placement; keep it invisible for that one frame.
        { position: 'fixed', left: 0, top: 0, visibility: 'hidden' };
  }

  return {
    ref,
    style,
    dragging,
    consumeDrag,
    handlers: enabled
      ? {
          onPointerDown,
          onPointerMove,
          onPointerUp: onPointerEnd,
          onPointerCancel: onPointerEnd,
        }
      : {},
  };
}
