import React from 'react';
import type { ApplicationStatus, JobStatus } from '../../types/api';

export interface StatusBadgeProps {
  status: ApplicationStatus | JobStatus | string;
  size?: 'sm' | 'md';
  className?: string;
}

interface StatusConfig {
  label: string;
  classes: string;
  icon: string;
}

const statusMap: Record<string, StatusConfig> = {
  // Application statuses
  APPLIED: {
    label: 'Applied',
    classes: 'bg-[#F0FDF4] text-[#166534] border-[#BBF7D0]',
    icon: 'send',
  },
  UNDER_REVIEW: {
    label: 'Under Review',
    classes: 'bg-status-review-bg text-status-review border-status-review-border',
    icon: 'visibility',
  },
  SHORTLISTED: {
    label: 'Shortlisted',
    classes: 'bg-primary-tint text-primary border-[#A7F3D0]',
    icon: 'star',
  },
  INTERVIEW: {
    label: 'Interview',
    classes: 'bg-status-interview-bg text-status-interview border-status-interview-border',
    icon: 'calendar_month',
  },
  INTERVIEW_SCHEDULED: {
    label: 'Interview',
    classes: 'bg-status-interview-bg text-status-interview border-status-interview-border',
    icon: 'calendar_month',
  },
  SELECTED: {
    label: 'Selected',
    classes: 'bg-[#ECFDF5] text-[#065F46] border-[#6EE7B7]',
    icon: 'check_circle',
  },
  OFFERED: {
    label: 'Offered',
    classes: 'bg-[#ECFDF5] text-[#065F46] border-[#6EE7B7]',
    icon: 'check_circle',
  },
  REJECTED: {
    label: 'Rejected',
    classes: 'bg-status-rejected-bg text-status-rejected border-status-rejected-border',
    icon: 'cancel',
  },
  WITHDRAWN: {
    label: 'Withdrawn',
    classes: 'bg-status-withdrawn-bg text-status-withdrawn border-status-withdrawn-border',
    icon: 'undo',
  },
  // Job statuses
  ACTIVE: {
    label: 'Active',
    classes: 'bg-[#F0FDF4] text-[#166534] border-[#BBF7D0]',
    icon: 'check_circle',
  },
  PUBLISHED: {
    label: 'Active',
    classes: 'bg-[#F0FDF4] text-[#166534] border-[#BBF7D0]',
    icon: 'check_circle',
  },
  DRAFT: {
    label: 'Draft',
    classes: 'bg-[#F5F5F4] text-[#57534E] border-[#D6D3D1]',
    icon: 'edit_note',
  },
  CLOSED: {
    label: 'Closed',
    classes: 'bg-status-withdrawn-bg text-status-withdrawn border-status-withdrawn-border',
    icon: 'lock',
  },
  ARCHIVED: {
    label: 'Archived',
    classes: 'bg-[#F5F5F4] text-[#57534E] border-[#D6D3D1]',
    icon: 'archive',
  },
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  size = 'sm',
  className = '',
}) => {
  const normalizedKey = status.toUpperCase();
  const config = statusMap[normalizedKey] || {
    label: status.replace(/_/g, ' '),
    classes: 'bg-[#F2F5F3] text-slate-text border-outline-warm',
    icon: 'info',
  };

  const sizeStyles =
    size === 'sm'
      ? 'text-[11px] leading-3.5 px-2 py-0.5 gap-1'
      : 'text-xs leading-4 px-2.5 py-1 gap-1.5';

  return (
    <span
      className={`inline-flex items-center font-medium rounded border font-sans select-none ${sizeStyles} ${config.classes} ${className}`}
    >
      <span className="material-symbols-outlined !text-[13px]">{config.icon}</span>
      <span>{config.label}</span>
    </span>
  );
};
