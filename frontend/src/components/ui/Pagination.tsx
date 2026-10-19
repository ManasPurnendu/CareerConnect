import React from 'react';

export interface PaginationProps {
  currentPage: number; // 0-indexed per Spring Data Page
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  onPageChange,
  className = '',
}) => {
  if (totalPages <= 1) return null;

  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const maxVisible = 5;

    if (totalPages <= maxVisible) {
      for (let i = 0; i < totalPages; i++) {
        pages.push(i);
      }
    } else {
      pages.push(0);
      if (currentPage > 2) {
        pages.push('...');
      }

      const start = Math.max(1, currentPage - 1);
      const end = Math.min(totalPages - 2, currentPage + 1);

      for (let i = start; i <= end; i++) {
        pages.push(i);
      }

      if (currentPage < totalPages - 3) {
        pages.push('...');
      }
      pages.push(totalPages - 1);
    }

    return pages;
  };

  return (
    <nav
      aria-label="Pagination Navigation"
      className={`flex items-center justify-between sm:justify-end gap-2 text-xs sm:text-sm ${className}`}
    >
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 0}
        aria-label="Previous page"
        className="min-h-[44px] px-3.5 py-2 rounded border border-outline-warm bg-white text-slate-text hover:bg-[#F2F5F3] disabled:opacity-40 disabled:cursor-not-allowed inline-flex items-center gap-1 transition-colors"
      >
        <span className="material-symbols-outlined !text-base">chevron_left</span>
        <span className="hidden sm:inline">Previous</span>
      </button>

      <div className="flex items-center gap-1">
        {getPageNumbers().map((page, idx) => {
          if (page === '...') {
            return (
              <span key={`ellipsis-${idx}`} className="px-2 text-slate-muted">
                ...
              </span>
            );
          }

          const pageNum = page as number;
          const isActive = pageNum === currentPage;

          return (
            <button
              key={pageNum}
              onClick={() => onPageChange(pageNum)}
              aria-label={`Page ${pageNum + 1}`}
              aria-current={isActive ? 'page' : undefined}
              className={`w-10 h-10 min-w-[40px] min-h-[40px] rounded flex items-center justify-center font-medium font-mono text-xs transition-colors ${
                isActive
                  ? 'bg-primary text-white font-semibold'
                  : 'bg-white border border-outline-warm text-slate-text hover:bg-[#F2F5F3]'
              }`}
            >
              {pageNum + 1}
            </button>
          );
        })}
      </div>

      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage >= totalPages - 1}
        aria-label="Next page"
        className="min-h-[44px] px-3.5 py-2 rounded border border-outline-warm bg-white text-slate-text hover:bg-[#F2F5F3] disabled:opacity-40 disabled:cursor-not-allowed inline-flex items-center gap-1 transition-colors"
      >
        <span className="hidden sm:inline">Next</span>
        <span className="material-symbols-outlined !text-base">chevron_right</span>
      </button>
    </nav>
  );
};
