import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MessageCircle } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { InlineError } from "@/components/ui/feedback";
import { TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { ApiError } from "@/lib/api";
import { whatsAppApi } from "@/services/gomo-api";
import { formatWhatsAppNumber, isMobileWithDdd } from "./whatsapp-number";

const schema = z.object({
  phoneNumber: z.string().trim().refine(isMobileWithDdd, "Informe um celular com DDD, como (24) 99999-8888."),
  consent: z.boolean().refine(Boolean, "Confirme que aceita receber os avisos no WhatsApp."),
});

type Values = z.infer<typeof schema>;

/** Hidden until the server has a WhatsApp Business number: nobody sees an option that cannot work yet. */
export function WhatsAppAlertsCard() {
  const queryClient = useQueryClient();
  const contact = useQuery({ queryKey: ["whatsapp"], queryFn: whatsAppApi.get });
  const [editing, setEditing] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { phoneNumber: "", consent: false },
  });

  const saved = (data: Awaited<ReturnType<typeof whatsAppApi.get>>) => {
    queryClient.setQueryData(["whatsapp"], data);
    setEditing(false);
    setApiError(null);
    reset();
  };

  const save = useMutation({
    mutationFn: whatsAppApi.save,
    onSuccess: saved,
    onError: (error) => setApiError(error instanceof ApiError ? error.message : "Não foi possível salvar o número."),
  });

  const remove = useMutation({ mutationFn: whatsAppApi.remove, onSuccess: saved });

  if (!contact.data?.available) return null;

  const phoneNumber = contact.data.phoneNumber;
  const showForm = editing || !phoneNumber;

  return (
    <section className="surface mb-5 p-4 sm:p-5" aria-labelledby="whatsapp-title">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary-soft text-primary">
          <MessageCircle className="size-5" aria-hidden />
        </span>
        <div className="min-w-0">
          <h2 id="whatsapp-title" className="font-bold">Avisos no WhatsApp</h2>
          <p className="mt-1 text-sm text-muted">
            {phoneNumber
              ? `Os avisos de preço chegam também no ${formatWhatsAppNumber(phoneNumber)}.`
              : "Receba os avisos de preço também no seu WhatsApp, além do sininho do Gomo."}
          </p>
        </div>
      </div>

      {showForm ? (
        <form
          className="mt-4 grid max-w-xl gap-3"
          onSubmit={handleSubmit((values) => save.mutate(values))}
          noValidate
        >
          <TextField
            id="whatsapp-number"
            label="Celular com DDD"
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            placeholder="(24) 99999-8888"
            error={errors.phoneNumber?.message}
            {...register("phoneNumber")}
          />
          <label className="flex cursor-pointer items-start gap-3 text-sm">
            <input type="checkbox" className="mt-0.5 size-4 shrink-0 accent-primary" {...register("consent")} />
            <span>Aceito receber no WhatsApp os avisos dos meus alertas de preço. Posso parar quando quiser, aqui mesmo.</span>
          </label>
          <InlineError>{errors.consent?.message ?? apiError}</InlineError>
          <div className="flex flex-wrap gap-2">
            <NativeButton type="submit" loading={save.isPending}>Salvar número</NativeButton>
            {phoneNumber ? (
              <NativeButton variant="secondary" onClick={() => { setEditing(false); reset(); }}>Cancelar</NativeButton>
            ) : null}
          </div>
        </form>
      ) : (
        <div className="mt-4 flex flex-wrap gap-2">
          <NativeButton variant="secondary" size="sm" onClick={() => setEditing(true)}>Trocar número</NativeButton>
          <NativeButton variant="secondary" size="sm" loading={remove.isPending} onClick={() => remove.mutate()}>
            Parar avisos no WhatsApp
          </NativeButton>
        </div>
      )}
    </section>
  );
}
