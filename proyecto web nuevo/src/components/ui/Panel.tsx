import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../utils/classNames';

interface PanelProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  tone?: 'default' | 'primary' | 'success' | 'warning' | 'danger';
}

const toneClasses = {
  default: '',
  primary: 'border-ln-primary/30 shadow-ln-card-hover before:bg-ln-primary',
  success: 'border-ln-success/30 before:bg-ln-success',
  warning: 'border-ln-warning/35 before:bg-ln-warning',
  danger: 'border-ln-danger/35 before:bg-ln-danger',
};

export const Panel = ({ children, className, tone = 'default', ...props }: PanelProps) => {
  return (
    <div
      className={cn(
        'glass-panel-premium fintech-section relative overflow-hidden rounded-lg border border-ln-border bg-ln-surface p-5 shadow-ln-card transition-[box-shadow,border-color] duration-200 before:absolute before:inset-x-0 before:top-0 before:h-0.5 before:bg-ln-border',
        toneClasses[tone],
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
};

interface PanelHeaderProps {
  eyebrow?: string;
  title: string;
  subtitle?: string;
  action?: ReactNode;
  className?: string;
}

export const PanelHeader = ({ action, className, eyebrow, subtitle, title }: PanelHeaderProps) => {
  return (
    <div className={cn('flex flex-wrap items-start justify-between gap-3', className)}>
      <div className="min-w-0">
        {eyebrow && <span className="text-xs font-semibold uppercase tracking-wide text-ln-primary">{eyebrow}</span>}
        <h3 className="text-lg font-bold text-ln-text-primary">{title}</h3>
        {subtitle && <p className="mt-1 text-sm text-ln-text-secondary">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
};
