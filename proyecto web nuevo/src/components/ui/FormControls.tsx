import type { InputHTMLAttributes, LabelHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react';
import { Search } from 'lucide-react';
import { cn } from '../../utils/classNames';

interface CompactFieldProps extends LabelHTMLAttributes<HTMLLabelElement> {
  children: ReactNode;
  helper?: ReactNode;
  label: ReactNode;
}

export const CompactField = ({ children, className, helper, label, ...props }: CompactFieldProps) => (
  <label className={cn('compact-field', className)} {...props}>
    <span className="compact-field__label">{label}</span>
    {children}
    {helper && <span className="compact-field__helper">{helper}</span>}
  </label>
);

interface SearchInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  wrapperClassName?: string;
}

export const SearchInput = ({ className, wrapperClassName, ...props }: SearchInputProps) => (
  <div className={cn('search-input-shell', wrapperClassName)}>
    <Search className="search-input-shell__icon" size={16} />
    <input type="search" className={cn('form-input search-input', className)} {...props} />
  </div>
);

interface MoneyInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  symbol?: string;
  wrapperClassName?: string;
}

export const MoneyInput = ({ className, symbol = '$', wrapperClassName, ...props }: MoneyInputProps) => (
  <div className={cn('money-input-shell', wrapperClassName)}>
    <span className="money-input-shell__symbol">{symbol}</span>
    <input type="number" className={cn('form-input money-input', className)} {...props} />
  </div>
);

export const CompactSelect = ({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) => (
  <select className={cn('form-input compact-select', className)} {...props} />
);
