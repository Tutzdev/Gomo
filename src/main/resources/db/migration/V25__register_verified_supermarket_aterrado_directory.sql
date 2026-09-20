ALTER TABLE stores ADD COLUMN price_source_note varchar(1000);

-- Directory only. Verified 2026-09-20 on the retailer's branch page.
-- Its September 18-21 flyer explicitly excludes Sul Fluminense; no prices are imported.
INSERT INTO data_sources (id, code, name, base_url, enabled, verified_at, created_at)
VALUES ('ee98e5c4-c956-4a8e-adbb-4d2ef9a6ac74', 'SUPERMARKET_OFFICIAL_DIRECTORY',
        'Supermarket - diretório oficial de lojas', 'https://redesupermarket.com.br/', true,
        '2026-09-20T20:15:56Z', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO supermarket_chains (id, name, source_id, source_reference, collected_at, updated_at)
SELECT '6518d725-f7b6-4b47-b86b-ed2af0b3b5d0', 'Supermarket', id,
       'https://redesupermarket.com.br/nossas-lojas/', '2026-09-20T20:15:56Z', CURRENT_TIMESTAMP
FROM data_sources WHERE code = 'SUPERMARKET_OFFICIAL_DIRECTORY'
AND NOT EXISTS (SELECT 1 FROM supermarket_chains WHERE lower(name) IN ('supermarket', 'rede supermarket'));

INSERT INTO stores (id, supermarket_chain_id, city_id, name, address, active, source_id,
                    source_reference, collected_at, updated_at, price_source_note)
SELECT 'c6b05cb7-88c0-463d-b098-af57cd3fa2d8', chain.id, city.id, 'Supermarket Aterrado',
       'Avenida Paulo de Frontin, 1096 - Aterrado, Volta Redonda - RJ', true, source.id,
       'https://redesupermarket.com.br/lojas/supermarket-aterrado/', '2026-09-20T20:15:56Z', CURRENT_TIMESTAMP,
       'Unidade confirmada no diretório oficial. Preços não integrados: o catálogo público indicado está inacessível e o encarte consultado exclui o Sul Fluminense. Nenhum preço ou estoque foi presumido.'
FROM cities city
JOIN states state ON state.id = city.state_id
CROSS JOIN data_sources source
CROSS JOIN LATERAL (
    SELECT id FROM supermarket_chains WHERE lower(name) IN ('supermarket', 'rede supermarket') ORDER BY id LIMIT 1
) chain
WHERE city.name = 'Volta Redonda' AND state.code = 'RJ'
  AND source.code = 'SUPERMARKET_OFFICIAL_DIRECTORY'
  AND NOT EXISTS (
      SELECT 1 FROM stores existing WHERE existing.city_id = city.id
      AND (lower(existing.name) LIKE '%supermarket%aterrado%'
           OR (lower(existing.address) LIKE '%paulo%frontin%' AND existing.address LIKE '%1096%'))
  );
