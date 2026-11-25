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

export function relativeTime(value: string, now: number = Date.now()) {
  const minutes = Math.max(0, Math.round((now - new Date(value).getTime()) / 60000));

  if (minutes < 60) {
    return minutes <= 1 ? "agora" : `há ${minutes} min`;
  }

  const hours = Math.round(minutes / 60);

  if (hours < 24) {
    return `há ${hours} h`;
  }

  const days = Math.round(hours / 24);

  return days === 1 ? "ontem" : `há ${days} dias`;
}
