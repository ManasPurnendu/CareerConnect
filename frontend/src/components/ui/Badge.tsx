import React from 'react';

export type BadgeVariant = 'default' | 'neutral' | 'success' | 'warning' | 'info' | 'danger';
export type BadgeSize = 'sm' | 'md';

export interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  size?: BadgeSize;
  icon?: React.ReactNode;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'default',
  size = 'sm',
  icon,
  className = '',
}) => {
  const baseStyles = 'inline-flex items-center font-medium rounded font-sans transition-colors';

  const sizeStyles: Record<BadgeSize, string> = {
    sm: 'text-[11px] leading-3.5 px-2 py-0.5 gap-1',
    md: 'text-xs leading-4 px-2.5 py-1 gap-1.5',
  };

  const variantStyles: Record<BadgeVariant, string> = {
    default: 'bg-primary-tint text-primary border border-[#A7F3D0]',
    neutral: 'bg-[#F2F5F3] text-slate-text border border-outline-warm',
    success: 'bg-status-offered-bg text-status-offered border border-status-offered-border',
    warning: 'bg-status-review-bg text-status-review border border-status-review-border',
    info: 'bg-status-interview-bg text-status-interview border border-status-interview-border',
    danger: 'bg-status-rejected-bg text-status-rejected border border-status-rejected-border',
  };

  return (
    <span className={`${baseStyles} ${sizeStyles[size]} ${variantStyles[variant]} ${className}`}>
      {icon && <span className="inline-flex shrink-0">{icon}</span>}
      <span>{children}</span>
    </span>
  );
};
