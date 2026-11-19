-- Itens básicos: "pão de hambúrguer" de qualquer marca, "acém" de qualquer açougue. O catálogo agrupa só o
-- mesmo produto exato (nome + marca + tamanho), então o pão de hambúrguer aparecia em 3 dos 9 mercados de
-- Volta Redonda, embora os 9 vendam algum. Esta tabela liga cada produto com preço ao item básico que ele é,
-- com o tamanho da embalagem na unidade do item (kg, L ou un), para comparar o preço por kg entre marcas.
-- É refeita depois de cada coleta, como catalog_item_products.
CREATE TABLE basic_item_products (
    product_id UUID PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    basic_item VARCHAR(64) NOT NULL,
    package_amount NUMERIC(12, 4)
);

CREATE INDEX idx_basic_item_products_item ON basic_item_products (basic_item);

-- Um item da lista pode pedir "qualquer marca": a comparação usa, em cada mercado, a embalagem mais barata por
-- kg (ou L, ou unidade) daquele item básico, em vez de exigir o produto exato.
ALTER TABLE shopping_list_items ADD COLUMN basic_item VARCHAR(64);

CREATE UNIQUE INDEX uq_shopping_list_items_basic_item
    ON shopping_list_items (shopping_list_id, basic_item) WHERE basic_item IS NOT NULL;
