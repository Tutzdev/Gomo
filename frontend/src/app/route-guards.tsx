import { Navigate, Outlet, useLocation } from "react-router-dom";
import { AppShell } from "@/components/layout/app-shell";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { useAuth } from "@/features/auth/auth-context";

/**
 * The app area. Visitors share it with signed-in people: searching, comparing and the markets are open, so
 * someone who arrives from a product page on Google can keep comparing without an account.
 */
export function AppLayout() {
  const { loading, sessionError, refreshUser } = useAuth();

  if (loading) {
    return (
      <main className="grid min-h-screen place-items-center p-4">
        <LoadingState label="Carregando sua conta…" />
      </main>
    );
  }
  if (sessionError) {
    return (
      <main className="grid min-h-screen place-items-center p-4">
        <ErrorState message={sessionError} retry={() => void refreshUser()} />
      </main>
    );
  }
  return <AppShell />;
}

/** Pages that belong to an account (lists, alerts, profile): visitors sign in first and come back here. */
export function AccountGuard() {
  const { user } = useAuth();
  const { pathname, search, hash } = useLocation();
  // The hash too: an invite link carries its token there.
  if (!user) return <Navigate to={`/entrar?depois=${encodeURIComponent(pathname + search + hash)}`} replace />;
  return <Outlet />;
}

export function AdminGuard() {
  const { user } = useAuth();
  return user?.role === "ADMIN" ? <Outlet /> : <Navigate to="/acesso-negado" replace />;
}
