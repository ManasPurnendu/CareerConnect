import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Input } from '../Input';

describe('Input component', () => {
  it('renders with label and connects htmlFor with input id', () => {
    render(<Input label="Email Address" id="email-field" />);
    const label = screen.getByText('Email Address');
    expect(label).toHaveAttribute('for', 'email-field');
    expect(screen.getByRole('textbox')).toHaveAttribute('id', 'email-field');
  });

  it('renders error message and marks aria-invalid', () => {
    render(<Input label="Password" error="Required field" />);
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Required field')).toBeInTheDocument();
  });

  it('handles typing events', () => {
    const handleChange = vi.fn();
    render(<Input placeholder="Search" onChange={handleChange} />);
    const input = screen.getByPlaceholderText('Search');
    fireEvent.change(input, { target: { value: 'React' } });
    expect(handleChange).toHaveBeenCalled();
  });
});
