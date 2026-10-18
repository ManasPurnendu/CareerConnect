import React, { forwardRef, type InputHTMLAttributes } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, helperText, leftIcon, rightIcon, id, className = '', disabled, ...props }, ref) => {
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
        <div className="relative flex items-center">
          {leftIcon && (
            <span className="absolute left-3 text-slate-muted pointer-events-none flex items-center justify-center">
              {leftIcon}
            </span>
          )}
          <input
            ref={ref}
            id={generatedId}
            disabled={disabled}
            aria-invalid={!!error}
            aria-describedby={error ? `${generatedId}-error` : helperText ? `${generatedId}-helper` : undefined}
            className={`w-full min-h-[44px] px-3.5 py-2 text-sm bg-white border rounded text-slate-text placeholder:text-slate-muted/70 transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent disabled:bg-[#F3F4F1] disabled:text-slate-muted disabled:cursor-not-allowed ${
              leftIcon ? 'pl-10' : ''
            } ${rightIcon ? 'pr-10' : ''} ${
              error ? 'border-status-rejected focus:ring-status-rejected' : 'border-outline-warm hover:border-outline'
            } ${className}`}
            {...props}
          />
          {rightIcon && (
            <span className="absolute right-3 text-slate-muted flex items-center justify-center">
              {rightIcon}
            </span>
          )}
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

Input.displayName = 'Input';
