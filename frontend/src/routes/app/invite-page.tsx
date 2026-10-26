import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ListChecks, Users } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { EmptyState, InlineError, LoadingState } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/brand";
import { sharingApi } from "@/services/gomo-api";

/**
 * /app/convite#<token>: an invite to a shared list. The token comes after the #, which the browser never sends to
 * the server, and goes to the API in a request body.
 */
export function InvitePage() {
  const { hash } = useLocation();
  const token = decodeURIComponent(hash.replace(/^#/, "")).trim();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const preview = useQuery({
    queryKey: ["list-invite", token],
    queryFn: () => sharingApi.preview(token),
    enabled: Boolean(token),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 1,
  });
  const accept = useMutation({
    mutationFn: () => sharingApi.accept(token),
    onSuccess: async ({ listId }) => {
      await queryClient.invalidateQueries({ queryKey: ["shopping-lists"] });
      navigate(`/app/listas/${listId}`, { replace: true });
    },
  });

  if (!token || preview.isError) {
    return (
      <EmptyState
        title="Este convite não vale mais"
        description={preview.error?.message ?? "O link está incompleto. Peça um link novo a quem compartilhou a lista."}
        action={<NativeButton to="/app/listas">Ver minhas listas</NativeButton>}
      />
    );
  }
  if (!preview.data) return <LoadingState label="Abrindo o convite…" />;
  const invite = preview.data;

  return (
    <section className="surface mx-auto max-w-lg p-6 text-center">
      <span className="mx-auto grid size-12 place-items-center rounded-full bg-primary-soft text-primary">
        <Users className="size-6" aria-hidden />
      </span>
      <h1 className="mt-4 text-2xl font-extrabold">{invite.ownerName} compartilhou uma lista com você</h1>
      <p className="mt-2 flex items-center justify-center gap-1.5 font-semibold">
        <ListChecks className="size-4 text-primary" aria-hidden />
        {invite.listName}
      </p>
      <p className="mt-1 text-sm text-muted">
        {invite.items} {invite.items === 1 ? "produto" : "produtos"} · {invite.members + 1} {invite.members === 0 ? "pessoa" : "pessoas"} na lista · convite vale até {formatDate(invite.expiresAt)}
      </p>
      <p className="mt-4 text-sm">Na lista você adiciona o que falta e, no mercado, vê o que já foi para o carrinho. Não precisa assinar nada.</p>
      <div className="mt-6">
        {invite.alreadyIn ? (
          <NativeButton to={`/app/listas/${invite.listId}`}>Abrir a lista</NativeButton>
        ) : (
          <NativeButton loading={accept.isPending} onClick={() => accept.mutate()}>Entrar na lista</NativeButton>
        )}
      </div>
      <InlineError>{accept.error?.message}</InlineError>
    </section>
  );
}
