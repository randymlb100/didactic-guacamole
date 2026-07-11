import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../utils/classNames';

type StatusTone = 'primary' | 'success' | 'warning' | 'danger' | 'neutral';

interface StatusBadgeProps extends HTMLAttributes<HTMLSpanElement> {
  children: ReactNode;
  tone?: StatusTone;
}

const toneClasses: Record<StatusTone, string> = {
  primary: 'border-ln-primary/25 bg-ln-primary/10 text-ln-primary before:bg-ln-primary',
  success: 'border-ln-success/25 bg-ln-success/10 text-ln-success before:bg-ln-success',
  warning: 'border-ln-warning/25 bg-ln-warning/10 text-ln-warning before:bg-ln-warning',
  danger: 'border-ln-danger/25 bg-ln-danger/10 text-ln-danger before:bg-ln-danger',
  neutral: 'border-ln-border bg-ln-surface-hover text-ln-text-secondary before:bg-ln-text-muted',
};

export const StatusBadge = ({ children, className, tone = 'neutral', ...props }: StatusBadgeProps) => {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-bold uppercase tracking-[0.035em] before:size-1.5 before:rounded-full', toneClasses[tone], className)} {...props}>
      {children}
    </span>
  );
};
