import type { CSSProperties } from "react";
import {
  Bell,
  CircleDollarSign,
  CupSoda,
  LayoutDashboard,
  ListChecks,
  Menu,
  PackageSearch,
  Settings,
  Store,
  Tags,
  Trophy,
  type LucideIcon,
} from "lucide-react";
import gomoLogo from "../../../../gomo-logo.png";
import { formatCurrency } from "@/lib/brand";

/*
 * As telas da hero são o próprio app desenhado em código (nítido em qualquer tela), com os mesmos
 * ícones, cores e textos do dashboard. Os preços são reais, coletados em 05/10/2026: ao atualizar,
 * troque os números aqui (o texto da figura acompanha).
 */
const comparison = {
  product: "Coca-Cola 2 L",
  offers: [
    { store: "Hortifruti Aterrado", price: 10.49 },
    { store: "Pérola Água Limpa", price: 10.79 },
    { store: "Spani Volta Redonda", price: 11.5 },
    { store: "Ville Sessenta", price: 11.9 },
    { store: "Nagumo Ponte Alta", price: 12.59 },
    { store: "Royal Retiro", price: 12.79 },
    { store: "Bramil Santo Agostinho", price: 13.69 },
  ],
};

const shoppingList = {
  name: "Compra da semana",
  itemCount: 10,
  items: [
    { name: "Coca-Cola 2 L", quantity: 2 },
    { name: "Açúcar União 1 kg", quantity: 2 },
    { name: "Óleo de soja Liza 900 ml", quantity: 2 },
    { name: "Biscoito Maizena Piraquê", quantity: 2 },
    { name: "Creme de leite Nestlé", quantity: 2 },
    { name: "Detergente Ypê 500 ml", quantity: 3 },
  ],
  cheapest: { store: "Pérola Água Limpa", total: 121.22 },
};

/* Os itens do menu lateral do dashboard, na mesma ordem. */
const sidebar: { label: string; icon: LucideIcon; active?: boolean }[] = [
  { label: "Visão geral", icon: LayoutDashboard },
  { label: "Comparar preços", icon: Tags, active: true },
  { label: "Produtos", icon: PackageSearch },
  { label: "Supermercados", icon: Store },
  { label: "Minhas listas", icon: ListChecks },
  { label: "Alertas de preço", icon: CircleDollarSign },
  { label: "Notificações", icon: Bell },
  { label: "Perfil e preferências", icon: Settings },
];

/* As abas do app no celular. */
const tabs: { label: string; icon: LucideIcon; active?: boolean }[] = [
  { label: "Início", icon: LayoutDashboard },
  { label: "Comparar", icon: Tags },
  { label: "Listas", icon: ListChecks, active: true },
  { label: "Alertas", icon: CircleDollarSign },
  { label: "Perfil", icon: Settings },
];

const cheapest = comparison.offers[0].price;
const highest = comparison.offers.at(-1)!.price;

export function HeroShowcase() {
  return (
    <figure className="hero-showcase">
      <figcaption className="sr-only">
        O Gomo comparando a {comparison.product} em {comparison.offers.length} mercados, de{" "}
        {formatCurrency(cheapest)} a {formatCurrency(highest)}, e mostrando que a{" "}
        {shoppingList.name.toLowerCase()} sai por {formatCurrency(shoppingList.cheapest.total)} no{" "}
        {shoppingList.cheapest.store}.
      </figcaption>

      <div className="hero-app" aria-hidden>
        <div className="hero-app__sidebar">
          <img src={gomoLogo} alt="" className="hero-app__logo" />
          <ul>
            {sidebar.map(({ label, icon: Icon, active }) => (
              <li key={label} className={active ? "is-active" : undefined}>
                <Icon />
                {label}
              </li>
            ))}
          </ul>
        </div>

        <div className="hero-app__main">
          <div className="hero-app__bar">
            <Menu />
            Comparar preços
          </div>

          <div className="hero-app__body">
            <div className="hero-compare">
              <div className="hero-compare__head">
                <span className="hero-compare__thumb">
                  <CupSoda />
                </span>
                <span>
                  <strong>{comparison.product}</strong>
                  <span>Preço atual em {comparison.offers.length} mercados</span>
                </span>
              </div>

              <p className="hero-compare__savings">
                Comprando no mais barato você economiza até <strong>{formatCurrency(highest - cheapest)}</strong> por
                unidade.
              </p>

              <ol>
                {comparison.offers.map((offer, index) => (
                  <li
                    key={offer.store}
                    className={index === 0 ? "is-cheapest" : undefined}
                    style={{ "--row": index } as CSSProperties}
                  >
                    <span className="hero-compare__rank">{index === 0 ? <Trophy /> : index + 1}</span>
                    <span className="hero-compare__store">
                      <Store />
                      {offer.store}
                    </span>
                    <span className="hero-compare__price">
                      <strong>{formatCurrency(offer.price)}</strong>
                      <span>{index === 0 ? "Mais barato" : `+${formatCurrency(offer.price - cheapest)}`}</span>
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </div>
      </div>

      <div className="hero-phone" aria-hidden>
        <div className="hero-phone__screen">
          <div className="hero-phone__status">
            <span className="hero-phone__time">9:41</span>
            <span className="hero-phone__island" />
            <span className="hero-phone__indicators">
              <CellularIcon />
              <WifiIcon />
              <BatteryIcon />
            </span>
          </div>

          <div className="hero-phone__bar">
            Minhas listas
            <Bell />
          </div>

          <div className="hero-list">
            <strong className="hero-list__name">{shoppingList.name}</strong>
            <span className="hero-list__count">{shoppingList.itemCount} itens</span>
            <ul>
              {shoppingList.items.map((item) => (
                <li key={item.name}>
                  <span>{item.name}</span>
                  <span>{item.quantity}×</span>
                </li>
              ))}
            </ul>
            <span className="hero-list__more">e mais {shoppingList.itemCount - shoppingList.items.length} itens</span>
          </div>

          <div className="hero-list__result">
            <span className="hero-list__badge">Lista completa mais barata</span>
            <span className="hero-list__store">{shoppingList.cheapest.store}</span>
            <strong>{formatCurrency(shoppingList.cheapest.total)}</strong>
          </div>

          <nav className="hero-phone__tabs">
            {tabs.map(({ label, icon: Icon, active }) => (
              <span key={label} className={active ? "is-active" : undefined}>
                <Icon />
                {label}
              </span>
            ))}
          </nav>

          <span className="hero-phone__home" />
        </div>
      </div>
    </figure>
  );
}

/* Ícones da barra de status desenhados como os do iOS: quatro barras, leque do Wi-Fi e bateria cheia. */
function CellularIcon() {
  return (
    <svg className="hero-phone__cellular" viewBox="0 0 18 12" fill="currentColor">
      <rect x="0" y="7.5" width="3" height="4.5" rx="1" />
      <rect x="5" y="5" width="3" height="7" rx="1" />
      <rect x="10" y="2.5" width="3" height="9.5" rx="1" />
      <rect x="15" y="0" width="3" height="12" rx="1" />
    </svg>
  );
}

function WifiIcon() {
  return (
    <svg className="hero-phone__wifi" viewBox="-0.7 -0.4 17.4 12.4" fill="none" stroke="currentColor" strokeLinecap="round">
      <path d="M0.93 4.43 A10 10 0 0 1 15.07 4.43" strokeWidth="2.2" />
      <path d="M3.47 6.97 A6.4 6.4 0 0 1 12.53 6.97" strokeWidth="2.2" />
      <path d="M8 11.6 L5.45 8.95 A3.6 3.6 0 0 1 10.55 8.95 Z" fill="currentColor" strokeWidth="1" strokeLinejoin="round" />
    </svg>
  );
}

function BatteryIcon() {
  return (
    <svg className="hero-phone__battery" viewBox="0 0 27 13">
      <rect x="0.5" y="0.5" width="23" height="12" rx="3.8" fill="none" stroke="currentColor" strokeOpacity="0.35" />
      <rect x="2" y="2" width="20" height="9" rx="2.4" fill="currentColor" />
      <path d="M25 4.5 a1.5 1.5 0 0 1 1.5 1.5 v1 a1.5 1.5 0 0 1 -1.5 1.5 z" fill="currentColor" fillOpacity="0.4" />
    </svg>
  );
}
