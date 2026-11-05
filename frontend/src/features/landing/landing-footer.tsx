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
  { label: "Planos", href: "/#preco" },
  { label: "Dúvidas", href: "/#duvidas" },
];

/* Só o que abre sem conta: listas e alertas pediriam login a quem ainda está conhecendo o Gomo. */
const appLinks: FooterLink[] = [
  { label: "Buscar preços", to: "/app/produtos" },
  { label: "Ofertas da semana", to: "/ofertas" },
  { label: "Supermercados", to: "/app/supermercados" },
];

const legalLinks: FooterLink[] = [
  { label: "Termos de Uso", to: "/termos" },
  { label: "Privacidade", to: "/privacidade" },
];

export function LandingFooter() {
  const { user } = useAuth();
  const accountLinks: FooterLink[] = user
    ? [{ label: "Abrir o Gomo", to: "/app" }, { label: "Assinar", to: "/assinar" }]
    : [{ label: "Criar conta", to: "/entrar?modo=cadastro" }, { label: "Entrar", to: "/entrar" }];

  const columns = [
    { title: "Gomo", links: pageLinks },
    { title: "Sem conta", links: appLinks },
    { title: "Conta", links: accountLinks },
    { title: "Legal", links: legalLinks },
  ];

  return (
    <footer className="landing-footer-band">
      <div className="landing-wide landing-footer">
        <div className="landing-footer__brand">
          <BrandLogo />
          <p>Compare preços de supermercado antes de sair de casa.</p>
        </div>

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

        <p className="landing-footer__bottom">© {new Date().getFullYear()} Gomo</p>
      </div>
    </footer>
  );
}
