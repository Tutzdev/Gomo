import type { ApiProblem } from "@/types/api";

/*
 * Em produção a API fica no mesmo domínio do site (o Nginx encaminha /api para o backend).
 * Um endereço de localhost só serve no desenvolvimento: publicado, ele apontaria para a máquina
 * de quem acessa e todo pedido falharia com "Não foi possível conectar ao servidor".
 */
const API_BASE_URL = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL);

function resolveApiBaseUrl(configured: string | undefined) {
  const sameOrigin = "/api/v1";
  const url = configured?.trim().replace(/\/$/, "");
  if (!url) return import.meta.env.DEV ? "http://localhost:8080/api/v1" : sameOrigin;
  if (import.meta.env.PROD && isLocalAddress(url) && !isLocalAddress(window.location.origin)) return sameOrigin;
  return url;
}

function isLocalAddress(url: string) {
  try {
    return ["localhost", "127.0.0.1", "[::1]"].includes(new URL(url, window.location.origin).hostname);
  } catch {
    return false;
  }
}

const LEGACY_TOKEN_KEY = "gomo.session";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ApiProblem,
  ) {
    super(problem.detail ?? "Não foi possível concluir a solicitação.");
  }
}

/*
 * A sessão fica num cookie HttpOnly que o JavaScript não lê. Antes dele, o token ficava no localStorage;
 * esse token antigo só é lido uma vez, para ser trocado por um cookie, e depois apagado.
 */
export const legacySessionStorage = {
  read: () => {
    try {
      return window.localStorage.getItem(LEGACY_TOKEN_KEY);
    } catch {
      return null;
    }
  },
  clear: () => {
    try {
      window.localStorage.removeItem(LEGACY_TOKEN_KEY);
    } catch {
      // Sem acesso ao armazenamento (modo privado bloqueado), não há nada para apagar.
    }
  },
};

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  /** false nas rotas de login e cadastro: um 401 nelas é senha errada, não sessão vencida. */
  authenticated?: boolean;
  /**
   * Dados públicos pedidos sem a sessão, como o visitante os veria. A página pública de um produto, por
   * exemplo, não gasta as comparações do dia de quem está logado.
   */
  anonymous?: boolean;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { authenticated, anonymous = false, body, ...init } = options;
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (!anonymous) {
    // Pede a sessão em cookie e marca o pedido para a checagem de origem (proteção contra CSRF) da API.
    headers.set("X-Gomo-Session", "cookie");
  }

  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
      credentials: anonymous ? "omit" : "include",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, {
      title: "Falha de rede",
      detail: "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.",
    });
  }

  if (response.status === 401 && authenticated !== false && !anonymous) {
    window.dispatchEvent(new Event("gomo:session-expired"));
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ApiProblem;
    problem.detail ??= `O servidor respondeu HTTP ${response.status}. Tente novamente.`;
    throw new ApiError(response.status, problem);
  }

  // 204 e os 202 da API (cadastro, e-mails de conta) não têm corpo.
  if (response.status === 204 || response.status === 202) return undefined as T;

  if (!response.headers.get("Content-Type")?.includes("json")) {
    throw new ApiError(response.status, {
      title: "Resposta inesperada",
      detail: "O servidor não retornou os dados esperados. Tente novamente mais tarde.",
    });
  }

  return (await response.json()) as T;
}

export function queryString(values: Record<string, string | number | undefined | null>) {
  const params = new URLSearchParams();

  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  });

  return params.toString();
}
