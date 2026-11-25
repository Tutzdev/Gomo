export function pricedStoresLabel(count: number, cityName?: string | null) {
  const where = cityName ? ` de ${cityName}` : "";

  if (count > 1) {
    return `Preço em ${count} mercados${where}`;
  }

  if (count === 1) {
    return `Preço em 1 mercado${where}`;
  }

  return `Sem preço atual nos mercados${where}`;
}
