import { useQuery } from "@tanstack/react-query";
import { Check, ChevronLeft, ChevronRight, LoaderCircle, Package, Search, SlidersHorizontal, X } from "lucide-react";
import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from "react";
import { catalogApi, type ProductFilters } from "@/services/gomo-api";
import type { Product } from "@/types/api";
import { cn } from "@/lib/cn";
import { productDisplayName, productMetadata } from "./product-label";
import { HighlightedName } from "./highlighted-name";
import { ProductSearchFilters } from "./product-search-filters";

interface ProductPickerProps {
  id: string;
  value: string;
  onChange: (id: string) => void;
  excludedIds?: string[];
  onSearchChange?: (search: string) => void;
  cityId?: string;
  storeIds?: string[];
}

export function ProductPicker({ id, value, onChange, excludedIds = [], onSearchChange, cityId, storeIds }: ProductPickerProps) {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [selected, setSelected] = useState<Product | null>(null);
  const [filters, setFilters] = useState<ProductFilters>({});
  const [showFilters, setShowFilters] = useState(false);
  const [panel, setPanel] = useState({ height: 360, above: false });
  const input = useRef<HTMLInputElement>(null);
  const results = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  useEffect(() => {
    if (!value && selected) {
      setSelected(null);
      setSearch("");
      setQuery("");
      setPage(0);
      setOpen(false);
      input.current?.focus();
    }
  }, [value, selected]);
  useEffect(() => {
    results.current?.children[active]?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const products = useQuery({
    queryKey: ["products", "picker-groups", query, page, filters, cityId, storeIds],
    queryFn: ({ signal }) => catalogApi.discoverProducts({ ...filters, query, page, size: 8, cityId, storeIds }, signal),
    enabled: open && query.length >= 2 && !selected,
    retry: 1,
  });
  const waiting = search.trim() !== query || products.isFetching;
  const options = waiting ? [] : products.data?.content.map((group) => ({ ...group.product, name: group.displayName })) ?? [];
  const expanded = open && !selected && search.trim().length >= 2;
  const activeOption = options[active];
  const suggestions = [...new Set(options.map((product) => product.brand).filter((brand): brand is string => Boolean(brand)))].filter((brand) => brand.toLowerCase().includes(query.toLowerCase()) && brand.toLowerCase() !== query.toLowerCase()).slice(0, 3);

  useEffect(() => {
    if (!expanded) return;
    const position = () => {
      const bounds = input.current?.getBoundingClientRect();
      if (!bounds) return;
      const below = window.innerHeight - bounds.bottom - 48;
      const above = bounds.top - 116;
      const opensAbove = below < 200 && above > below;
      const height = Math.max(100, Math.min(440, opensAbove ? above : below));
      setPanel((previous) => previous.height === height && previous.above === opensAbove ? previous : { height, above: opensAbove });
    };
    position();
    window.addEventListener("resize", position);
    window.addEventListener("scroll", position, true);
    return () => { window.removeEventListener("resize", position); window.removeEventListener("scroll", position, true); };
  }, [expanded]);

  const edit = (text: string) => {
    setSelected(null);
    setSearch(text);
    setPage(0);
    setActive(-1);
    setOpen(true);
    setFilters({});
    onChange("");
    onSearchChange?.(text);
  };
  const choose = (product: Product) => {
    if (excludedIds.includes(product.id)) return;
    setSelected(product);
    setSearch(product.name);
    setOpen(false);
    setActive(-1);
    onChange(product.id);
  };
  const move = (direction: number) => {
    if (!options.length) return;
    let next = active < 0 && direction < 0 ? 0 : active;
    for (let attempt = 0; attempt < options.length; attempt++) {
      next = (next + direction + options.length) % options.length;
      if (!excludedIds.includes(options[next].id)) { setActive(next); return; }
    }
  };
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
      move(event.key === "ArrowDown" ? 1 : -1);
    } else if (event.key === "Enter" && !selected) {
      event.preventDefault();
      if (activeOption) choose(activeOption);
    } else if (event.key === "Escape") {
      event.preventDefault();
      setOpen(false);
      setActive(-1);
    }
  };

  return <div className="relative min-w-0" onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false); }}>
    <label htmlFor={`${id}-search`} className="mb-2 block text-sm font-semibold">Buscar produto</label>
    <div className="relative">
      {selected ? <Check className="pointer-events-none absolute left-3 top-3.5 size-5 text-success" aria-hidden /> : <Search className="pointer-events-none absolute left-3 top-3.5 size-5 text-muted" aria-hidden />}
      <input ref={input} id={`${id}-search`} role="combobox" type="text" autoComplete="off" spellCheck={false}
        className="field-control product-search-input" placeholder="Ex.: coca cola 1 litro" maxLength={200}
        aria-expanded={expanded} aria-autocomplete="list" aria-controls={expanded ? `${id}-results` : undefined}
        aria-activedescendant={expanded && activeOption ? `${id}-option-${activeOption.id}` : undefined}
        aria-describedby={`${id}-status`} value={search} onKeyDown={onKeyDown}
        onFocus={() => setOpen(true)} onChange={(event) => edit(event.target.value)} />
      {search ? <button type="button" className="icon-button absolute right-1 top-0.5" aria-label="Limpar produto" onClick={() => { edit(""); input.current?.focus(); }}><X className="size-4" aria-hidden /></button> : null}
    </div>
    {selected ? <p className="mt-2 text-xs text-muted">{productMetadata(selected)}</p> : null}
    <p id={`${id}-status`} role="status" className="mt-2 text-xs text-muted">
      {selected ? "Produto selecionado" : !expanded ? "Pesquise um produto pelo nome, marca ou tamanho." : waiting ? "Buscando produtos…" : products.isError ? "Busca indisponível no momento." : `${products.data?.totalElements.toLocaleString("pt-BR") ?? 0} produtos encontrados`}
    </p>
    {expanded ? <div style={{ "--search-height": `${panel.height}px` } as CSSProperties} className={cn("mt-2 overflow-hidden rounded-lg border border-border bg-white lg:absolute lg:inset-x-0 lg:z-30 lg:flex lg:max-h-[var(--search-height)] lg:flex-col lg:shadow-lg", panel.above ? "lg:bottom-full lg:mb-2" : "lg:top-full")}>
      <div className="flex shrink-0 items-center justify-between gap-2 border-b border-border px-3 py-2">
        <p className="text-xs font-semibold text-muted">{Object.keys(filters).some((key) => filters[key as keyof ProductFilters]) ? "Resultados filtrados" : "Resultados do catálogo"}</p>
        <button type="button" aria-expanded={showFilters} aria-controls={`${id}-filters`} className="flex min-h-9 items-center gap-1 rounded-md px-2 text-xs font-semibold hover:bg-surface-strong focus-visible:outline-2 focus-visible:outline-focus" onClick={() => setShowFilters(!showFilters)}><SlidersHorizontal className="size-3.5" aria-hidden />Filtros</button>
      </div>
      {showFilters ? <div id={`${id}-filters`} className="max-h-[28dvh] shrink-0 overflow-y-auto"><ProductSearchFilters id={id} query={query} value={filters} onChange={(next) => { setFilters(next); setPage(0); setActive(-1); }} /></div> : null}
      {waiting ? <div className="flex items-center gap-2 p-5 text-sm text-muted"><LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />Buscando…</div> : products.isError ? <div className="p-4 text-sm" role="alert"><p>Não foi possível carregar os produtos.</p><button type="button" className="mt-2 min-h-10 font-semibold text-primary underline" onClick={() => void products.refetch()}>Tentar novamente</button></div> : !options.length ? <div className="p-4 text-sm"><p>Não encontramos produtos para “{query}”.</p><p className="mt-1 text-muted">Tente um nome mais curto ou remova os filtros.</p></div> : null}
      {!waiting && suggestions.length && !Object.keys(filters).length ? <div className="flex flex-wrap items-center gap-2 px-3 py-2"><span className="text-xs text-muted">Marcas:</span>{suggestions.map((brand) => <button type="button" key={brand} className="min-h-9 rounded-md bg-surface-strong px-2 text-xs hover:bg-primary-soft focus-visible:outline-2 focus-visible:outline-focus" onClick={() => { setFilters({ brand }); setPage(0); setActive(-1); }}>{brand}</button>)}</div> : null}
      <ul ref={results} id={`${id}-results`} role="listbox" aria-label="Produtos encontrados" aria-busy={waiting} className="min-h-0 max-h-[min(22rem,42dvh)] overflow-y-auto overscroll-contain lg:flex-1">
        {options.map((product, index) => {
          const excluded = excludedIds.includes(product.id);
          return <li key={product.id} id={`${id}-option-${product.id}`} role="option" aria-selected={active === index} aria-disabled={excluded}
            className={cn("flex min-h-18 cursor-pointer items-center gap-3 border-t border-border px-3 py-3 first:border-t-0", active === index ? "bg-primary-soft" : "hover:bg-surface-strong", excluded && "cursor-default opacity-60")}
            onMouseDown={(event) => event.preventDefault()} onClick={() => choose(product)}>
            <span className="grid size-9 shrink-0 place-items-center rounded-md bg-surface-strong text-muted"><Package className="size-4" aria-hidden /></span>
            <span className="min-w-0 flex-1"><span className="block text-sm font-semibold leading-5"><HighlightedName name={productDisplayName(product)} query={query} /></span><span className="mt-1 block text-xs text-muted">{productMetadata(product)}</span></span>
            {excluded ? <span className="text-xs text-muted">Na lista</span> : null}
          </li>;
        })}
      </ul>
      {!waiting && products.data && products.data.totalPages > 1 ? <div className="flex shrink-0 items-center justify-between border-t border-border px-2 py-1">
        <button type="button" className="icon-button" disabled={page === 0} aria-label="Resultados anteriores" onClick={() => { setPage(page - 1); setActive(-1); input.current?.focus(); }}><ChevronLeft className="size-4" aria-hidden /></button>
        <span className="text-xs text-muted">{page + 1} de {products.data.totalPages}</span>
        <button type="button" className="icon-button" disabled={page + 1 >= products.data.totalPages} aria-label="Próximos resultados" onClick={() => { setPage(page + 1); setActive(-1); input.current?.focus(); }}><ChevronRight className="size-4" aria-hidden /></button>
      </div> : null}
    </div> : null}
  </div>;
}
