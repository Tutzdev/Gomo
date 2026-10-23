-- Produtos deixados de fora pelas regras (sem peso médio, preço por kg sem base, variações sem código)
-- deixam de contar como erro da coleta; o motivo e a quantidade ficam resumidos aqui.
ALTER TABLE collection_runs ADD COLUMN skip_summary VARCHAR(2000);
