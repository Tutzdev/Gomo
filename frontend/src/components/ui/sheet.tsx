import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

interface SheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  side?: "left" | "right";
  onCloseAutoFocus?: (event: Event) => void;
  bodyClassName?: string;
  className?: string;
}

export function Sheet({
  open,
  onOpenChange,
  title,
  description,
  children,
  side = "right",
  onCloseAutoFocus,
  bodyClassName,
  className,
}: SheetProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/45 data-[state=closed]:animate-out data-[state=open]:animate-in motion-reduce:animate-none" />
        <Dialog.Content
          onCloseAutoFocus={onCloseAutoFocus}
          className={cn(
            "fixed inset-y-0 z-50 flex w-[min(88vw,22rem)] flex-col bg-white p-5 shadow-2xl",
            "focus:outline-none motion-safe:duration-200",
            side === "right" ? "right-0 border-l border-border" : "left-0 border-r border-border",
            className,
          )}
        >
          <div className="flex shrink-0 items-start justify-between gap-4">
            <div>
              <Dialog.Title className="font-semibold text-foreground">{title}</Dialog.Title>
              {description ? (
                <Dialog.Description className="mt-1 text-sm text-muted">{description}</Dialog.Description>
              ) : null}
            </div>
            <Dialog.Close className="icon-button" aria-label={`Fechar ${title.toLowerCase()}`}>
              <X className="size-5" aria-hidden />
            </Dialog.Close>
          </div>
          <div className={cn("min-h-0 flex-1", bodyClassName ?? "mt-6 overflow-y-auto")}>{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
