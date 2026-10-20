import { Share2 } from "lucide-react";
import { NativeButton } from "@/components/ui/native-button";

/**
 * Opens the phone's share sheet (WhatsApp, Telegram, SMS...) when the browser has one. Desktop browsers
 * without it go straight to WhatsApp, which is where a price tip usually ends up anyway.
 */
export function ShareButton({ text, url, label = "Compartilhar" }: { text: string; url: string; label?: string }) {
  const share = async () => {
    if (navigator.share) {
      try {
        await navigator.share({ text, url });
        return;
      } catch (error) {
        // Closing the share sheet is not a failure; anything else falls back to WhatsApp.
        if (error instanceof DOMException && error.name === "AbortError") return;
      }
    }
    window.open(`https://wa.me/?text=${encodeURIComponent(`${text} ${url}`)}`, "_blank", "noopener,noreferrer");
  };

  return (
    <NativeButton variant="secondary" size="sm" onClick={() => void share()}>
      <Share2 className="size-4" aria-hidden />
      {label}
    </NativeButton>
  );
}
