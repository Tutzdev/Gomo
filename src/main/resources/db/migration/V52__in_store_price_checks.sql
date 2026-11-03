-- Markets without an online store enter with prices written down at the shelf by the Gomo team.
-- Such a price is its own origin (STORE_VISIT): it stays current longer than a scraped one
-- (app.prices.in-store-max-age) because nobody can revisit a shelf four times a day.

ALTER TABLE stores
    ADD COLUMN price_channel VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
    ADD CONSTRAINT ck_stores_price_channel CHECK (price_channel IN ('ONLINE', 'IN_STORE'));

ALTER TABLE price_records
    DROP CONSTRAINT ck_price_origin_type,
    DROP CONSTRAINT ck_price_origin_reference,
    ADD CONSTRAINT ck_price_origin_type CHECK (origin_type IN ('SOURCE', 'USER_CONTRIBUTION', 'STORE_VISIT')),
    ADD CONSTRAINT ck_price_origin_reference CHECK (
        (origin_type IN ('SOURCE', 'STORE_VISIT') AND contribution_id IS NULL)
        OR (origin_type = 'USER_CONTRIBUTION' AND contribution_id IS NOT NULL)
    );

INSERT INTO data_sources (id, code, name, base_url, enabled, verified_at, created_at)
VALUES ('7d0f3c2e-5b8a-4f61-9c3d-2a6e8b1f4c70', 'in_store_price_check',
        'Preço conferido na loja física', 'https://github.com/Tutzdev/Gomo/blob/main/docs/data-sources.md', true,
        '2026-11-03T12:00:00Z', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- Chains: reuse the ones already registered (Supermarket since V25, Royal by its collector), create the rest.
INSERT INTO supermarket_chains (id, name, source_id, source_reference, collected_at, updated_at)
SELECT chain.id::uuid, chain.name, source.id, chain.reference, '2026-11-03T12:00:00Z', CURRENT_TIMESTAMP
FROM data_sources source
CROSS JOIN (VALUES
    ('6518d725-f7b6-4b47-b86b-ed2af0b3b5d0', 'Supermarket', 'https://redesupermarket.com.br/nossas-lojas/', 'supermarket'),
    ('3c1b7e4a-8d2f-4a90-b6e5-1f7c9a2d3e81', 'Royal Supermercados', 'https://www.royalsupermercados.com.br', 'royal'),
    ('a4e2d9f1-6c3b-4e87-9a15-7b2c8d0e5f92', 'MAX Supermercado', 'in-store:chain:max-supermercado-barra-mansa', 'max supermercado'),
    ('b5f3e0a2-7d4c-4f98-8b26-8c3d9e1f6a03', 'Mercado Pingo de Mel', 'in-store:chain:pingo-de-mel-barra-mansa', 'mercado pingo de mel'),
    ('c6a4f1b3-8e5d-4a09-9c37-9d4e0f2a7b14', 'Mercado Bom Jesus', 'in-store:chain:bom-jesus-barra-mansa', 'mercado bom jesus')
) AS chain(id, name, reference, prefix)
WHERE source.code = 'in_store_price_check'
  AND NOT EXISTS (SELECT 1 FROM supermarket_chains existing WHERE existing.id = chain.id::uuid)
  AND NOT EXISTS (SELECT 1 FROM supermarket_chains existing WHERE lower(existing.name) LIKE chain.prefix || '%');

-- Barra Mansa markets found on 2026-11-03 (docs/data-sources.md): physical stores, no online catalog.
INSERT INTO stores (id, supermarket_chain_id, city_id, name, address, active, source_id, source_reference,
                    collected_at, updated_at, price_source_note, price_channel)
SELECT store.id::uuid, chain.id, city.id, store.name, store.address, true, source.id, store.reference,
       '2026-11-03T12:00:00Z', CURRENT_TIMESTAMP,
       'Mercado sem loja online. Os preços são anotados na prateleira pela equipe do Gomo e valem por alguns dias.',
       'IN_STORE'
FROM (VALUES
    ('0b1c2d3e-4f50-4a61-8b72-9c83d4e5f601', 'supermarket', 'Supermarket Barra Mansa Centro',
     'Avenida Domingos Mariano, 149 - Centro, Barra Mansa - RJ',
     'https://redesupermarket.com.br/lojas/supermarket-barra-mansa/'),
    ('1c2d3e4f-5061-4b72-9c83-ad94e5f60712', 'royal', 'Royal Jardim Boa Vista',
     'Avenida Vereador Hamilton Reis Alves, 1391 - Jardim Boa Vista, Barra Mansa - RJ',
     'in-store:store:royal-jardim-boa-vista'),
    ('2d3e4f50-6172-4c83-ad94-be05f6071823', 'max supermercado', 'MAX Supermercado Goiabal',
     'Rua Moisés Braga Lima, 536 - Goiabal, Barra Mansa - RJ',
     'in-store:store:max-supermercado-goiabal'),
    ('3e4f5061-7283-4d94-be05-cf1607182934', 'mercado pingo de mel', 'Mercado Pingo de Mel',
     'Rua Padre Luis Santamaria Diez, 137 - Vista Alegre, Barra Mansa - RJ',
     'in-store:store:pingo-de-mel-vista-alegre'),
    ('4f506172-8394-4ea5-cf16-d07182930a45', 'mercado bom jesus', 'Mercado Bom Jesus',
     'Estrada Governador Chagas Freitas, 4819 - Colônia, Barra Mansa - RJ',
     'in-store:store:bom-jesus-colonia')
) AS store(id, chain_prefix, name, address, reference)
JOIN cities city ON city.name = 'Barra Mansa'
JOIN states state ON state.id = city.state_id AND state.code = 'RJ'
CROSS JOIN data_sources source
CROSS JOIN LATERAL (
    SELECT id FROM supermarket_chains WHERE lower(name) LIKE store.chain_prefix || '%' ORDER BY id LIMIT 1
) chain
WHERE source.code = 'in_store_price_check'
  AND NOT EXISTS (SELECT 1 FROM stores existing WHERE existing.id = store.id::uuid)
  AND NOT EXISTS (SELECT 1 FROM stores existing WHERE existing.source_id = source.id
                  AND existing.source_reference = store.reference);
