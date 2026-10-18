import React from 'react';

export interface SkeletonProps {
  variant?: 'text' | 'circular' | 'rectangular' | 'card';
  width?: string;
  height?: string;
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({
  variant = 'text',
  width,
  height,
  className = '',
}) => {
  const baseStyles = 'animate-pulse bg-[#E7E7E0]/70';

  if (variant === 'circular') {
    return (
      <div
        className={`${baseStyles} rounded-full shrink-0 ${className}`}
        style={{ width: width || '40px', height: height || '40px' }}
      />
    );
  }

  if (variant === 'card') {
    return (
      <div
        className={`bg-white rounded border border-outline-warm p-5 flex flex-col gap-4 ${className}`}
      >
        <div className="flex items-center gap-3">
          <div className={`${baseStyles} w-10 h-10 rounded`} />
          <div className="flex-1 flex flex-col gap-2">
            <div className={`${baseStyles} h-4 w-3/4 rounded`} />
            <div className={`${baseStyles} h-3 w-1/2 rounded`} />
          </div>
        </div>
        <div className={`${baseStyles} h-3 w-full rounded`} />
        <div className="flex gap-2">
          <div className={`${baseStyles} h-5 w-16 rounded`} />
          <div className={`${baseStyles} h-5 w-16 rounded`} />
        </div>
      </div>
    );
  }

  if (variant === 'rectangular') {
    return (
      <div
        className={`${baseStyles} rounded ${className}`}
        style={{ width: width || '100%', height: height || '120px' }}
      />
    );
  }

  return (
    <div
      className={`${baseStyles} rounded ${className}`}
      style={{ width: width || '100%', height: height || '16px' }}
    />
  );
};
