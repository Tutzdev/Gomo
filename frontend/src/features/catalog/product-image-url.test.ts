import { describe, expect, it } from "vitest";
import { productImageUrl } from "./product-image-url";

describe("productImageUrl", () => {
  it("asks a VTEX store for the photo at twice the size on screen", () => {
    expect(productImageUrl("https://hortifrutibr.vtexassets.com/arquivos/ids/164940/Guarana.png?v=1", 48)).toBe(
      "https://hortifrutibr.vtexassets.com/arquivos/ids/164940-96-96/Guarana.png?v=1",
    );
  });

  it("replaces a size the store already put in the address", () => {
    expect(productImageUrl("https://loja.vteximg.com.br/arquivos/ids/77-500-500/x.jpg", 56)).toBe(
      "https://loja.vteximg.com.br/arquivos/ids/77-112-112/x.jpg",
    );
  });

  it("keeps the original when the host has no resizer", () => {
    const url = "https://phygital-files.mercafacil.com/catalogo/uploads/produto/gatorade.jpg";
    expect(productImageUrl(url, 48)).toBe(url);
  });
});
