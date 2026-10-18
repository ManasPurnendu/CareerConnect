import { forwardRef, type SelectHTMLAttributes } from 'react';

export interface SelectOption {
  value: string | number;
  label: string;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  helperText?: string;
  options: SelectOption[];
  placeholder?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, helperText, options, placeholder, id, className = '', disabled, ...props }, ref) => {
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
        <div className="relative">
          <select
            ref={ref}
            id={generatedId}
            disabled={disabled}
            aria-invalid={!!error}
            aria-describedby={error ? `${generatedId}-error` : helperText ? `${generatedId}-helper` : undefined}
            className={`w-full min-h-[44px] px-3.5 py-2 text-sm bg-white border rounded text-slate-text appearance-none transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent disabled:bg-[#F3F4F1] disabled:text-slate-muted disabled:cursor-not-allowed ${
              error ? 'border-status-rejected focus:ring-status-rejected' : 'border-outline-warm hover:border-outline'
            } ${className}`}
            {...props}
          >
            {placeholder && (
              <option value="" disabled>
                {placeholder}
              </option>
            )}
            {options.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <span className="absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none text-slate-muted">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </span>
        </div>
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

Select.displayName = 'Select';
