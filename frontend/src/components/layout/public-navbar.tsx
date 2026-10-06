import { Menu } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { NativeButton } from "@/components/ui/native-button";
import { Sheet } from "@/components/ui/sheet";
import { useAuth } from "@/features/auth/auth-context";

const navigation = [
  { label: "Como funciona", href: "/#como-funciona" },
  { label: "Preço", href: "/#preco" },
  { label: "Dúvidas", href: "/#duvidas" },
];

export function PublicNavbar() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { user } = useAuth();
  const platformPath = user ? "/app" : "/entrar";
  const signUpPath = user ? "/app/comparar" : "/entrar?modo=cadastro";

  return (
    <header className="sticky top-0 z-40 border-b border-border/80 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/85">
      <div className="mx-auto flex h-18 max-w-7xl items-center justify-between gap-5 px-4 sm:px-6 lg:px-16">
        <Link to="/" aria-label="Gomo — página inicial" className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2">
          <BrandLogo />
        </Link>

        <nav aria-label="Navegação principal" className="hidden items-center gap-1 lg:flex">
          {navigation.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="rounded-lg px-3 py-2 text-sm font-semibold text-muted transition-colors hover:bg-surface-strong hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            >
              {item.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-2 lg:flex">
          <NativeButton to={platformPath} variant="ghost" className="landing-button">Acessar plataforma</NativeButton>
          <NativeButton to={signUpPath} className="landing-button">Começar grátis</NativeButton>
        </div>

        <button
          type="button"
          className="icon-button public-navbar-menu-button"
          aria-label="Abrir navegação"
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen(true)}
        >
          <Menu className="size-5" aria-hidden />
        </button>
      </div>

      <Sheet
        open={mobileOpen}
        onOpenChange={setMobileOpen}
        title="Navegação"
        description="Acesse as principais áreas da Gomo."
      >
        <nav aria-label="Navegação móvel" className="flex h-full flex-col">
          <div className="space-y-1">
            {navigation.map((item) => (
              <a
                key={item.href}
                href={item.href}
                onClick={() => setMobileOpen(false)}
                className="block rounded-lg px-3 py-3 font-semibold text-foreground hover:bg-surface-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              >
                {item.label}
              </a>
            ))}
          </div>
          <div className="mt-auto grid gap-2 border-t border-border pt-5">
            <NativeButton to={platformPath} variant="secondary" className="w-full">Acessar plataforma</NativeButton>
            <NativeButton to={signUpPath} className="w-full">Começar grátis</NativeButton>
          </div>
        </nav>
      </Sheet>
    </header>
  );
}
