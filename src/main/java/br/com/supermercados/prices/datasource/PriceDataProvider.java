package br.com.supermercados.prices.datasource;

import br.com.supermercados.prices.price.PriceObservation;

import java.util.UUID;

public interface PriceDataProvider {

    UUID sourceId();

    ProviderResult<PriceObservation> fetchPrices(ProviderRequest request);
}
