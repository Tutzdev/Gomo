-- Gomo only lists markets that sell online with a public price. Two units never had that: Supermarket
-- Aterrado (directory entry only, no price) and Atacadão Vila Rica (hand-reviewed flyers, expired 2026-09-21).
-- They are deactivated, not deleted: price history, favourites, alert notifications and contributions keep
-- pointing at them (ON DELETE RESTRICT), and an inactive store is already hidden from listings and comparisons.
UPDATE stores
SET active = false,
    price_source_note = 'Retirado em 10/2026: o Gomo só compara mercados com loja online e preço público.',
    updated_at = CURRENT_TIMESTAMP
WHERE active
  AND (source_id IN (SELECT id FROM data_sources WHERE code = 'SUPERMARKET_OFFICIAL_DIRECTORY')
       OR (source_id IN (SELECT id FROM data_sources WHERE code = 'atacadao_reviewed_flyers')
           AND source_reference = 'atacadao:store:289'));

UPDATE data_sources SET enabled = false WHERE code = 'SUPERMARKET_OFFICIAL_DIRECTORY';

-- The flyer importer is gone, but this source still owns the Atacadão São Geraldo unit that the online
-- collector attaches its prices to. The code stays (it is the unit's identity); only the label changes.
UPDATE data_sources SET name = 'Diretório oficial de lojas Atacadão' WHERE code = 'atacadao_reviewed_flyers';
