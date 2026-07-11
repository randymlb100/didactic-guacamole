import type { ReactNode } from 'react';
import { cn } from '../../utils/classNames';

interface MetricCardProps {
  label: string;
  value: ReactNode;
  meta?: ReactNode;
  icon?: ReactNode;
  accent?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'sales' | 'balance' | 'recharge' | 'risk' | 'system' | 'sports';
  className?: string;
}

const accentClasses = {
  primary: 'metric-card-pro--primary',
  success: 'metric-card-pro--success',
  warning: 'metric-card-pro--warning',
  danger: 'metric-card-pro--danger',
  info: 'metric-card-pro--info',
  sales: 'metric-card-pro--sales',
  balance: 'metric-card-pro--balance',
  recharge: 'metric-card-pro--recharge',
  risk: 'metric-card-pro--risk',
  system: 'metric-card-pro--system',
  sports: 'metric-card-pro--sports',
};

export const MetricCard = ({ accent = 'primary', className, icon, label, meta, value }: MetricCardProps) => {
  return (
    <div className={cn('metric-card-pro', accentClasses[accent], className)}>
      <div className="metric-card-pro__content">
        <div className="metric-card-pro__top">
          <span className="metric-card-pro__label">{label}</span>
          {icon && <span className="metric-card-pro__icon">{icon}</span>}
        </div>
        <strong className="metric-card-pro__value">{value}</strong>
        <div className="metric-card-pro__meta">
          <span>{meta || 'Estado operativo'}</span>
          <span className="metric-card-pro__rail" />
        </div>
      </div>
    </div>
  );
};
