import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, CheckCircle2, Eye, EyeOff, LockKeyhole, Mail } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm, type UseFormRegisterReturn } from "react-hook-form";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { z } from "zod";
import { BrandLogo } from "@/components/brand-logo";
import { InlineError } from "@/components/ui/feedback";
import { TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { useAuth } from "@/features/auth/auth-context";
import { ApiError } from "@/lib/api";
import { authApi } from "@/services/gomo-api";

const loginSchema = z.object({
  email: z.email("Informe um e-mail válido."),
  password: z.string().min(1, "Informe sua senha.").max(72, "A senha deve ter no máximo 72 caracteres."),
});

const registerSchema = z.object({
  name: z.string().trim().min(1, "Informe seu nome.").max(120, "Use no máximo 120 caracteres."),
  email: z.email("Informe um e-mail válido.").max(254),
  password: z.string().min(12, "Use pelo menos 12 caracteres.").max(72, "Use no máximo 72 caracteres."),
});

type LoginValues = z.infer<typeof loginSchema>;
type RegisterValues = z.infer<typeof registerSchema>;

/** Only same-site paths are accepted as a return destination, never another origin. */
function internalPath(value: string | null) {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : null;
}

export function AuthPage() {
  const { user, login, register } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [mode, setMode] = useState<"login" | "register">(
    searchParams.get("modo") === "cadastro" ? "register" : "login",
  );
  const [message, setMessage] = useState<string | null>(null);
  const destination = internalPath(searchParams.get("depois")) ?? "/app";

  if (user) return <Navigate to={destination} replace />;

  const handleLogin = async (values: LoginValues) => {
    await login(values.email, values.password);
    navigate(destination, { replace: true });
  };

  const handleRegister = async (values: RegisterValues) => {
    await register(values.name, values.email, values.password);
    setMessage("Conta criada. Entre para solicitar a verificação do seu e-mail.");
    setMode("login");
  };

  return (
    <AuthFrame>
      <div className={`auth-switcher ${mode === "register" ? "auth-switcher--register" : ""}`}>
        <div className="auth-form-panel auth-form-panel--login">
          <LoginForm onSubmit={handleLogin} message={message} />
        </div>
        <div className="auth-form-panel auth-form-panel--register">
          <RegisterForm onSubmit={handleRegister} />
        </div>
        <div className="auth-brand-panel" aria-hidden="true">
          <div className="max-w-sm">
            <BrandLogo className="h-11 brightness-0 invert" />
            <h2 className="mt-8 text-3xl font-extrabold tracking-[-0.04em]">
              {mode === "login" ? "Ainda não usa a Gomo?" : "Que bom ter você de volta."}
            </h2>
            <p className="mt-4 leading-7 text-white/72">
              {mode === "login"
                ? "Crie sua conta para organizar listas, alertas e preferências em um só lugar."
                : "Entre para continuar suas comparações e acompanhar seus preços."}
            </p>
            <button
              type="button"
              className="mt-8 min-h-11 rounded-lg border border-white/35 px-5 font-bold text-white hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white"
              onClick={() => {
                setMessage(null);
                setMode(mode === "login" ? "register" : "login");
              }}
            >
              {mode === "login" ? "Criar conta" : "Entrar na conta"}
            </button>
          </div>
        </div>
      </div>

      <div className="mt-4 text-center text-sm md:hidden">
        {mode === "login" ? "Ainda não tem conta?" : "Já tem uma conta?"}{" "}
        <button type="button" className="font-bold text-primary underline-offset-4 hover:underline" onClick={() => setMode(mode === "login" ? "register" : "login")}>
          {mode === "login" ? "Criar conta" : "Entrar"}
        </button>
      </div>
    </AuthFrame>
  );
}

function LoginForm({ onSubmit, message }: { onSubmit: (values: LoginValues) => Promise<void>; message: string | null }) {
  const [showPassword, setShowPassword] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const submit = handleSubmit(async (values) => {
    setApiError(null);
    try { await onSubmit(values); } catch (error) { setApiError(error instanceof ApiError ? error.message : "Não foi possível entrar."); }
  });

  return (
    <form onSubmit={submit} className="mx-auto w-full max-w-sm" noValidate>
      <p className="text-sm font-extrabold uppercase tracking-[0.14em] text-primary">Acessar a Gomo</p>
      <h1 className="mt-2 text-3xl font-extrabold tracking-[-0.04em]">Entre na sua conta</h1>
      <p className="mt-2 text-sm leading-6 text-muted">Continue suas listas, comparações e alertas.</p>
      {message ? <p className="mt-5 flex gap-2 rounded-lg bg-success-soft p-3 text-sm text-success" role="status"><CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden />{message}</p> : null}
      <div className="mt-7 space-y-4">
        <TextField id="login-email" label="E-mail" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
        <PasswordField id="login-password" label="Senha" visible={showPassword} onVisibilityChange={setShowPassword} autoComplete="current-password" error={errors.password?.message} registration={register("password")} />
      </div>
      <div className="mt-3 text-right"><Link to="/recuperar-senha" className="text-sm font-semibold text-primary hover:underline">Esqueci minha senha</Link></div>
      <InlineError>{apiError}</InlineError>
      <NativeButton type="submit" loading={isSubmitting} className="mt-5 w-full" size="lg" glow>Entrar</NativeButton>
    </form>
  );
}

function RegisterForm({ onSubmit }: { onSubmit: (values: RegisterValues) => Promise<void> }) {
  const [showPassword, setShowPassword] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) });
  const submit = handleSubmit(async (values) => {
    setApiError(null);
    try { await onSubmit(values); } catch (error) { setApiError(error instanceof ApiError ? error.message : "Não foi possível criar a conta."); }
  });

  return (
    <form onSubmit={submit} className="mx-auto w-full max-w-sm" noValidate>
      <p className="text-sm font-extrabold uppercase tracking-[0.14em] text-primary">Começar</p>
      <h1 className="mt-2 text-3xl font-extrabold tracking-[-0.04em]">Crie sua conta</h1>
      <p className="mt-2 text-sm leading-6 text-muted">Organize suas compras e compare preços com contexto.</p>
      <div className="mt-7 space-y-4">
        <TextField id="register-name" label="Nome" autoComplete="name" error={errors.name?.message} {...register("name")} />
        <TextField id="register-email" label="E-mail" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
        <PasswordField id="register-password" label="Senha" visible={showPassword} onVisibilityChange={setShowPassword} autoComplete="new-password" hint="Use de 12 a 72 caracteres." error={errors.password?.message} registration={register("password")} />
      </div>
      <InlineError>{apiError}</InlineError>
      <NativeButton type="submit" loading={isSubmitting} className="mt-5 w-full" size="lg" glow>Criar conta</NativeButton>
      <p className="mt-4 text-center text-xs leading-5 text-muted">
        Ao criar a conta, você concorda com os <Link to="/termos" className="font-semibold text-primary hover:underline">Termos de Uso</Link> e
        a <Link to="/privacidade" className="font-semibold text-primary hover:underline">Política de Privacidade</Link>.
      </p>
    </form>
  );
}

function PasswordField({ id, label, visible, onVisibilityChange, error, hint, autoComplete, registration }: { id: string; label: string; visible: boolean; onVisibilityChange: (visible: boolean) => void; error?: string; hint?: string; autoComplete: string; registration: UseFormRegisterReturn }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-semibold">{label}</label>
      <div className="relative">
        <input id={id} type={visible ? "text" : "password"} autoComplete={autoComplete} className="field-control pr-12" aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined} {...registration} />
        <button type="button" className="absolute right-1 top-1 grid size-9 place-items-center rounded-md text-muted hover:bg-surface-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus" onClick={() => onVisibilityChange(!visible)} aria-label={visible ? "Ocultar senha" : "Mostrar senha"}>
          {visible ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
        </button>
      </div>
      {hint && !error ? <p id={`${id}-hint`} className="mt-1.5 text-xs text-muted">{hint}</p> : null}
      <InlineError id={`${id}-error`}>{error}</InlineError>
    </div>
  );
}

export function PasswordResetRequestPage() {
  const schema = z.object({ email: z.email("Informe um e-mail válido.") });
  type Values = z.infer<typeof schema>;
  const [sent, setSent] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema) });

  return (
    <AuthFrame compact>
      <form className="mx-auto max-w-sm" noValidate onSubmit={handleSubmit(async ({ email }) => {
        setApiError(null);
        try { await authApi.requestPasswordReset(email); setSent(true); } catch (error) { setApiError(error instanceof ApiError ? error.message : "Não foi possível enviar a solicitação."); }
      })}>
        <span className="grid size-11 place-items-center rounded-lg bg-primary-soft text-primary"><Mail className="size-5" aria-hidden /></span>
        <h1 className="mt-5 text-3xl font-extrabold tracking-tight">Recupere sua senha</h1>
        <p className="mt-2 text-sm leading-6 text-muted">Informe seu e-mail. Se a conta existir, o backend enviará as instruções configuradas.</p>
        {sent ? <p className="mt-6 rounded-lg bg-success-soft p-4 text-sm text-success" role="status">Solicitação recebida. Verifique seu e-mail.</p> : <div className="mt-6"><TextField id="reset-email" label="E-mail" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} /><InlineError>{apiError}</InlineError><NativeButton type="submit" className="mt-5 w-full" loading={isSubmitting}>Solicitar redefinição</NativeButton></div>}
      </form>
    </AuthFrame>
  );
}

export function PasswordResetConfirmPage() {
  return <TokenActionPage kind="password" />;
}

export function EmailConfirmationPage() {
  return <TokenActionPage kind="email" />;
}

function TokenActionPage({ kind }: { kind: "password" | "email" }) {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">(kind === "email" ? "loading" : "idle");
  const [message, setMessage] = useState<string | null>(null);
  const schema = z.object({ password: z.string().min(12, "Use pelo menos 12 caracteres.").max(72) });
  type Values = z.infer<typeof schema>;
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (kind !== "email") return;
    if (!token) { setStatus("error"); setMessage("Token de verificação ausente."); return; }
    void authApi.confirmEmail(token).then(() => { setStatus("success"); setMessage("E-mail verificado com sucesso."); }).catch((error: unknown) => { setStatus("error"); setMessage(error instanceof ApiError ? error.message : "Não foi possível verificar o e-mail."); });
  }, [kind, token]);

  return (
    <AuthFrame compact>
      <div className="mx-auto max-w-sm">
        <span className="grid size-11 place-items-center rounded-lg bg-primary-soft text-primary"><LockKeyhole className="size-5" aria-hidden /></span>
        <h1 className="mt-5 text-3xl font-extrabold tracking-tight">{kind === "email" ? "Verificação de e-mail" : "Defina uma nova senha"}</h1>
        {kind === "email" ? (
          <><p className="mt-4 text-sm text-muted" role="status">{status === "loading" ? "Confirmando seu e-mail…" : message}</p>{status !== "loading" ? <NativeButton to="/entrar" className="mt-6 w-full">Ir para o login</NativeButton> : null}</>
        ) : (
          <form className="mt-6" noValidate onSubmit={handleSubmit(async ({ password }) => {
            if (!token) { setStatus("error"); setMessage("Token de redefinição ausente."); return; }
            setMessage(null);
            try { await authApi.resetPassword(token, password); setStatus("success"); setMessage("Senha alterada. Você já pode entrar."); } catch (error) { setStatus("error"); setMessage(error instanceof ApiError ? error.message : "Não foi possível alterar a senha."); }
          })}>
            {status === "success" ? <><p className="rounded-lg bg-success-soft p-4 text-sm text-success" role="status">{message}</p><NativeButton to="/entrar" className="mt-5 w-full">Entrar</NativeButton></> : <><TextField id="new-password" label="Nova senha" type="password" autoComplete="new-password" hint="Use de 12 a 72 caracteres." error={errors.password?.message} {...register("password")} /><InlineError>{message}</InlineError><NativeButton type="submit" className="mt-5 w-full" loading={isSubmitting}>Salvar nova senha</NativeButton></>}
          </form>
        )}
      </div>
    </AuthFrame>
  );
}

function AuthFrame({ children, compact = false }: { children: import("react").ReactNode; compact?: boolean }) {
  return (
    <main className="min-h-screen bg-[#f7f7f8] px-4 py-6 sm:py-10">
      <div className="mx-auto max-w-6xl">
        <div className="mb-6 flex items-center justify-between gap-4">
          <Link to="/" className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"><BrandLogo /></Link>
          <Link to="/" className="inline-flex items-center gap-2 text-sm font-semibold text-muted hover:text-foreground"><ArrowLeft className="size-4" aria-hidden />Voltar</Link>
        </div>
        <div className={compact ? "surface mx-auto max-w-xl p-6 sm:p-10" : "overflow-hidden rounded-xl border border-border bg-white shadow-[0_18px_60px_rgba(50,20,18,0.08)]"}>{children}</div>
      </div>
    </main>
  );
}
