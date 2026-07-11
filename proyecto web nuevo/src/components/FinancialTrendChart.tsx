import React, { useState } from 'react';
import type { TicketRecord, SportsTicketRecord } from '../types';

function getTrendChartData(tickets: TicketRecord[], sportsTickets: SportsTicketRecord[]): { label: string; dateStr: string; amount: number }[] {
  const data: { label: string; dateStr: string; amount: number }[] = [];
  const daysOfWeek = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
  
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dayLabel = daysOfWeek[d.getDay()];
    const dateStr = new Intl.DateTimeFormat('fr-CA', { timeZone: 'America/Santo_Domingo' }).format(d);
    
    const ticketSum = tickets
      .filter(t => t.status !== 'cancelled' && t.status !== 'voided' && t.drawDateKey === dateStr)
      .reduce((acc, t) => acc + t.total, 0);
      
    const sportsSum = sportsTickets
      .filter(t => t.status !== 'void' && (t.soldAt && t.soldAt.startsWith(dateStr)))
      .reduce((acc, t) => acc + t.stake, 0);
      
    data.push({
      label: dayLabel,
      dateStr,
      amount: ticketSum + sportsSum
    });
  }
  return data;
}

export const FinancialTrendChart: React.FC<{ tickets: TicketRecord[], sportsTickets: SportsTicketRecord[] }> = ({ tickets, sportsTickets }) => {
  const data = getTrendChartData(tickets, sportsTickets);
  const maxVal = Math.max(...data.map(d => d.amount), 100) * 1.15;
  
  const width = 500;
  const height = 140;
  const paddingX = 40;
  const paddingY = 20;
  
  const chartWidth = width - paddingX * 2;
  const chartHeight = height - paddingY * 2;
  
  const points = data.map((d, index) => {
    const x = paddingX + (index / (data.length - 1)) * chartWidth;
    const y = height - paddingY - (d.amount / maxVal) * chartHeight;
    return { x, y, amount: d.amount, label: d.label, dateStr: d.dateStr };
  });
  
  let pathD = '';
  let areaD = '';
  
  if (points.length > 0) {
    pathD = `M ${points[0].x} ${points[0].y}`;
    areaD = `M ${points[0].x} ${height - paddingY}`;
    
    points.forEach((p, index) => {
      if (index > 0) {
        pathD += ` L ${p.x} ${p.y}`;
      }
      areaD += ` L ${p.x} ${p.y}`;
    });
    
    areaD += ` L ${points[points.length - 1].x} ${height - paddingY} Z`;
  }
  
  const [hoveredPoint, setHoveredPoint] = useState<{ x: number; y: number; amount: number; label: string; dateStr: string } | null>(null);
  
  return (
    <div style={{ position: 'relative', width: '100%', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} style={{ overflow: 'visible' }}>
        <defs>
          <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity="0.4" />
            <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="lineGradient" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="hsl(var(--primary))" />
            <stop offset="50%" stopColor="hsl(var(--success))" />
            <stop offset="100%" stopColor="hsl(var(--primary))" />
          </linearGradient>
        </defs>
        
        {/* Grid lines */}
        {[0.25, 0.5, 0.75, 1].map((ratio, index) => {
          const y = height - paddingY - ratio * chartHeight;
          const val = Math.round(ratio * maxVal);
          return (
            <g key={index}>
              <line x1={paddingX} y1={y} x2={width - paddingX} y2={y} stroke="hsl(var(--border))" strokeDasharray="3,3" strokeOpacity="0.4" />
              <text x={paddingX - 8} y={y + 3} fill="hsl(var(--text-muted))" fontSize="8px" textAnchor="end">${val}</text>
            </g>
          );
        })}
        
        {areaD && <path className="chart-area-reveal" d={areaD} fill="url(#chartGradient)" />}
        {pathD && (
          <path 
            className="chart-line-reveal"
            d={pathD} 
            fill="none" 
            stroke="url(#lineGradient)" 
            strokeWidth="2.5" 
            strokeLinecap="round" 
            strokeLinejoin="round" 
          />
        )}
        
        {points.map((p, index) => (
          <g key={index}>
            {hoveredPoint?.label === p.label && (
              <line x1={p.x} y1={paddingY} x2={p.x} y2={height - paddingY} stroke="hsl(var(--primary))" strokeDasharray="2,2" strokeOpacity="0.4" />
            )}
            
            <circle 
              cx={p.x} 
              cy={p.y} 
              r={hoveredPoint?.label === p.label ? "5.5" : "3.5"} 
              fill="hsl(var(--surface))" 
              stroke="hsl(var(--primary))" 
              strokeWidth="2" 
              style={{ transition: 'r 0.1s ease', cursor: 'pointer' }}
              onMouseEnter={() => setHoveredPoint(p)}
              onMouseLeave={() => setHoveredPoint(null)}
            />
            
            <text x={p.x} y={height - 2} fill="hsl(var(--text-secondary))" fontSize="9px" textAnchor="middle" fontWeight="600">
              {p.label}
            </text>
          </g>
        ))}
      </svg>
      
      {hoveredPoint && (
        <div className="glass-panel-premium" style={{
          position: 'absolute',
          top: hoveredPoint.y - 45 > 0 ? hoveredPoint.y - 45 : 5,
          left: hoveredPoint.x - 50,
          width: '100px',
          padding: '4px 6px',
          textAlign: 'center',
          pointerEvents: 'none',
          zIndex: 10,
          boxShadow: 'var(--shadow-md)',
          fontSize: '0.8rem'
        }}>
          <span style={{ fontSize: '0.6rem', color: 'hsl(var(--text-secondary))', display: 'block' }}>{hoveredPoint.label} ({hoveredPoint.dateStr.substring(8, 10)})</span>
          <strong style={{ fontSize: '0.75rem', color: 'hsl(var(--text-primary))', display: 'block', marginTop: '1px' }}>
            ${hoveredPoint.amount.toLocaleString('en-US', { maximumFractionDigits: 0 })}
          </strong>
        </div>
      )}
    </div>
  );
};
