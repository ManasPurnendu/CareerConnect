import React from 'react';
import { Button } from './Button';

export interface ErrorStateProps {
  title?: string;
  message?: string;
  errorCode?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Something went wrong',
  message = 'An unexpected error occurred while loading this content. Please try again.',
  errorCode,
  onRetry,
  className = '',
}) => {
  return (
    <div
      role="alert"
      className={`rounded border border-status-rejected-border bg-status-rejected-bg/40 p-6 sm:p-10 text-center flex flex-col items-center justify-center max-w-lg mx-auto my-6 ${className}`}
    >
      <div className="w-12 h-12 rounded-full bg-status-rejected-bg border border-status-rejected-border flex items-center justify-center text-status-rejected mb-3">
        <span className="material-symbols-outlined !text-2xl">error_outline</span>
      </div>
      <h3 className="font-headline font-semibold text-base sm:text-lg text-slate-text mb-1">
        {title}
      </h3>
      {errorCode && (
        <span className="inline-block font-mono text-[11px] bg-white border border-status-rejected-border text-status-rejected px-2 py-0.5 rounded mb-2">
          Code: {errorCode}
        </span>
      )}
      <p className="text-xs sm:text-sm text-slate-muted max-w-sm mb-5 leading-relaxed">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry} leftIcon={<span className="material-symbols-outlined !text-base">refresh</span>}>
          Try Again
        </Button>
      )}
    </div>
  );
};
