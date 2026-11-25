import { LoaderCircle } from "lucide-react";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";

type NativeButtonVariant = "primary" | "secondary" | "ghost" | "danger";
type NativeButtonSize = "sm" | "md" | "lg" | "icon";

interface NativeButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  href?: string;
  loading?: boolean;
  glow?: boolean;
  to?: string;
  variant?: NativeButtonVariant;
  size?: NativeButtonSize;
}

const variantClasses: Record<NativeButtonVariant, string> = {
  primary:
    "border-primary bg-primary text-white hover:bg-primary-strong active:bg-primary-dark",
  secondary:
    "border-border-strong bg-white text-foreground hover:border-primary/35 hover:bg-primary-soft",
  ghost:
    "border-transparent bg-transparent text-muted hover:bg-surface-strong hover:text-foreground",
  danger:
    "border-danger bg-danger text-white hover:bg-danger-strong",
};

const sizeClasses: Record<NativeButtonSize, string> = {
  sm: "min-h-11 px-3 text-sm",
  md: "min-h-11 px-4 text-sm",
  lg: "min-h-12 px-5 text-base",
  icon: "size-11 p-0",
};

export function NativeButton({
  children,
  className,
  disabled,
  loading = false,
  glow = false,
  href,
  to,
  type = "button",
  variant = "primary",
  size = "md",
  ...props
}: NativeButtonProps) {
  const classes = cn(
    "native-button inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-lg border font-semibold",
    "transition-[background-color,border-color,color,box-shadow,transform] duration-150",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2",
    "disabled:pointer-events-none disabled:opacity-55",
    (disabled || loading) && "pointer-events-none opacity-55",
    variantClasses[variant],
    sizeClasses[size],
    glow && variant === "primary" && "native-button--glow",
    className,
  );

  const content = (
    <>
      {loading ? <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden /> : null}
      <span className="inline-flex items-center justify-center gap-2 whitespace-nowrap">{children}</span>
    </>
  );

  if (href) {
    return (
      <a
        href={href}
        className={classes}
        data-variant={variant}
        aria-disabled={disabled || loading || undefined}
      >
        {content}
      </a>
    );
  }

  if (to) {
    return (
      <Link
        to={to}
        className={classes}
        data-variant={variant}
        aria-disabled={disabled || loading || undefined}
      >
        {content}
      </Link>
    );
  }

  return (
    <button
      {...props}
      type={type}
      className={classes}
      data-variant={variant}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {content}
    </button>
  );
}
