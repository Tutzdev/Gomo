import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Link2, Lock, UserMinus, Users } from "lucide-react";
import { useState } from "react";
import { InlineError } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { ShareButton } from "@/components/ui/share-button";
import { Sheet } from "@/components/ui/sheet";
import { useUpsell } from "@/features/plan/upsell-context";
import { usePlan } from "@/features/plan/use-plan";
import { formatDate } from "@/lib/brand";
import { siteUrl } from "@/lib/site-url";
import { sharingApi } from "@/services/gomo-api";
import type { ShoppingList } from "@/types/api";

/**
 * Who the list is shared with, and the invite link. The token goes in the address fragment (after #), which the
 * browser never sends to the server: the link cannot end up in a log.
 */
export function ShareListSheet({ list, open, onOpenChange }: { list: ShoppingList; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { premium } = usePlan();
  const { showUpsell } = useUpsell();
  const queryClient = useQueryClient();
  const [link, setLink] = useState<{ url: string; expiresAt: string } | null>(null);
  const [copied, setCopied] = useState(false);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["shopping-list", list.id] });
  const invite = useMutation({
    mutationFn: () => sharingApi.invite(list.id),
    onSuccess: (created) => setLink({ url: `${siteUrl("/app/convite")}#${created.token}`, expiresAt: created.expiresAt }),
  });
  const stop = useMutation({ mutationFn: () => sharingApi.stopSharing(list.id), onSuccess: () => setLink(null) });
  const remove = useMutation({ mutationFn: (userId: string) => sharingApi.removeMember(list.id, userId), onSuccess: refresh });
  const copy = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link.url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Without clipboard access the link is still on screen to select.
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange} title="Compartilhar a lista" description="Quem entrar adiciona itens e marca o carrinho junto com você.">
      <div className="space-y-6">
        <section aria-labelledby="share-members">
          <h3 id="share-members" className="flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-muted">
            <Users className="size-4" aria-hidden />
            Na lista ({list.members.length + 1})
          </h3>
          <ul className="mt-2 divide-y divide-border rounded-lg border border-border">
            <li className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
              <span className="font-semibold">Você</span>
              <span className="text-xs text-muted">criou a lista</span>
            </li>
            {list.members.map((member) => (
              <li key={member.userId} className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
                <span className="min-w-0">
                  <span className="block truncate font-semibold">{member.name}</span>
                  <span className="text-xs text-muted">entrou em {formatDate(member.joinedAt)}</span>
                </span>
                <NativeButton variant="ghost" size="sm" loading={remove.isPending && remove.variables === member.userId} onClick={() => remove.mutate(member.userId)} aria-label={`Tirar ${member.name} da lista`}>
                  <UserMinus className="size-4" aria-hidden />
                  Tirar
                </NativeButton>
              </li>
            ))}
          </ul>
          <InlineError>{remove.error?.message}</InlineError>
        </section>

        <section aria-labelledby="share-link">
          <h3 id="share-link" className="flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-muted">
            <Link2 className="size-4" aria-hidden />
            Link de convite
          </h3>
          {!premium ? (
            <div className="mt-2 rounded-lg bg-primary-soft p-4 text-sm">
              <p>Compartilhar faz parte do Premium. Quem recebe o convite não precisa assinar.</p>
              <NativeButton className="mt-3" size="sm" variant="secondary" onClick={() => showUpsell("SHARED_LISTS")}>
                <Lock className="size-4" aria-hidden />
                Conhecer o Premium
              </NativeButton>
            </div>
          ) : link ? (
            <div className="mt-2 space-y-3">
              <p className="break-all rounded-lg bg-surface-strong p-3 font-mono text-xs">{link.url}</p>
              <p className="text-xs text-muted">Vale até {formatDate(link.expiresAt)}. Quem abrir o link com uma conta do Gomo entra na lista.</p>
              <div className="flex flex-wrap gap-2">
                <NativeButton size="sm" onClick={() => void copy()}>
                  {copied ? <Check className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}
                  {copied ? "Copiado" : "Copiar link"}
                </NativeButton>
                <ShareButton label="Mandar no WhatsApp" text={`Entra na nossa lista "${list.name}" no Gomo:`} url={link.url} />
              </div>
            </div>
          ) : (
            <div className="mt-2 space-y-2">
              <p className="text-sm text-muted">Crie um link e mande para quem compra com você. Um link novo cancela o anterior.</p>
              <NativeButton size="sm" loading={invite.isPending} onClick={() => invite.mutate()}>
                <Link2 className="size-4" aria-hidden />
                Criar link de convite
              </NativeButton>
            </div>
          )}
          <InlineError>{invite.error?.message}</InlineError>
          {premium ? (
            <button type="button" className="mt-4 text-sm font-semibold text-muted underline-offset-2 hover:text-foreground hover:underline" onClick={() => stop.mutate()}>
              {stop.isSuccess ? "Link cancelado: ninguém novo entra." : "Parar de compartilhar (cancelar o link)"}
            </button>
          ) : null}
        </section>
      </div>
    </Sheet>
  );
}
