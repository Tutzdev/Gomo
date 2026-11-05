import { useEffect, useRef, type RefObject } from "react";

/** Time for the carousel to slow down to a stop, or speed back up. */
const SPEED_CHANGE_MS = 550;

/**
 * Moves a track that holds its content twice by half of its own width, forever, so the loop has no seam.
 * `paused` eases the speed to zero instead of freezing at once. The duration follows the width, so the speed in
 * pixels per second stays the same however many items there are; `contentKey` restarts it when they change.
 */
export function useMarquee(
  trackRef: RefObject<HTMLElement | null>,
  { pixelsPerSecond, paused, contentKey }: { pixelsPerSecond: number; paused: boolean; contentKey: string },
) {
  const animationRef = useRef<Animation | null>(null);

  useEffect(() => {
    const track = trackRef.current;
    if (!track) return;
    const animation = track.animate([{ transform: "translateX(0)" }, { transform: "translateX(-50%)" }], {
      duration: Math.max((track.scrollWidth / 2 / pixelsPerSecond) * 1000, 1000),
      iterations: Infinity,
    });
    animationRef.current = animation;
    return () => {
      animation.cancel();
      animationRef.current = null;
    };
  }, [trackRef, pixelsPerSecond, contentKey]);

  useEffect(() => {
    const animation = animationRef.current;
    if (!animation) return;
    return easePlaybackRate(animation, paused ? 0 : 1);
  }, [paused, contentKey]);
}

function easePlaybackRate(animation: Animation, target: number) {
  const initial = animation.playbackRate;
  const startedAt = performance.now();
  let frame = 0;

  const step = (now: number) => {
    const progress = Math.min((now - startedAt) / SPEED_CHANGE_MS, 1);
    const eased = 1 - (1 - progress) ** 3;
    animation.playbackRate = initial + (target - initial) * eased;
    if (progress < 1) frame = requestAnimationFrame(step);
  };

  frame = requestAnimationFrame(step);
  return () => cancelAnimationFrame(frame);
}
