import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '../../utils/classNames';

type TableActionTone = 'primary' | 'danger' | 'neutral' | 'success' | 'warning' | 'info';

interface TableActionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  tone?: TableActionTone;
}

const toneClasses: Record<TableActionTone, string> = {
  primary: 'table-action-semantic table-action-semantic--primary',
  danger: 'table-action-semantic table-action-semantic--danger',
  neutral: 'table-action-semantic table-action-semantic--neutral',
  success: 'table-action-semantic table-action-semantic--success',
  warning: 'table-action-semantic table-action-semantic--warning',
  info: 'table-action-semantic table-action-semantic--info',
};

export const TableActionButton = ({ children, className, tone = 'neutral', type = 'button', ...props }: TableActionButtonProps) => (
  <button
    type={type}
    className={cn(
      'inline-flex min-h-8 items-center justify-center rounded-md border px-2.5 py-1 text-xs font-bold transition-[background-color,border-color,color,filter] duration-150 disabled:cursor-not-allowed disabled:opacity-50',
      toneClasses[tone],
      className,
    )}
    {...props}
  >
    {children}
  </button>
);
