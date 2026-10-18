import React from 'react';
import { Link } from 'react-router-dom';

export interface BreadcrumbItem {
  label: string;
  path?: string;
}

export interface BreadcrumbProps {
  items: BreadcrumbItem[];
  className?: string;
}

export const Breadcrumb: React.FC<BreadcrumbProps> = ({ items, className = '' }) => {
  return (
    <nav aria-label="Breadcrumb" className={`text-xs text-slate-muted ${className}`}>
      <ol className="flex items-center flex-wrap gap-1.5">
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={index} className="flex items-center gap-1.5">
              {item.path && !isLast ? (
                <Link
                  to={item.path}
                  className="hover:text-primary transition-colors underline-offset-2 hover:underline"
                >
                  {item.label}
                </Link>
              ) : (
                <span className={isLast ? 'text-slate-text font-medium' : ''} aria-current={isLast ? 'page' : undefined}>
                  {item.label}
                </span>
              )}
              {!isLast && (
                <span className="material-symbols-outlined !text-[13px] text-slate-muted/60 select-none">
                  chevron_right
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
};
