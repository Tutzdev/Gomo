import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, Plus } from "lucide-react";
import { useState } from "react";
import { EmptyState, ErrorState, InlineError, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, StatusBadge } from "@/components/page/page-elements";
import { ProductPicker } from "@/features/catalog/product-picker";
import { ApiError } from "@/lib/api";
import { formatCurrency, formatDate } from "@/lib/brand";
import { alertApi, catalogApi } from "@/services/gomo-api";
import { PlanLimitNotice } from "@/features/plan/plan-locks";
import { useLimitUpsell } from "@/features/plan/use-limit-upsell";
import { planLimitOf, usePlan } from "@/features/plan/use-plan";

const FREE_ACTIVE_ALERTS = 2;

export function AlertsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [productId, setProductId] = useState("");
  const [cityId, setCityId] = useState("");
  const [targetPrice, setTargetPrice] = useState("");
  const [apiError, setApiError] = useState<string | null>(null);
  const alerts = useQuery({ queryKey: ["alerts", 0], queryFn: () => alertApi.list(0, 100) });
  const cities = useQuery({ queryKey: ["cities", "alerts"], queryFn: () => catalogApi.cities(undefined, 0, 100) });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["alerts"] });
  const { premium } = usePlan();
  const alertLimit = useLimitUpsell();
  const activeAlerts = alerts.data?.content.filter((alert) => alert.active).length ?? 0;
  const openNewAlertForm = () => {
    if (!premium && activeAlerts >= FREE_ACTIVE_ALERTS) {
      setShowForm(false);
      alertLimit.block("ACTIVE_ALERTS");
      return;
    }
    alertLimit.clear();
    setShowForm((value) => !value);
  };
  const createAlert = useMutation({ mutationFn: () => alertApi.create({ productId, cityId, targetPrice: Number(targetPrice) }), onSuccess: async () => { setShowForm(false); setProductId(""); setCityId(""); setTargetPrice(""); setApiError(null); await invalidate(); }, onError: (error) => {
    if (planLimitOf(error) === "ACTIVE_ALERTS") { setShowForm(false); alertLimit.block("ACTIVE_ALERTS"); return; }
    setApiError(error instanceof ApiError ? error.message : "Não foi possível criar o alerta.");
  } });
  const deactivate = useMutation({ mutationFn: alertApi.deactivate, onSuccess: invalidate });
  const productDetails = useQueries({ queries: [...new Set(alerts.data?.content.map((item) => item.productId) ?? [])].map((id) => ({ queryKey: ["product", id], queryFn: () => catalogApi.product(id) })) });
  const productNames = new Map(productDetails.flatMap((result) => result.data ? [[result.data.id, result.data.name]] : []));
  const cityNames = new Map(cities.data?.content.map((city) => [city.id, city.name]));

  return (
    <>
      <PageHeading title="Alertas de preço" description="Defina o preço desejado e acompanhe os alertas gerados pelos registros disponíveis." action={<NativeButton onClick={openNewAlertForm}><Plus className="size-4" aria-hidden />Novo alerta</NativeButton>} />
      {!premium ? <p className="-mt-2 mb-5 text-sm text-muted">Plano grátis: {Math.min(activeAlerts, FREE_ACTIVE_ALERTS)} de {FREE_ACTIVE_ALERTS} alertas ativos, com aviso no máximo 1 vez ao dia. No Premium, sem limite e a cada coleta.</p> : null}
      {alertLimit.notice ? <div className="mb-5"><PlanLimitNotice>{alertLimit.notice}</PlanLimitNotice></div> : null}
      {showForm ? <form className="surface mb-5 grid gap-4 p-4 sm:p-5 lg:grid-cols-[1fr_1fr_0.65fr_auto] lg:items-end" onSubmit={(event) => { event.preventDefault(); if (productId && cityId && Number(targetPrice) > 0) createAlert.mutate(); }}><ProductPicker id="alert-product" value={productId} onChange={setProductId} /><SelectField id="alert-city" label="Cidade" value={cityId} onChange={(event) => setCityId(event.target.value)} required><option value="">Selecione</option>{cities.data?.content.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}</SelectField><TextField id="alert-price" label="Preço desejado" type="number" min="0.01" step="0.01" value={targetPrice} onChange={(event) => setTargetPrice(event.target.value)} required /><NativeButton type="submit" loading={createAlert.isPending} disabled={!productId || !cityId || Number(targetPrice) <= 0}>Criar alerta</NativeButton><div className="lg:col-span-4"><InlineError>{apiError}</InlineError></div></form> : null}
      {alerts.isLoading ? <SkeletonRows rows={6} /> : alerts.isError ? <ErrorState retry={() => void alerts.refetch()} /> : !alerts.data?.content.length ? <EmptyState title="Nenhum alerta criado" description="Crie um alerta para acompanhar quando um produto atingir o preço desejado." action={<NativeButton onClick={openNewAlertForm}>Criar alerta</NativeButton>} /> : <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{alerts.data.content.map((alert) => <article key={alert.id} className="surface p-5"><div className="flex items-start justify-between gap-4"><span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary"><BellRing className="size-5" aria-hidden /></span><StatusBadge tone={alert.active ? "success" : "neutral"}>{alert.active ? "Ativo" : "Desativado"}</StatusBadge></div><h2 className="mt-4 font-bold">{productNames.get(alert.productId) ?? "Produto"}</h2><p className="mt-1 text-sm text-muted">{cityNames.get(alert.cityId) ?? "Cidade não identificada"}</p><p className="mt-4 text-2xl font-extrabold">{formatCurrency(alert.targetPrice)}</p><p className="mt-3 text-xs text-muted">Criado em {formatDate(alert.createdAt)}</p>{alert.active ? <NativeButton variant="secondary" size="sm" className="mt-5" loading={deactivate.isPending && deactivate.variables === alert.id} onClick={() => deactivate.mutate(alert.id)}>Desativar alerta</NativeButton> : null}</article>)}</div>}
    </>
  );
}
