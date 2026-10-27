import { CircleSlash2, Home, LockKeyhole } from "lucide-react";
import { BrandLogo } from "@/components/brand-logo";
import { NativeButton } from "@/components/ui/native-button";

export function AccessDeniedPage() {
  return <MessagePage icon={LockKeyhole} title="Acesso não permitido" description="Sua conta não possui permissão para abrir esta área." action="Voltar à visão geral" to="/app" />;
}

export function NotFoundPage() {
  return <MessagePage icon={CircleSlash2} title="Página não encontrada" description="O endereço informado não existe ou foi movido." action="Ir para o início" to="/" />;
}

function MessagePage({ icon: Icon, title, description, action, to }: { icon: typeof Home; title: string; description: string; action: string; to: string }) {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f7f7f8] p-4">
      <div className="surface w-full max-w-lg p-7 text-center sm:p-10">
        <BrandLogo className="mx-auto" />
        <span className="mx-auto mt-8 grid size-12 place-items-center rounded-lg bg-primary-soft text-primary">
          <Icon className="size-6" aria-hidden />
        </span>
        <h1 className="mt-5 text-2xl font-extrabold tracking-tight">{title}</h1>
        <p className="mt-2 text-sm leading-6 text-muted">{description}</p>
        <NativeButton to={to} className="mt-6">
          <Home className="size-4" aria-hidden />
          {action}
        </NativeButton>
      </div>
    </main>
  );
}
