package br.com.supermercados.prices.shoppinglist;

import br.com.supermercados.prices.basicitem.BasicItem;

public record AnyBrandSuggestion(String basicItem, String name) {

    static AnyBrandSuggestion of(BasicItem item) {
        return new AnyBrandSuggestion(item.slug(), item.name());
    }
}
