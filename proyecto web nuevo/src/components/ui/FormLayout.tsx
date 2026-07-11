import type { HTMLAttributes, LabelHTMLAttributes, ReactNode } from 'react';
import { cn } from '../../utils/classNames';

interface FormGridProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  columns?: 1 | 2 | 3;
}

export const FormGrid = ({ children, className, columns = 2, ...props }: FormGridProps) => (
  <div
    className={cn(
      'grid grid-cols-1 gap-4',
      columns === 2 && 'sm:grid-cols-2',
      columns === 3 && 'sm:grid-cols-2 lg:grid-cols-3',
      className,
    )}
    {...props}
  >
    {children}
  </div>
);

interface FieldGroupProps extends LabelHTMLAttributes<HTMLLabelElement> {
  children: ReactNode;
  label: string;
}

export const FieldGroup = ({ children, className, label, ...props }: FieldGroupProps) => (
  <label className={cn('form-group', className)} {...props}>
    <span className="form-label">{label}</span>
    {children}
  </label>
);
