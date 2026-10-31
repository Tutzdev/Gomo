package br.com.supermercados.prices.collection;

import java.util.UUID;

/** A city seeded by V3 that a collector serves; the name is what the source must publish for the unit. */
public record ServedCity(UUID id, String name, String stateName, String stateCode) {

    public static final ServedCity VOLTA_REDONDA = new ServedCity(
            UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"), "Volta Redonda", "Rio de Janeiro", "RJ");
    public static final ServedCity BARRA_MANSA = new ServedCity(
            UUID.fromString("2f6cba12-af5c-4af6-96a6-da7184148f88"), "Barra Mansa", "Rio de Janeiro", "RJ");

    public String label() {
        return name + "/" + stateCode;
    }
}
