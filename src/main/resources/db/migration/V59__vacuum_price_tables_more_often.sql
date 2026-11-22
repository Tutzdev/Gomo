-- A compactação noturna apaga ~500 mil preços repetidos por dia. Com o padrão (limpar quando 20% da tabela
-- estiver morta) o espaço não era reaproveitado a tempo e price_records chegou a 14 GB com 1 GB de dados.
ALTER TABLE price_records SET (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.02
);

ALTER TABLE latest_prices SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.05
);

ALTER TABLE daily_lowest_prices SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.05
);
