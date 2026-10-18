import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Button } from '../Button';

describe('Button component', () => {
  it('renders children correctly', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button', { name: /click me/i })).toBeInTheDocument();
  });

  it('handles click events', () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>Action</Button>);
    fireEvent.click(screen.getByRole('button', { name: /action/i }));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('renders primary variant by default with min 44px touch target', () => {
    render(<Button>Primary</Button>);
    const btn = screen.getByRole('button', { name: /primary/i });
    expect(btn.className).toContain('bg-primary');
    expect(btn.className).toContain('min-h-[44px]');
  });

  it('renders outline and danger variants', () => {
    const { rerender } = render(<Button variant="outline">Outline</Button>);
    expect(screen.getByRole('button').className).toContain('border');

    rerender(<Button variant="danger">Danger</Button>);
    expect(screen.getByRole('button').className).toContain('bg-status-rejected');
  });

  it('shows loading spinner and disables button when isLoading is true', () => {
    render(<Button isLoading>Submit</Button>);
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('disables button when disabled prop is provided', () => {
    const handleClick = vi.fn();
    render(<Button disabled onClick={handleClick}>Disabled</Button>);
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
    fireEvent.click(btn);
    expect(handleClick).not.toHaveBeenCalled();
  });
});
