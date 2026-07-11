import type { ReactNode } from 'react';
import { cn } from '../../utils/classNames';

interface DataToolbarProps {
  children?: ReactNode;
  className?: string;
  title?: string;
  subtitle?: string;
}

export const DataToolbar = ({ children, className, subtitle, title }: DataToolbarProps) => {
  return (
    <div className={cn('fintech-toolbar fintech-section flex flex-wrap items-center justify-between gap-3 rounded-lg border border-ln-border bg-ln-surface px-4 py-3 shadow-ln-card', className)}>
      {(title || subtitle) && (
        <div className="min-w-0">
          {title && <p className="text-sm font-bold text-ln-text-primary">{title}</p>}
          {subtitle && <p className="text-xs text-ln-text-secondary">{subtitle}</p>}
        </div>
      )}
      {children && (
        <div
          className={cn(
            'flex min-w-0 flex-1 flex-wrap items-center gap-2',
            title || subtitle ? 'justify-start sm:min-w-[260px] sm:justify-end' : 'w-full justify-start',
          )}
        >
          {children}
        </div>
      )}
    </div>
  );
};
