import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from '../StatusBadge';

describe('StatusBadge component', () => {
  it('renders application statuses correctly with accessible text and icons', () => {
    const { rerender } = render(<StatusBadge status="APPLIED" />);
    expect(screen.getByText('Applied')).toBeInTheDocument();

    rerender(<StatusBadge status="UNDER_REVIEW" />);
    expect(screen.getByText('Under Review')).toBeInTheDocument();

    rerender(<StatusBadge status="SHORTLISTED" />);
    expect(screen.getByText('Shortlisted')).toBeInTheDocument();

    rerender(<StatusBadge status="INTERVIEW_SCHEDULED" />);
    expect(screen.getByText('Interview')).toBeInTheDocument();

    rerender(<StatusBadge status="OFFERED" />);
    expect(screen.getByText('Offered')).toBeInTheDocument();

    rerender(<StatusBadge status="REJECTED" />);
    expect(screen.getByText('Rejected')).toBeInTheDocument();

    rerender(<StatusBadge status="WITHDRAWN" />);
    expect(screen.getByText('Withdrawn')).toBeInTheDocument();
  });

  it('renders job statuses correctly', () => {
    const { rerender } = render(<StatusBadge status="PUBLISHED" />);
    expect(screen.getByText('Active')).toBeInTheDocument();

    rerender(<StatusBadge status="DRAFT" />);
    expect(screen.getByText('Draft')).toBeInTheDocument();

    rerender(<StatusBadge status="CLOSED" />);
    expect(screen.getByText('Closed')).toBeInTheDocument();
  });
});
