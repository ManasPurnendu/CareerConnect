import { forwardRef, type InputHTMLAttributes } from 'react';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  description?: string;
  error?: string;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ label, description, error, id, className = '', disabled, ...props }, ref) => {
    const generatedId = id || `checkbox-${label.toLowerCase().replace(/\s+/g, '-')}`;

    return (
      <div className="flex flex-col gap-1">
        <label htmlFor={generatedId} className="flex items-start gap-2.5 cursor-pointer select-none">
          <input
            ref={ref}
            type="checkbox"
            id={generatedId}
            disabled={disabled}
            className={`mt-0.5 h-4 w-4 rounded border-outline-warm text-primary focus:ring-primary focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
            {...props}
          />
          <div className="flex flex-col">
            <span className={`text-sm font-medium ${disabled ? 'text-slate-muted' : 'text-slate-text'}`}>
              {label}
            </span>
            {description && <span className="text-xs text-slate-muted">{description}</span>}
          </div>
        </label>
        {error && <p className="text-xs text-status-rejected font-medium pl-6.5">{error}</p>}
      </div>
    );
  }
);

Checkbox.displayName = 'Checkbox';
