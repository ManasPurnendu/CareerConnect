import React from 'react';

export interface LoadingStateProps {
  message?: string;
  className?: string;
}

export const LoadingState: React.FC<LoadingStateProps> = ({
  message = 'Loading...',
  className = '',
}) => {
  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex flex-col items-center justify-center p-8 sm:p-12 gap-3 text-center ${className}`}
    >
      <div className="w-8 h-8 border-3 border-outline-warm border-t-primary rounded-full animate-spin" />
      <p className="text-xs font-medium text-slate-muted font-sans tracking-wide">{message}</p>
      <span className="sr-only">{message}</span>
    </div>
  );
};
