import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../utils/classNames';

interface ModalShellProps {
  children: ReactNode;
  align?: 'center' | 'bottom';
  className?: string;
}

export const ModalShell = ({ align = 'center', children, className }: ModalShellProps) => (
  <div
    className={cn(
      'fixed inset-0 z-[110] flex bg-black/55 p-4 backdrop-blur-sm',
      align === 'center' ? 'items-center justify-center' : 'items-end justify-center p-0 sm:p-4',
      className,
    )}
  >
    {children}
  </div>
);

interface ModalCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  size?: 'sm' | 'md';
  sheet?: boolean;
}

export const ModalCard = ({ children, className, sheet = false, size = 'sm', ...props }: ModalCardProps) => {
  return (
    <div
      className={cn(
        'relative w-full border border-ln-border bg-ln-surface p-6 shadow-ln-lg backdrop-blur-md',
        size === 'sm' ? 'max-w-[430px]' : 'max-w-[560px]',
        sheet ? 'max-h-[85vh] overflow-y-auto rounded-t-ln-lg sm:rounded-ln-lg' : 'rounded-ln-lg',
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
};
