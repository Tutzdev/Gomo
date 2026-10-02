import { useQuery } from "@tanstack/react-query";
import { Check, LoaderCircle, Package, Search, Store, X } from "lucide-react";
import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem } from "@/types/api";
import { HighlightedName } from "./highlighted-name";

interface CatalogItemPickerProps {
  id: string;
  value: CatalogItem | null;
  onChange: (item: CatalogItem | null) => void;
  cityId?: string;
  storeIds?: string[];
  /** Catalog item IDs already chosen, shown as unavailable. */
  excludedIds?: string[];
  label?: string;
}

/**
 * Search for a product by its everyday name ("coca 2 litros", "monster") and choose the generic item,
 * not one store's listing. Each result says in how many markets it is sold and its lowest current price.
 */
export function CatalogItemPicker({
  id,
  value,
  onChange,
  cityId,
  storeIds,
  excludedIds = [],
  label = "Buscar produto",
}: CatalogItemPickerProps) {
  const [search, setSearch] = useState(value?.name ?? "");
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const input = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);
  // The parent clears the value after adding the item: reset the field for the next product.
  useEffect(() => {
    if (!value) setSearch((current) => (open ? current : ""));
  }, [value, open]);
  useEffect(() => {
    list.current?.children[active]?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const results = useQuery({
    queryKey: ["catalog-items", query, cityId, storeIds],
    queryFn: ({ signal }) => catalogApi.catalogItems({ query, cityId, storeIds, size: 12 }, signal),
    enabled: open && !value && query.length >= 2,
    placeholderData: (previous) => previous,
    retry: 1,
  });
  const expanded = open && !value && search.trim().length >= 2;
  const waiting = search.trim() !== query || (results.isFetching && !results.data);
  const options = results.data?.content ?? [];
  const isExcluded = (item: CatalogItem) => excludedIds.includes(item.id);

  const choose = (item: CatalogItem) => {
    if (isExcluded(item)) return;
    setSearch(item.name);
    setOpen(false);
    setActive(-1);
    onChange(item);
  };
  const edit = (text: string) => {
    setSearch(text);
    setOpen(true);
    setActive(-1);
    if (value) onChange(null);
  };
  const move = (direction: number) => {
    if (!options.length) return;
    let next = active;
    for (let attempt = 0; attempt < options.length; attempt++) {
      next = (next + direction + options.length) % options.length;
      if (!isExcluded(options[next])) {
        setActive(next);
        return;
      }
    }
  };
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
      move(event.key === "ArrowDown" ? 1 : -1);
    } else if (event.key === "Enter" && expanded) {
      event.preventDefault();
      const option = options[active] ?? options.find((item) => !isExcluded(item));
      if (option) choose(option);
    } else if (event.key === "Escape") {
      setOpen(false);
      setActive(-1);
    }
  };

  const status = value
    ? `${value.name} selecionado`
    : !expanded
      ? "Digite o nome do produto, como você falaria no mercado."
      : waiting
        ? "Buscando…"
        : results.isError
          ? "Busca indisponível no momento."
          : `${results.data?.totalElements.toLocaleString("pt-BR") ?? 0} produtos encontrados`;

  return (
    <div
      className="relative min-w-0"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
      }}
    >
      <label htmlFor={`${id}-search`} className="mb-2 block text-sm font-semibold">
        {label}
      </label>
      <div className="relative">
        {value ? (
          <Check className="pointer-events-none absolute left-3 top-3.5 size-5 text-success" aria-hidden />
        ) : (
          <Search className="pointer-events-none absolute left-3 top-3.5 size-5 text-muted" aria-hidden />
        )}
        <input
          ref={input}
          id={`${id}-search`}
          role="combobox"
          type="text"
          autoComplete="off"
          spellCheck={false}
          className="field-control product-search-input"
          placeholder="Ex.: coca cola 2 litros, monster, leite ninho"
          maxLength={120}
          aria-expanded={expanded}
          aria-autocomplete="list"
          aria-controls={expanded ? `${id}-results` : undefined}
          aria-activedescendant={expanded && options[active] ? `${id}-option-${options[active].id}` : undefined}
          aria-describedby={`${id}-status`}
          value={search}
          onKeyDown={onKeyDown}
          onFocus={() => setOpen(true)}
          onChange={(event) => edit(event.target.value)}
        />
        {search ? (
          <button
            type="button"
            className="icon-button absolute right-1 top-0.5"
            aria-label="Limpar busca"
            onClick={() => {
              edit("");
              input.current?.focus();
            }}
          >
            <X className="size-4" aria-hidden />
          </button>
        ) : null}
      </div>
      <p id={`${id}-status`} role="status" className="mt-2 text-xs text-muted">
        {status}
      </p>

      {expanded ? (
        <div className="mt-2 overflow-hidden rounded-lg border border-border bg-white lg:absolute lg:inset-x-0 lg:top-full lg:z-30 lg:shadow-lg">
          {waiting && !options.length ? (
            <p className="flex items-center gap-2 p-5 text-sm text-muted">
              <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />
              Buscando…
            </p>
          ) : results.isError ? (
            <div className="p-4 text-sm" role="alert">
              <p>Não foi possível carregar os produtos.</p>
              <button
                type="button"
                className="mt-2 min-h-10 font-semibold text-primary underline"
                onClick={() => void results.refetch()}
              >
                Tentar novamente
              </button>
            </div>
          ) : !options.length ? (
            <div className="p-4 text-sm">
              <p>Nenhum produto com preço atual para “{query}”.</p>
              <p className="mt-1 text-muted">
                Tente só a marca, ou a marca e o tamanho (ex.: “ninho 380g”). Se os preços estiverem sendo
                atualizados, volte em alguns minutos.
              </p>
            </div>
          ) : null}
          <ul
            ref={list}
            id={`${id}-results`}
            role="listbox"
            aria-label="Produtos encontrados"
            className={cn("max-h-[min(24rem,45dvh)] overflow-y-auto overscroll-contain", waiting && "opacity-60")}
          >
            {options.map((item, index) => (
              <CatalogItemOption
                key={item.id}
                id={`${id}-option-${item.id}`}
                item={item}
                query={query}
                active={index === active}
                excluded={isExcluded(item)}
                onChoose={() => choose(item)}
              />
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

function CatalogItemOption({
  id,
  item,
  query,
  active,
  excluded,
  onChoose,
}: {
  id: string;
  item: CatalogItem;
  query: string;
  active: boolean;
  excluded: boolean;
  onChoose: () => void;
}) {
  return (
    <li
      id={id}
      role="option"
      aria-selected={active}
      aria-disabled={excluded}
      className={cn(
        "flex min-h-18 cursor-pointer items-center gap-3 border-t border-border px-3 py-2.5 first:border-t-0",
        active ? "bg-primary-soft" : "hover:bg-surface-strong",
        excluded && "cursor-default opacity-60",
      )}
      onMouseDown={(event) => event.preventDefault()}
      onClick={onChoose}
    >
      <CatalogItemThumbnail imageUrl={item.imageUrl} />
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold leading-5">
          <HighlightedName name={item.name} query={query} />
        </span>
        <span className="mt-1 flex items-center gap-1 text-xs text-muted">
          <Store className="size-3.5 shrink-0" aria-hidden />
          {item.pricedStores === 1 ? "Preço atual em 1 mercado" : `Preço atual em ${item.pricedStores} mercados`}
        </span>
      </span>
      <span className="shrink-0 text-right">
        {excluded ? (
          <span className="text-xs text-muted">Na lista</span>
        ) : item.lowestPrice !== null ? (
          <>
            <span className="block text-[0.7rem] text-muted">a partir de</span>
            <span className="block text-sm font-bold tabular-nums">{formatCurrency(item.lowestPrice)}</span>
          </>
        ) : (
          <span className="text-xs text-muted">Sem preço atual</span>
        )}
      </span>
    </li>
  );
}

export function CatalogItemThumbnail({ imageUrl, size = "md" }: { imageUrl: string | null; size?: "sm" | "md" }) {
  const [failed, setFailed] = useState(false);
  return (
    <span
      className={cn(
        "grid shrink-0 place-items-center overflow-hidden rounded-md bg-white ring-1 ring-border",
        size === "sm" ? "size-10" : "size-12",
      )}
    >
      {imageUrl && !failed ? (
        <img
          src={imageUrl}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          width={48}
          height={48}
          className="size-full object-contain p-0.5"
          onError={() => setFailed(true)}
        />
      ) : (
        <Package className="size-5 text-muted" aria-hidden />
      )}
    </span>
  );
}
