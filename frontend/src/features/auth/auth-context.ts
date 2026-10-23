import { createContext, useContext } from "react";
import type { User } from "@/types/api";

export interface AuthContextValue {
  user: User | null;
  loading: boolean;
  sessionError: string | null;
  login: (email: string, password: string) => Promise<User>;
  /** A conta já entra quando a senha confere; null quando não entrou (o e-mail já tinha outra conta). */
  register: (name: string, email: string, password: string) => Promise<User | null>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
