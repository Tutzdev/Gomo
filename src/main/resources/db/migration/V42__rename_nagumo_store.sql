-- A unidade aparecia com o código interno da Nagumo ("036-V.REDONDA") no admin e no /status.
UPDATE stores SET name = 'Nagumo Ponte Alta' WHERE name = 'Nagumo Ponte Alta (036-V.REDONDA)';
UPDATE collection_runs SET store_name = 'Nagumo Ponte Alta' WHERE store_name = '036-V.REDONDA';
