import { Pause, Play } from "lucide-react";
import { useEffect, useRef, useState, type PointerEvent, type RefObject } from "react";
import { usePrefersReducedMotion } from "./motion-hooks";
import { TESTIMONIALS_ARE_EXAMPLES, testimonialRows, type Testimonial } from "./testimonials";

/** Calmo o bastante para ler um card enquanto ele passa. */
const PIXELS_PER_SECOND = 30;
/** Tempo para o carrossel desacelerar até parar, ou voltar à velocidade normal. */
const SPEED_CHANGE_MS = 550;

export function TestimonialsSection() {
  const reducedMotion = usePrefersReducedMotion();
  const [hovered, setHovered] = useState(false);
  const [stopped, setStopped] = useState(false);
  const animated = !reducedMotion;
  const paused = hovered || stopped;

  function handlePointerEnter(event: PointerEvent) {
    if (event.pointerType === "mouse") setHovered(true);
  }

  return (
    <section className="landing-band" aria-labelledby="testimonials-title">
      <div className="landing-frame">
        <div className="testimonials-head">
          <h2 id="testimonials-title" className="landing-heading__title">Quem compara, conta.</h2>
          {animated ? (
            <button
              type="button"
              className="testimonials-toggle"
              aria-label={stopped ? "Continuar depoimentos" : "Pausar depoimentos"}
              onClick={() => setStopped((current) => !current)}
            >
              {stopped ? <Play className="size-3.5" aria-hidden /> : <Pause className="size-3.5" aria-hidden />}
              {stopped ? "Continuar" : "Pausar"}
            </button>
          ) : null}
        </div>

        {animated ? (
          <div className="testimonials" onPointerEnter={handlePointerEnter} onPointerLeave={() => setHovered(false)}>
            {testimonialRows.map((row, index) => (
              <MarqueeRow key={index} testimonials={row} reverse={index % 2 === 1} paused={paused} />
            ))}
          </div>
        ) : (
          // Sem movimento, os depoimentos viram uma grade parada.
          <ul className="testimonials-grid">
            {testimonialRows.flat().map((testimonial, index) => (
              <li key={testimonial.name}>
                <TestimonialCard testimonial={testimonial} tone={index % 4} />
              </li>
            ))}
          </ul>
        )}

        {TESTIMONIALS_ARE_EXAMPLES ? <p className="testimonials-note">Depoimentos ilustrativos.</p> : null}
      </div>
    </section>
  );
}

interface MarqueeRowProps {
  testimonials: Testimonial[];
  reverse: boolean;
  paused: boolean;
}

/* A fileira é repetida uma vez e anda metade da própria largura, para o laço não ter emenda. */
function MarqueeRow({ testimonials, reverse, paused }: MarqueeRowProps) {
  const trackRef = useRef<HTMLDivElement>(null);
  useMarquee(trackRef, { reverse, paused });

  const cards = testimonials.map((testimonial, index) => (
    <li key={testimonial.name}>
      <TestimonialCard testimonial={testimonial} tone={index % 4} />
    </li>
  ));

  return (
    <div className="marquee">
      <div ref={trackRef} className="marquee__track">
        <ul className="marquee__group">{cards}</ul>
        <ul className="marquee__group" aria-hidden>{cards}</ul>
      </div>
    </div>
  );
}

function TestimonialCard({ testimonial, tone }: { testimonial: Testimonial; tone: number }) {
  return (
    <figure className="testimonial">
      <figcaption className="testimonial__author">
        <span className="testimonial__avatar" data-tone={tone} aria-hidden>
          {initials(testimonial.name)}
        </span>
        <span className="min-w-0">
          <strong>{testimonial.name}</strong>
          <span>{testimonial.context}</span>
        </span>
      </figcaption>
      <blockquote className="testimonial__text">
        <p>{testimonial.text}</p>
      </blockquote>
    </figure>
  );
}

function useMarquee(trackRef: RefObject<HTMLDivElement | null>, { reverse, paused }: { reverse: boolean; paused: boolean }) {
  const animationRef = useRef<Animation | null>(null);

  useEffect(() => {
    const track = trackRef.current;
    if (!track) return;

    const keyframes = [{ transform: "translateX(0)" }, { transform: "translateX(-50%)" }];
    const animation = track.animate(reverse ? keyframes.reverse() : keyframes, {
      duration: (track.scrollWidth / 2 / PIXELS_PER_SECOND) * 1000,
      iterations: Infinity,
    });
    animationRef.current = animation;
    return () => {
      animation.cancel();
      animationRef.current = null;
    };
  }, [trackRef, reverse]);

  useEffect(() => {
    const animation = animationRef.current;
    if (!animation) return;
    return easePlaybackRate(animation, paused ? 0 : 1);
  }, [paused, reverse]);
}

/* Desacelera até parar (ou volta a andar) aos poucos, em vez de congelar de uma vez sob o cursor. */
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

function initials(name: string) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}
