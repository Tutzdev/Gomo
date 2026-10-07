import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, MailCheck, MapPin, Trash2, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState, InlineError, LoadingState } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, StatusBadge } from "@/components/page/page-elements";
import { useAuth } from "@/features/auth/auth-context";
import { ApiError } from "@/lib/api";
import { authApi, catalogApi, preferenceApi, subscriptionApi } from "@/services/gomo-api";
import type { User } from "@/types/api";

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const queryClient = useQueryClient();
  const [name, setName] = useState(user?.name ?? "");
  const [preferredCityId, setPreferredCityId] = useState("");
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [emailMessage, setEmailMessage] = useState<string | null>(null);
  const preferences = useQuery({ queryKey: ["preferences"], queryFn: preferenceApi.get });
  const cities = useQuery({ queryKey: ["cities", "profile"], queryFn: () => catalogApi.cities(undefined, 0, 100) });

  useEffect(() => { if (preferences.data) setPreferredCityId(preferences.data.preferredCityId ?? ""); }, [preferences.data]);
  const updateProfile = useMutation({ mutationFn: () => authApi.updateUser(name.trim()), onSuccess: async () => { setProfileMessage("Nome atualizado com sucesso."); await refreshUser(); }, onError: (error) => setProfileMessage(error instanceof ApiError ? error.message : "Não foi possível atualizar o nome.") });
  const updateCity = useMutation({ mutationFn: () => preferredCityId ? preferenceApi.setCity(preferredCityId) : preferenceApi.clearCity(), onSuccess: async (data) => { queryClient.setQueryData(["preferences"], data); setProfileMessage("Cidade preferida atualizada."); }, onError: (error) => setProfileMessage(error instanceof ApiError ? error.message : "Não foi possível atualizar a cidade.") });
  const requestVerification = useMutation({ mutationFn: authApi.requestEmailVerification, onSuccess: () => setEmailMessage("Solicitação enviada. Verifique seu e-mail."), onError: (error) => setEmailMessage(error instanceof ApiError ? error.message : "Não foi possível solicitar a verificação.") });

  if (!user || preferences.isLoading || cities.isLoading) return <LoadingState />;
  if (preferences.isError || cities.isError) return <ErrorState retry={() => void Promise.all([preferences.refetch(), cities.refetch()])} />;

  return (
    <>
      <PageHeading title="Perfil e preferências" description="Mantenha seus dados, cidade e estado de verificação atualizados." />
      <div className="grid gap-6 xl:grid-cols-2">
        <section className="surface p-5 sm:p-6">
          <div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary"><UserRound className="size-5" aria-hidden /></span><div><h2 className="font-bold">Dados da conta</h2><p className="text-sm text-muted">Informações suportadas pelo backend.</p></div></div>
          <form className="mt-6 space-y-4" onSubmit={(event) => { event.preventDefault(); updateProfile.mutate(); }}>
            <TextField id="profile-name" label="Nome" value={name} maxLength={120} onChange={(event) => setName(event.target.value)} required />
            <TextField id="profile-email" label="E-mail" value={user.email} disabled readOnly />
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border p-4"><div><p className="text-sm font-semibold">Verificação do e-mail</p><div className="mt-2"><StatusBadge tone={user.emailVerified ? "success" : "warning"}>{user.emailVerified ? "Verificado" : "Pendente"}</StatusBadge></div></div>{!user.emailVerified ? <NativeButton type="button" variant="secondary" loading={requestVerification.isPending} onClick={() => requestVerification.mutate()}><MailCheck className="size-4" aria-hidden />Reenviar verificação</NativeButton> : <BadgeCheck className="size-6 text-success" aria-hidden />}</div>
            <InlineError>{emailMessage}</InlineError>
            <NativeButton type="submit" loading={updateProfile.isPending} disabled={!name.trim()}>Salvar nome</NativeButton>
          </form>
        </section>

        <section className="surface p-5 sm:p-6">
          <div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary"><MapPin className="size-5" aria-hidden /></span><div><h2 className="font-bold">Preferências de compra</h2><p className="text-sm text-muted">A cidade orienta atalhos e comparações.</p></div></div>
          <form className="mt-6" onSubmit={(event) => { event.preventDefault(); updateCity.mutate(); }}>
            <SelectField id="preferred-city" label="Cidade preferida" value={preferredCityId} onChange={(event) => setPreferredCityId(event.target.value)}><option value="">Nenhuma cidade definida</option>{cities.data?.content.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}</SelectField>
            <p className="mt-4 text-sm text-muted">Lojas favoritas: <strong className="text-foreground">{preferences.data?.favoriteStoreIds.length ?? 0}</strong></p>
            <NativeButton type="submit" className="mt-5" loading={updateCity.isPending}>Salvar preferência</NativeButton>
          </form>
          <PlanSection user={user} onChanged={async () => { await refreshUser(); await queryClient.invalidateQueries(); }} />
          <InlineError>{profileMessage}</InlineError>
        </section>
      </div>
      <DeleteAccountSection />
    </>
  );
}

function DeleteAccountSection() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [confirming, setConfirming] = useState(false);
  const deletion = useMutation({
    mutationFn: () => authApi.deleteAccount(password),
    onSuccess: async () => {
      // The server already ended every session; this only clears the browser.
      await logout().catch(() => undefined);
      navigate("/", { replace: true });
    },
  });
  const error = deletion.error instanceof ApiError ? deletion.error.message : deletion.error ? "Não foi possível excluir a conta." : undefined;

  return (
    <section className="surface mt-6 border-danger/25 p-5 sm:p-6">
      <div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-lg bg-danger-soft text-danger"><Trash2 className="size-5" aria-hidden /></span><div><h2 className="font-bold">Excluir conta</h2><p className="text-sm text-muted">Apaga na hora suas listas, alertas, preferências e sessões. Não dá para desfazer.</p></div></div>
      <form className="mt-6 grid gap-4 sm:max-w-md" onSubmit={(event) => { event.preventDefault(); deletion.reset(); setConfirming(true); }}>
        <TextField id="delete-password" label="Confirme com sua senha" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
        <p className="text-xs leading-5 text-muted">Preços que você enviou como contribuição continuam no histórico, sem seu nome. Veja a <Link to="/privacidade" className="font-semibold text-primary hover:underline">Política de Privacidade</Link>.</p>
        <NativeButton type="submit" variant="danger" disabled={!password}>Excluir minha conta</NativeButton>
      </form>
      <ConfirmDialog open={confirming} onOpenChange={setConfirming} title="Excluir sua conta?" description="Suas listas, alertas e preferências serão apagados agora. Você poderá criar uma conta nova depois, mas não recuperar esta." confirmLabel="Excluir conta" loading={deletion.isPending} error={error} onConfirm={() => deletion.mutate()} />
    </section>
  );
}

function PlanSection({ user, onChanged }: { user: User; onChanged: () => Promise<void> }) {
  const cancelTrial = useMutation({ mutationFn: subscriptionApi.cancelTrial, onSuccess: onChanged });
  const trialEndsAt = user.trialEndsAt
    ? new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "long" }).format(new Date(user.trialEndsAt))
    : null;

  return (
    <div className="mt-8 border-t border-border pt-6">
      <h3 className="font-bold">Plano</h3>
      <div className="mt-3">
        <StatusBadge tone={user.premium ? "success" : "neutral"}>
          {user.premiumSource === "TRIAL" ? "Teste Premium" : user.premium ? "Premium" : "Plano grátis"}
        </StatusBadge>
      </div>
      <p className="mt-3 text-sm leading-6 text-muted">
        {user.premiumSource === "TRIAL"
          ? `Seu teste vai até ${trialEndsAt}. Depois disso a conta volta ao plano grátis, sem cobrança.`
          : user.premium
            ? "Sua conta tem todos os recursos do Premium."
            : "Compare os 3 mercados mais baratos, com 1 lista de até 10 produtos e 2 alertas."}
      </p>
      {user.premiumSource === "TRIAL" ? (
        <>
          <NativeButton variant="secondary" className="mt-4" loading={cancelTrial.isPending} onClick={() => cancelTrial.mutate()}>
            Cancelar teste agora
          </NativeButton>
          <InlineError>{cancelTrial.error?.message}</InlineError>
        </>
      ) : !user.premium ? (
        <NativeButton to="/assinar?origem=perfil" className="mt-4">
          {user.trialAvailable ? "Testar o Premium por 7 dias" : "Conhecer o Premium"}
        </NativeButton>
      ) : null}
    </div>
  );
}
