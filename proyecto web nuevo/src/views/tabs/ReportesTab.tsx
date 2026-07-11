import React from 'react';
import { FileSpreadsheet } from 'lucide-react';
import type { TicketRecord, SportsTicketRecord } from '../../types';
import { ActionButton, MetricCard, Panel, PanelHeader } from '../../components/ui';

interface ReportesTabProps {
  tickets: TicketRecord[];
  sportsTickets: SportsTicketRecord[];
}

export const ReportesTab: React.FC<ReportesTabProps> = ({
  tickets,
  sportsTickets
}) => {
  const handleExportToExcel = () => {
    const headers = ["Tipo", "ID/Codigo", "Cajero", "Fecha", "Monto Jugado", "Premios", "Estado"];
    const rows: any[][] = [];

    const activeLotteryTickets = tickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided');
    const activeSportsTickets = sportsTickets.filter(st => st.status !== 'void');

    for (const t of activeLotteryTickets) {
      const dateStr = new Date(t.createdAtEpochMs).toLocaleDateString();
      rows.push([
        "Loteria",
        t.serial || t.id,
        t.sellerUser || "",
        dateStr,
        t.total.toFixed(2),
        t.totalPrize.toFixed(2),
        t.status
      ]);
    }

    for (const st of activeSportsTickets) {
      const dateStr = new Date(st.soldAt).toLocaleDateString();
      rows.push([
        "Deportiva",
        st.ticketCode || st.id,
        st.sellerUsername || "",
        dateStr,
        st.stake.toFixed(2),
        st.potentialPayout.toFixed(2),
        st.status
      ]);
    }

    const csvContent = "\uFEFF" + [headers.join(","), ...rows.map(r => r.map(val => `"${String(val).replace(/"/g, '""')}"`).join(","))].join("\n");
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `reporte_ventas_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const grossSales = tickets.filter(t => t.status !== 'cancelled' && t.status !== 'voided').reduce((acc, t) => acc + t.total, 0);
  const totalPrizes = tickets.filter(t => t.status === 'paid' || t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
  const netEarnings = grossSales - totalPrizes;

  return (
    <Panel className="fade-in flex flex-col gap-5">
      <PanelHeader
        title="Análisis de Ventas vs Premios"
        action={
          <ActionButton icon={<FileSpreadsheet size={16} />} onClick={handleExportToExcel} variant="success">
            Exportar a Excel
          </ActionButton>
        }
      />

      <div className="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(220px,1fr))]">
        <MetricCard label="Ventas Brutas Totales" value={`$${grossSales.toFixed(2)}`} accent="primary" />
        <MetricCard label="Premios Aprobados" value={`$${totalPrizes.toFixed(2)}`} accent="danger" />
        <MetricCard label="Ingreso Neto (Ganancia)" value={`$${netEarnings.toFixed(2)}`} accent="success" />
      </div>
    </Panel>
  );
};
