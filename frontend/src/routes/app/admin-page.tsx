import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Database, FileClock, RefreshCw, ShieldCheck, X } from "lucide-react";
import { useState } from "react";
import { ErrorState, InlineError, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, StatusBadge, SurfaceTitle } from "@/components/page/page-elements";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/brand";
import { adminApi, contributionApi } from "@/services/gomo-api";
import type { MarketHealth, MarketStatus } from "@/types/api";

export function AdminPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<"collections" | "moderation" | "sources" | "ingestion" | "audit">("collections");
  const audit = useQuery({ queryKey: ["admin-audit"], queryFn: () => adminApi.audit(0, 100), enabled: tab === "audit" });
  const queue = useQuery({ queryKey: ["admin-contributions", "PENDING"], queryFn: () => contributionApi.moderation("PENDING"), enabled: tab === "moderation" });
  const approve = useMutation({ mutationFn: contributionApi.approve, onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-contributions"] }) });
  const reject = useMutation({ mutationFn: (id: string) => contributionApi.reject(id, "Contribuição rejeitada pela moderação."), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-contributions"] }) });

  return (
    <>
      <PageHeading title="Administração" description="Acompanhe as coletas, modere contribuições, registre fontes e consulte a auditoria." />
      <div className="mb-6 flex gap-2 overflow-x-auto border-b border-border" role="tablist" aria-label="Áreas administrativas">
        {[{ id: "collections", label: "Coletas" }, { id: "moderation", label: "Moderação" }, { id: "sources", label: "Fontes" }, { id: "ingestion", label: "Ingestão" }, { id: "audit", label: "Auditoria" }].map((item) => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} className={`dashboard-tab min-h-11 shrink-0 border-b-2 px-3 text-sm font-bold ${tab === item.id ? "border-primary text-primary" : "border-transparent text-muted hover:text-foreground"}`} onClick={() => setTab(item.id as typeof tab)}>{item.label}</button>)}
      </div>
      {tab === "collections" ? <CollectionsPanel /> : null}
      {tab === "moderation" ? <section className="surface overflow-hidden"><SurfaceTitle title="Contribuições pendentes" description="Aprovação gera um registro de preço; rejeição mantém o motivo no histórico." />{queue.isLoading ? <div className="p-5"><SkeletonRows rows={5} /></div> : queue.isError ? <div className="p-5"><ErrorState retry={() => void queue.refetch()} /></div> : !queue.data?.content.length ? <div className="p-8 text-center text-sm text-muted">Não há contribuições pendentes.</div> : <div className="overflow-x-auto"><table className="data-table min-w-[56rem]"><caption className="sr-only">Fila de moderação</caption><thead><tr><th>Enviada</th><th>Produto</th><th>Loja</th><th>Preço</th><th>Ações</th></tr></thead><tbody>{queue.data.content.map((item) => <tr key={item.id}><td>{formatDate(item.submittedAt)}</td><td className="font-mono text-xs">{item.productId}</td><td className="font-mono text-xs">{item.storeId}</td><td>R$ {Number(item.promotionalPrice ?? item.regularPrice).toFixed(2).replace(".", ",")}</td><td><div className="flex gap-2"><NativeButton size="sm" loading={approve.isPending && approve.variables === item.id} onClick={() => approve.mutate(item.id)}><Check className="size-4" aria-hidden />Aprovar</NativeButton><NativeButton size="sm" variant="danger" loading={reject.isPending && reject.variables === item.id} onClick={() => reject.mutate(item.id)}><X className="size-4" aria-hidden />Rejeitar</NativeButton></div></td></tr>)}</tbody></table></div>}</section> : null}
      {tab === "sources" ? <SourceRegistrationForm /> : null}
      {tab === "ingestion" ? <IngestionForm /> : null}
      {tab === "audit" ? <section className="surface overflow-hidden"><SurfaceTitle title="Auditoria administrativa" description="Ações registradas pelo backend." />{audit.isLoading ? <div className="p-5"><SkeletonRows rows={6} /></div> : audit.isError ? <div className="p-5"><ErrorState retry={() => void audit.refetch()} /></div> : <div className="overflow-x-auto"><table className="data-table min-w-[52rem]"><caption className="sr-only">Registro de auditoria administrativa</caption><thead><tr><th>Data</th><th>Ação</th><th>Recurso</th><th>Responsável</th></tr></thead><tbody>{audit.data?.content.map((entry) => <tr key={entry.id}><td>{formatDate(entry.occurredAt)}</td><td><StatusBadge>{translateAction(entry.action)}</StatusBadge></td><td>{entry.resourceType}<p className="mt-1 font-mono text-xs text-muted">{entry.resourceId}</p></td><td className="font-mono text-xs">{entry.actorUserId}</td></tr>)}</tbody></table></div>}</section> : null}
    </>
  );
}

const MARKET_STATUS: Record<MarketStatus, { label: string; tone: "success" | "warning" | "danger" | "neutral"; help: string }> = {
  OK: { label: "Em dia", tone: "success", help: "Preços coletados nas últimas horas." },
  STALE: { label: "Atrasado", tone: "warning", help: "As últimas coletas falharam; os preços ainda valem, mas vão vencer." },
  DOWN: { label: "Fora do ar", tone: "danger", help: "Sem preço atual: o mercado saiu das buscas e comparações." },
  NO_SOURCE: { label: "Sem fonte", tone: "neutral", help: "Não há fonte pública de preços para esta unidade." },
};

function CollectionsPanel() {
  const queryClient = useQueryClient();
  const health = useQuery({ queryKey: ["admin-collection-health"], queryFn: adminApi.collectionHealth, refetchInterval: 60_000 });
  const collect = useMutation({
    mutationFn: (code: string) => adminApi.collect(code, true),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["admin-collection-health"] }),
  });
  if (health.isLoading) return <section className="surface p-5"><SkeletonRows rows={6} /></section>;
  if (health.isError || !health.data) return <section className="surface p-5"><ErrorState retry={() => void health.refetch()} /></section>;
  const report = health.data;
  const attention = report.markets.filter((market) => market.status === "STALE" || market.status === "DOWN").length;
  return (
    <section className="surface overflow-hidden">
      <SurfaceTitle
        title="Coletas por mercado"
        description={attention ? `${attention} mercado(s) precisam de atenção. Verificado em ${formatDate(report.checkedAt)}.` : `Todos os mercados com fonte estão em dia. Verificado em ${formatDate(report.checkedAt)}.`}
      />
      <InlineError>{collect.isError ? (collect.error instanceof ApiError ? collect.error.message : "Não foi possível iniciar a coleta.") : null}</InlineError>
      <div className="overflow-x-auto">
        <table className="data-table min-w-[60rem]">
          <caption className="sr-only">Situação das coletas de cada mercado</caption>
          <thead><tr><th>Mercado</th><th>Situação</th><th>Último preço</th><th>Produtos com preço atual</th><th>Última coleta</th><th>Ações</th></tr></thead>
          <tbody>{report.markets.map((market) => <MarketRow key={market.storeId ?? market.storeName} market={market} collecting={collect.isPending && market.collectors.some((collector) => collector.code === collect.variables)} onCollect={(code) => collect.mutate(code)} />)}</tbody>
        </table>
      </div>
    </section>
  );
}

function MarketRow({ market, collecting, onCollect }: { market: MarketHealth; collecting: boolean; onCollect: (code: string) => void }) {
  const status = MARKET_STATUS[market.status];
  const collector = market.collectors.find((item) => item.scheduled) ?? market.collectors[0];
  return (
    <tr>
      <td className="font-semibold">{market.storeName}</td>
      <td><StatusBadge tone={status.tone}>{status.label}</StatusBadge><p className="mt-1 max-w-56 text-xs text-muted">{status.help}</p></td>
      <td>{market.lastPriceAt ? formatDate(market.lastPriceAt) : "Nunca"}</td>
      <td>{market.currentProducts.toLocaleString("pt-BR")}</td>
      <td>
        {collector ? <>{collector.lastRunAt ? formatDate(collector.lastRunAt) : "Nunca"}{collector.lastStatus ? <span className="ml-2 font-mono text-xs text-muted">{collector.lastStatus}</span> : null}
          {collector.lastStatus === "FAILED" && collector.lastError ? <p className="mt-1 max-w-72 text-xs text-danger">{collector.lastError}</p> : null}</> : <span className="text-muted">—</span>}
      </td>
      <td>{collector?.scheduled ? <NativeButton size="sm" variant="secondary" loading={collecting} onClick={() => onCollect(collector.code)}><RefreshCw className="size-4" aria-hidden />Coletar agora</NativeButton> : null}</td>
    </tr>
  );
}

function SourceRegistrationForm() {
  const [form, setForm] = useState({ code: "", name: "", baseUrl: "", verifiedAt: new Date().toISOString().slice(0, 16) });
  const [message, setMessage] = useState<string | null>(null);
  const mutation = useMutation({ mutationFn: () => adminApi.registerSource({ ...form, verifiedAt: new Date(form.verifiedAt).toISOString() }), onSuccess: () => { setMessage("Fonte registrada com sucesso."); setForm({ code: "", name: "", baseUrl: "", verifiedAt: new Date().toISOString().slice(0, 16) }); }, onError: (error) => setMessage(error instanceof ApiError ? error.message : "Não foi possível registrar a fonte.") });
  return <section className="surface p-5 sm:p-6"><div className="flex items-center gap-3"><Database className="size-5 text-primary" aria-hidden /><div><h2 className="font-bold">Registrar fonte</h2><p className="text-sm text-muted">O backend não oferece listagem de fontes; esta tela integra o cadastro disponível.</p></div></div><form className="mt-6 grid gap-4 sm:grid-cols-2" onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }}><TextField id="source-code" label="Código" value={form.code} pattern="[a-z0-9][a-z0-9_-]{0,79}" onChange={(event) => setForm({ ...form, code: event.target.value })} required /><TextField id="source-name" label="Nome" value={form.name} maxLength={160} onChange={(event) => setForm({ ...form, name: event.target.value })} required /><TextField id="source-url" label="URL base" type="url" value={form.baseUrl} onChange={(event) => setForm({ ...form, baseUrl: event.target.value })} required /><TextField id="source-verified" label="Verificada em" type="datetime-local" value={form.verifiedAt} onChange={(event) => setForm({ ...form, verifiedAt: event.target.value })} required /><div className="sm:col-span-2"><InlineError>{message}</InlineError><NativeButton type="submit" loading={mutation.isPending} disabled={!form.code || !form.name || !form.baseUrl}>Registrar fonte</NativeButton></div></form></section>;
}

function IngestionForm() {
  const [kind, setKind] = useState<"chain" | "store" | "product" | "price">("chain");
  const [payload, setPayload] = useState("{\n  \"source\": {\n    \"sourceId\": \"\",\n    \"sourceReference\": \"\",\n    \"collectedAt\": \"\"\n  }\n}");
  const [message, setMessage] = useState<string | null>(null);
  const mutation = useMutation({ mutationFn: async () => { const parsed: unknown = JSON.parse(payload); if (kind === "chain") return adminApi.ingestChain(parsed); if (kind === "store") return adminApi.ingestStore(parsed); if (kind === "product") return adminApi.ingestProduct(parsed); return adminApi.recordPrice(parsed); }, onSuccess: () => setMessage("Dados enviados e aceitos pelo backend."), onError: (error) => setMessage(error instanceof SyntaxError ? "O JSON informado é inválido." : error instanceof ApiError ? error.message : "Não foi possível enviar os dados.") });
  return <section className="surface p-5 sm:p-6"><div className="flex items-center gap-3"><ShieldCheck className="size-5 text-primary" aria-hidden /><div><h2 className="font-bold">Ingestão administrativa</h2><p className="text-sm text-muted">Envie o contrato JSON exato dos endpoints de catálogo.</p></div></div><form className="mt-6" onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }}><div className="max-w-sm"><SelectField id="ingestion-kind" label="Tipo de dado" value={kind} onChange={(event) => setKind(event.target.value as typeof kind)}><option value="chain">Rede</option><option value="store">Supermercado</option><option value="product">Produto</option><option value="price">Preço</option></SelectField></div><label htmlFor="ingestion-payload" className="mt-4 block text-sm font-semibold">Contrato JSON</label><textarea id="ingestion-payload" className="field-control mt-1.5 min-h-72 font-mono text-sm" value={payload} onChange={(event) => setPayload(event.target.value)} spellCheck={false} /><InlineError>{message}</InlineError><NativeButton type="submit" className="mt-4" loading={mutation.isPending}><FileClock className="size-4" aria-hidden />Enviar dados</NativeButton></form></section>;
}

function translateAction(action: string) {
  return ({ SOURCE_REGISTERED: "Fonte registrada", SOURCE_STATUS_CHANGED: "Fonte atualizada", CHAIN_INGESTED: "Rede ingerida", STORE_INGESTED: "Loja ingerida", PRODUCT_INGESTED: "Produto ingerido", PRICE_RECORDED: "Preço registrado", CONTRIBUTION_APPROVED: "Contribuição aprovada", CONTRIBUTION_REJECTED: "Contribuição rejeitada" } as Record<string, string>)[action] ?? action;
}
