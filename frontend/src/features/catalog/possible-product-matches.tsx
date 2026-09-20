import { Link } from "react-router-dom";
import type { Product } from "@/types/api";
import { productMetadata } from "./product-label";
import { queryString } from "@/lib/api";

export function PossibleProductMatches({ products, cityId, storeIds = [] }: { products: Product[]; cityId?: string; storeIds?: string[] }) {
  if (!products.length) return null;
  return (
    <section
      className="surface mt-5 p-5"
      aria-label="Correspondências não confirmadas"
    >
      <h3 className="font-bold">Confira a variante e a embalagem</h3>
      <p className="mt-2 text-sm text-muted">
        Estes cadastros têm informações insuficientes para confirmar que são o
        mesmo produto. Seus preços não entram na comparação acima. Abra um deles
        para conferir suas ofertas.
      </p>
      <details className="mt-3">
        <summary className="cursor-pointer py-2 text-sm font-semibold">
          Ver {products.length} possível(is) correspondência(s)
        </summary>
        <ul className="divide-y divide-border">
          {products.map((product) => (
            <li key={product.id} className="py-3">
              <Link
                className="font-semibold text-primary underline"
                to={`/app/produtos/${product.id}?${queryString({ cityId, storeIds: storeIds.join(",") })}`}
              >
                {product.name}
              </Link>
              <p className="mt-1 text-xs text-muted">
                {productMetadata(product)}
              </p>
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
