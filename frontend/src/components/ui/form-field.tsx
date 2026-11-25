import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";
import { InlineError } from "@/components/ui/feedback";
import { cn } from "@/lib/cn";

interface FieldFrameProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

function FieldFrame({ id, label, hint, error, children }: FieldFrameProps) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-semibold text-foreground">
        {label}
      </label>
      {children}
      {hint && !error ? <p id={`${id}-hint`} className="mt-1.5 text-xs text-muted">{hint}</p> : null}
      <InlineError id={`${id}-error`}>{error}</InlineError>
    </div>
  );
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
}

export function TextField({ id, label, error, hint, className, ...props }: TextFieldProps) {
  if (!id) throw new Error("TextField requires an id");

  return (
    <FieldFrame id={id} label={label} hint={hint} error={error}>
      <input
        {...props}
        id={id}
        className={cn("field-control", className)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
      />
    </FieldFrame>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}

export function SelectField({ id, label, error, hint, children, className, ...props }: SelectFieldProps) {
  if (!id) throw new Error("SelectField requires an id");

  return (
    <FieldFrame id={id} label={label} hint={hint} error={error}>
      <select
        {...props}
        id={id}
        className={cn("field-control", className)}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
      >
        {children}
      </select>
    </FieldFrame>
  );
}
