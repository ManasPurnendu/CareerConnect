import React from 'react';
import { Button } from './Button';

export interface EmptyStateProps {
  icon?: string;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon = 'inbox',
  title,
  description,
  actionLabel,
  onAction,
  className = '',
}) => {
  return (
    <div
      className={`rounded border border-dashed border-outline-warm bg-white p-8 sm:p-12 text-center flex flex-col items-center justify-center max-w-xl mx-auto my-6 ${className}`}
    >
      <div className="w-14 h-14 rounded-full bg-primary-tint border border-[#A7F3D0] flex items-center justify-center text-primary mb-4">
        <span className="material-symbols-outlined !text-3xl">{icon}</span>
      </div>
      <h3 className="font-headline font-semibold text-lg text-slate-text mb-1.5">{title}</h3>
      <p className="text-sm text-slate-muted max-w-md mb-6 leading-relaxed">{description}</p>
      {actionLabel && onAction && (
        <Button variant="primary" size="md" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
};
