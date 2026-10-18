import React from 'react';

export type AlertVariant = 'info' | 'success' | 'warning' | 'error';

export interface AlertProps {
  variant?: AlertVariant;
  title?: string;
  children: React.ReactNode;
  onClose?: () => void;
  className?: string;
}

export const Alert: React.FC<AlertProps> = ({
  variant = 'info',
  title,
  children,
  onClose,
  className = '',
}) => {
  const variantConfig: Record<
    AlertVariant,
    { bg: string; border: string; text: string; iconColor: string; icon: string }
  > = {
    info: {
      bg: 'bg-status-interview-bg',
      border: 'border-status-interview-border',
      text: 'text-[#1E40AF]',
      iconColor: 'text-status-interview',
      icon: 'info',
    },
    success: {
      bg: 'bg-status-offered-bg',
      border: 'border-status-offered-border',
      text: 'text-[#065F46]',
      iconColor: 'text-status-offered',
      icon: 'check_circle',
    },
    warning: {
      bg: 'bg-status-review-bg',
      border: 'border-status-review-border',
      text: 'text-[#92400E]',
      iconColor: 'text-status-review',
      icon: 'warning',
    },
    error: {
      bg: 'bg-status-rejected-bg',
      border: 'border-status-rejected-border',
      text: 'text-[#991B1B]',
      iconColor: 'text-status-rejected',
      icon: 'error',
    },
  };

  const config = variantConfig[variant];

  return (
    <div
      role="alert"
      className={`rounded border p-3.5 sm:p-4 flex items-start gap-3 ${config.bg} ${config.border} ${config.text} ${className}`}
    >
      <span className={`material-symbols-outlined shrink-0 !text-xl ${config.iconColor}`}>
        {config.icon}
      </span>
      <div className="flex-1 text-xs sm:text-sm">
        {title && <h4 className="font-semibold mb-0.5 leading-snug">{title}</h4>}
        <div className="opacity-95 leading-relaxed">{children}</div>
      </div>
      {onClose && (
        <button
          onClick={onClose}
          aria-label="Dismiss alert"
          className="shrink-0 p-1 rounded hover:bg-black/5 transition-colors -mr-1 -mt-1 text-current opacity-70 hover:opacity-100"
        >
          <span className="material-symbols-outlined !text-lg">close</span>
        </button>
      )}
    </div>
  );
};
