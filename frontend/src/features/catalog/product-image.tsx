import { Package } from "lucide-react";
import { useNearViewport, useProductImage } from "@/features/catalog/product-image-url";
import type { Product } from "@/types/api";

export function ProductImage({
  product,
  large = false,
}: {
  product: Product;
  large?: boolean;
}) {
  const image = useProductImage(product.imageUrl, large ? 96 : 56);
  const viewport = useNearViewport<HTMLSpanElement>();
  return (
    <span
      ref={viewport.ref}
      className={`grid shrink-0 place-items-center overflow-hidden rounded-lg bg-white ${large ? "size-24" : "size-14"}`}
    >
      {image.src && viewport.near ? (
        <img
          src={image.src}
          alt=""
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          width={large ? 96 : 56}
          height={large ? 96 : 56}
          className="size-full object-contain"
          onError={image.onError}
        />
      ) : (
        <Package className="size-7 text-muted" aria-hidden />
      )}
    </span>
  );
}
