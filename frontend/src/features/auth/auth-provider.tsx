import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { AuthContext, type AuthContextValue } from "@/features/auth/auth-context";
import { ApiError, legacySessionStorage } from "@/lib/api";
import { authApi } from "@/services/gomo-api";
import type { User } from "@/types/api";

/*
 * O cookie de sessão é HttpOnly: o site não sabe se ele existe e pergunta à API a cada carregamento.
 * Quem entrou antes do cookie ainda tem o token no localStorage; ele é trocado por um cookie uma vez.
 */
async function loadSessionUser(): Promise<User | null> {
  const legacyToken = legacySessionStorage.read();
  if (legacyToken) {
    try {
      const user = await authApi.moveSessionToCookie(legacyToken);
      legacySessionStorage.clear();
      return user;
    } catch (error) {
      // Token vencido ou recusado: apaga e segue como visitante. Falha de rede ou do servidor tenta de novo depois.
      if (!(error instanceof ApiError && error.status >= 400 && error.status < 500)) throw error;
      legacySessionStorage.clear();
    }
  }
  return (await authApi.session()) ?? null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState<string | null>(null);

  const refreshUser = useCallback(async () => {
    setSessionError(null);
    setLoading(true);
    try {
      setUser(await loadSessionUser());
    } catch {
      setSessionError("Não foi possível verificar sua sessão. Tente novamente em instantes.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshUser();

    const handleExpiredSession = () => {
      setUser(null);
      setSessionError(null);
    };
    window.addEventListener("gomo:session-expired", handleExpiredSession);
    return () => window.removeEventListener("gomo:session-expired", handleExpiredSession);
  }, [refreshUser]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      sessionError,
      login: async (email, password) => {
        const response = await authApi.login({ email, password });
        legacySessionStorage.clear();
        setUser(response.user);
        setSessionError(null);
        return response.user;
      },
      register: async (name, email, password) => {
        // O cadastro não abre sessão nem diz se o e-mail já tinha conta; a conta nova entra pelo login normal.
        await authApi.register({ name, email, password });
        try {
          const response = await authApi.login({ email, password });
          legacySessionStorage.clear();
          setUser(response.user);
          setSessionError(null);
          return response.user;
        } catch (error) {
          if (error instanceof ApiError && error.status === 401) return null;
          throw error;
        }
      },
      logout: async () => {
        try {
          await authApi.logout();
        } finally {
          legacySessionStorage.clear();
          setUser(null);
          setSessionError(null);
        }
      },
      refreshUser,
    }),
    [loading, refreshUser, sessionError, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
