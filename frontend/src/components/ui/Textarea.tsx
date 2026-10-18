import { forwardRef, type TextareaHTMLAttributes } from 'react';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ label, error, helperText, id, className = '', disabled, rows = 4, ...props }, ref) => {
    const generatedId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="w-full flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={generatedId}
            className="text-xs font-semibold text-slate-text tracking-wider uppercase font-sans select-none"
          >
            {label}
            {props.required && <span className="text-status-rejected ml-0.5">*</span>}
          </label>
        )}
        <textarea
          ref={ref}
          id={generatedId}
          rows={rows}
          disabled={disabled}
          aria-invalid={!!error}
          aria-describedby={error ? `${generatedId}-error` : helperText ? `${generatedId}-helper` : undefined}
          className={`w-full px-3.5 py-2.5 text-sm bg-white border rounded text-slate-text placeholder:text-slate-muted/70 transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent disabled:bg-[#F3F4F1] disabled:text-slate-muted disabled:cursor-not-allowed ${
            error ? 'border-status-rejected focus:ring-status-rejected' : 'border-outline-warm hover:border-outline'
          } ${className}`}
          {...props}
        />
        {error && (
          <p id={`${generatedId}-error`} className="text-xs text-status-rejected font-medium mt-0.5">
            {error}
          </p>
        )}
        {helperText && !error && (
          <p id={`${generatedId}-helper`} className="text-xs text-slate-muted mt-0.5">
            {helperText}
          </p>
        )}
      </div>
    );
  }
);

Textarea.displayName = 'Textarea';
