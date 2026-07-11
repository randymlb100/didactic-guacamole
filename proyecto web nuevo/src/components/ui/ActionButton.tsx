import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '../../utils/classNames';

type ActionButtonVariant = 'primary' | 'secondary' | 'danger' | 'success' | 'warning' | 'info' | 'finance' | 'ghost';

interface ActionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children?: ReactNode;
  icon?: ReactNode;
  variant?: ActionButtonVariant;
}

const variantClasses: Record<ActionButtonVariant, string> = {
  primary: 'btn-semantic btn-semantic--primary',
  secondary: 'btn-semantic btn-semantic--secondary',
  danger: 'btn-semantic btn-semantic--danger',
  success: 'btn-semantic btn-semantic--success',
  warning: 'btn-semantic btn-semantic--warning',
  info: 'btn-semantic btn-semantic--info',
  finance: 'btn-semantic btn-semantic--finance',
  ghost: 'btn-semantic btn-semantic--ghost',
};

export const ActionButton = ({ children, className, icon, variant = 'secondary', type = 'button', ...props }: ActionButtonProps) => {
  return (
    <button
      type={type}
      className={cn(
        'tap-active inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border px-4 py-2 text-sm font-bold transition-[box-shadow,border-color,background-color,color,filter] duration-150 disabled:cursor-not-allowed disabled:opacity-50',
        variantClasses[variant],
        className,
      )}
      {...props}
    >
      {icon}
      {children && <span>{children}</span>}
    </button>
  );
};
