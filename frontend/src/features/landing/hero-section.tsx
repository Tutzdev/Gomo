import { ArrowRight, Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { NativeButton } from "@/components/ui/native-button";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { HeroShowcase } from "./hero-showcase";
import { useHeroComparison, type HeroProduct } from "./use-hero-comparison";
import "./hero.css";

/*
 * A hero lê como uma frase do visitante: "Quero comprar [produto] e pagar [o menor preço], não [o maior]".
 * O produto é trocável e os preços são os de hoje na cidade; embaixo, o painel do Gomo com a mesma comparação.
 */
export function HeroSection({ signUpPath }: { signUpPath: string }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const comparison = useHeroComparison(selectedId);

  return (
    <section className="hero landing-wide" aria-labelledby="hero-title">
      <div className="hero__panel">
        <div className="hero__copy">
          <h1 id="hero-title" className="hero__title">
            Quero comprar{" "}
            <ProductPicker
              products={comparison.products}
              current={comparison.product}
              onSelect={setSelectedId}
            />{" "}
            e pagar{" "}
            {/* A vírgula anda junto com a pílula, para nunca começar uma linha sozinha. */}
            <span className="whitespace-nowrap">
              <span className="hero__pill hero__pill--price">{formatCurrency(comparison.lowest)}</span>,
            </span>{" "}
            <span className="hero__was">
              não <s>{formatCurrency(comparison.highest)}</s>
            </span>
            .
          </h1>

          <p className="hero__lead">
            Compare os supermercados da sua cidade e descubra onde a sua compra sai mais barata, antes de sair de casa.
          </p>

          <div className="hero__actions">
            <NativeButton to={signUpPath} size="lg" className="landing-button hero__cta">
              Comparar preços grátis
              <ArrowRight className="hero__cta-arrow size-[1.125rem]" aria-hidden />
            </NativeButton>
            <p className="hero__note">Sem cartão de crédito. Você cria a conta e já compara.</p>
          </div>
        </div>

        <HeroShowcase comparison={comparison} />
      </div>
    </section>
  );
}

interface ProductPickerProps {
  products: HeroProduct[];
  current: HeroProduct;
  onSelect: (id: string) => void;
}

/**
 * The product pill in the headline. A button opens a small listbox; while it is closed the heading reads as
 * plain text ("Quero comprar Coca-Cola 2 L e pagar…"), so search engines and screen readers get a clean title.
 */
function ProductPicker({ products, current, onSelect }: ProductPickerProps) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const wrapperRef = useRef<HTMLSpanElement>(null);
  const listId = useId();
  const canPick = products.length > 1;

  useEffect(() => {
    if (!open) return;
    listRef.current?.focus();
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [open]);

  if (!canPick) {
    return <span className="hero__pill hero__pill--product">{current.name}</span>;
  }

  const openList = () => {
    setActive(Math.max(0, products.findIndex((product) => product.id === current.id)));
    setOpen(true);
  };

  const choose = (index: number) => {
    onSelect(products[index].id);
    setOpen(false);
    buttonRef.current?.focus();
  };

  const onListKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    if (event.key === "ArrowDown") setActive((index) => (index + 1) % products.length);
    else if (event.key === "ArrowUp") setActive((index) => (index - 1 + products.length) % products.length);
    else if (event.key === "Home") setActive(0);
    else if (event.key === "End") setActive(products.length - 1);
    else if (event.key === "Enter" || event.key === " ") choose(active);
    else if (event.key === "Escape") {
      setOpen(false);
      buttonRef.current?.focus();
    } else if (event.key === "Tab") setOpen(false);
    else return;
    if (event.key !== "Tab") event.preventDefault();
  };

  return (
    <span ref={wrapperRef} className="hero__picker">
      <button
        ref={buttonRef}
        type="button"
        className="hero__pill hero__pill--product hero__pill--button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-label={`${current.name}. Trocar o produto do exemplo`}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            openList();
          }
        }}
      >
        <span className="hero__pill-label">{current.name}</span>
        <ChevronDown className="hero__pill-chevron" aria-hidden />
      </button>

      {open ? (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          tabIndex={-1}
          aria-label="Produtos com a maior diferença de preço hoje"
          aria-activedescendant={`${listId}-${active}`}
          className="hero__options"
          onKeyDown={onListKeyDown}
        >
          {products.map((product, index) => (
            <li
              key={product.id}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={product.id === current.id}
              className={cn("hero__option", index === active && "is-active")}
              onPointerEnter={() => setActive(index)}
              onClick={() => choose(index)}
            >
              <span>{product.name}</span>
              {product.id === current.id ? <Check className="size-4 shrink-0" aria-hidden /> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </span>
  );
}
