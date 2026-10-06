import { Link } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { useAuth } from "@/features/auth/auth-context";

interface FooterLink {
  label: string;
  /** Âncora da própria landing. */
  href?: string;
  /** Rota do app. */
  to?: string;
}

const pageLinks: FooterLink[] = [
  { label: "Como funciona", href: "/#como-funciona" },
  { label: "Preços", href: "/#preco" },
  { label: "Dúvidas", href: "/#duvidas" },
];

const appLinks: FooterLink[] = [
  { label: "Comparar preços", to: "/app/comparar" },
  { label: "Minhas listas", to: "/app/listas" },
  { label: "Alertas de preço", to: "/app/alertas" },
  { label: "Supermercados", to: "/app/supermercados" },
];

export function LandingFooter() {
  const { user } = useAuth();
  const accountLinks: FooterLink[] = user
    ? [{ label: "Abrir o Gomo", to: "/app" }, { label: "Planos", to: "/assinar" }]
    : [{ label: "Criar conta", to: "/entrar?modo=cadastro" }, { label: "Entrar", to: "/entrar" }, { label: "Planos", to: "/assinar" }];

  const columns = [
    { title: "Gomo", links: pageLinks },
    { title: "No app", links: appLinks },
    { title: "Conta", links: accountLinks },
  ];

  return (
    <footer className="landing-band">
      <div className="landing-frame landing-footer">
        <nav aria-label="Rodapé" className="landing-footer__columns">
          {columns.map((column) => (
            <div key={column.title}>
              <h2 className="landing-footer__title">{column.title}</h2>
              <ul className="landing-footer__links">
                {column.links.map((link) => (
                  <li key={link.label}>
                    {link.to ? (
                      <Link to={link.to} className="landing-footer__link">{link.label}</Link>
                    ) : (
                      <a href={link.href} className="landing-footer__link">{link.label}</a>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        <div className="landing-footer__bottom">
          <BrandLogo />
          <p>Compare preços de supermercado antes de sair de casa.</p>
          <p>© {new Date().getFullYear()} Gomo</p>
        </div>
      </div>
    </footer>
  );
}
