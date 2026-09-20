import { useQuery } from "@tanstack/react-query";
import { SelectField } from "@/components/ui/form-field";
import { catalogApi, type ProductFilters } from "@/services/gomo-api";
import { formatMeasurement } from "./product-label";

export function ProductSearchFilters({
  id,
  query,
  value,
  onChange,
}: {
  id: string;
  query: string;
  value: ProductFilters;
  onChange: (filters: ProductFilters) => void;
}) {
  const facets = useQuery({
    queryKey: ["product-filters", query],
    queryFn: ({ signal }) => catalogApi.productFilters(query, signal),
  });
  const change = (key: keyof ProductFilters, selected: string) =>
    onChange({ ...value, [key]: selected || undefined });
  return (
    <div className="space-y-3 border-t border-border p-3">
      {facets.isError ? (
        <p className="text-sm text-danger" role="alert">
          Não foi possível carregar os filtros.{" "}
          <button
            type="button"
            className="underline"
            onClick={() => void facets.refetch()}
          >
            Tentar novamente
          </button>
        </p>
      ) : null}
      <div className="grid grid-cols-2 gap-3">
        <SelectField
          id={`${id}-sort`}
          label="Ordenar por"
          value={value.sort ?? "relevance"}
          onChange={(event) => change("sort", event.target.value)}
        >
          <option value="relevance">Mais relevantes</option>
          <option value="name">A–Z</option>
          <option value="price_asc">Menor preço atual</option>
          <option value="price_desc">Maior preço atual</option>
        </SelectField>
        <SelectField
          id={`${id}-market`}
          label="Catálogo de origem"
          value={value.storeId ?? ""}
          disabled={facets.isPending}
          onChange={(event) => change("storeId", event.target.value)}
        >
          <option value="">Todos</option>
          {facets.data?.markets.map((market) => (
            <option key={market.id} value={market.id}>
              {market.name}
            </option>
          ))}
        </SelectField>
        <SelectField
          id={`${id}-brand`}
          label="Marca"
          value={value.brand ?? ""}
          disabled={facets.isPending}
          onChange={(event) => change("brand", event.target.value)}
        >
          <option value="">Todas</option>
          {facets.data?.brands.map((brand) => (
            <option key={brand} value={brand}>
              {brand}
            </option>
          ))}
        </SelectField>
        <SelectField
          id={`${id}-category`}
          label="Categoria"
          value={value.category ?? ""}
          disabled={facets.isPending}
          onChange={(event) => change("category", event.target.value)}
        >
          <option value="">Todas</option>
          {facets.data?.categories.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </SelectField>
        <SelectField
          id={`${id}-size`}
          label="Tamanho"
          value={
            value.unit && value.quantity
              ? `${value.unit}:${value.quantity}`
              : ""
          }
          disabled={facets.isPending}
          onChange={(event) => {
            const [unit, quantity] = event.target.value.split(":");
            onChange({
              ...value,
              unit: unit || undefined,
              quantity: quantity ? Number(quantity) : undefined,
            });
          }}
        >
          <option value="">Todos</option>
          {facets.data?.measurements.map((size) => (
            <option
              key={`${size.unit}:${size.quantity}`}
              value={`${size.unit}:${size.quantity}`}
            >
              {formatMeasurement(size.quantity, size.unit)}
            </option>
          ))}
        </SelectField>
        <button
          type="button"
          className="self-end rounded-md px-3 py-3 text-sm font-semibold text-primary focus-visible:outline-2 focus-visible:outline-focus"
          onClick={() => onChange({})}
        >
          Limpar filtros
        </button>
      </div>
      {value.sort?.startsWith("price") ? (
        <p className="text-xs text-muted">
          Ordenação pelo menor preço atual por produto nas lojas consultadas.
          Sem preço fica ao final.
        </p>
      ) : null}
    </div>
  );
}
