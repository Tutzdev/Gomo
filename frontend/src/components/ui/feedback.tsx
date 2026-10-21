import { AlertCircle, Inbox, LoaderCircle, RefreshCw } from "lucide-react";
import type { ReactNode } from "react";
import { NativeButton } from "@/components/ui/native-button";

export function LoadingState({ label = "Carregando informações…" }: { label?: string }) {
  return (
    <div className="state-panel" role="status" aria-live="polite">
      <LoaderCircle className="size-6 animate-spin text-primary motion-reduce:animate-none" aria-hidden />
      <p>{label}</p>
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="state-panel anim-trigger">
      <span className="grid size-11 place-items-center rounded-lg bg-surface-strong text-muted">
        <Inbox className="size-5" aria-hidden />
      </span>
      <div>
        <h3 className="font-semibold text-foreground">{title}</h3>
        <p className="mt-1 max-w-md text-sm text-muted">{description}</p>
      </div>
      {action}
    </div>
  );
}

export function ErrorState({ message, retry }: { message?: string; retry?: () => void }) {
  return (
    <div className="state-panel anim-trigger border-danger/25 bg-danger-soft" role="alert">
      <AlertCircle className="size-6 text-danger" aria-hidden />
      <div>
        <h3 className="font-semibold text-foreground">Não foi possível carregar</h3>
        <p className="mt-1 max-w-lg text-sm text-muted">
          {message ?? "Ocorreu um problema inesperado. Tente novamente."}
        </p>
      </div>
      {retry ? (
        <NativeButton variant="secondary" onClick={retry}>
          <RefreshCw className="size-4" aria-hidden />
          Tentar novamente
        </NativeButton>
      ) : null}
    </div>
  );
}

export function InlineError({ children, id }: { children?: ReactNode; id?: string }) {
  if (!children) return null;

  return (
    <p id={id} className="mt-1.5 flex items-start gap-1.5 text-sm text-danger" role="alert">
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden />
      <span>{children}</span>
    </p>
  );
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-3" aria-label="Carregando" aria-busy="true">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="h-16 animate-pulse rounded-lg bg-surface-strong motion-reduce:animate-none" />
      ))}
    </div>
  );
}
