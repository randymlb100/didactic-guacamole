export const drawTicketToCanvas = (ticket: any, isSports: boolean): HTMLCanvasElement => {
  const canvas = document.createElement('canvas');
  const width = 380;
  
  let height = 400;
  if (isSports) {
    height = 360 + (ticket.legs || []).length * 62;
  } else {
    height = 360 + (ticket.plays || []).length * 24;
  }
  
  canvas.width = width;
  canvas.height = height;
  
  const ctx = canvas.getContext('2d')!;
  
  // Fill background
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, width, height);
  
  // Draw diagonal status watermark in background
  ctx.save();
  ctx.translate(width / 2, height / 2);
  ctx.rotate(-30 * Math.PI / 180);
  ctx.font = '900 42px Courier New, monospace';
  
  let statusText = 'ACTIVO';
  let statusColor = 'rgba(59, 130, 246, 0.12)'; // Blue
  
  const status = String(ticket.status).toLowerCase();
  if (status === 'paid') {
    statusText = 'COBRADO';
    statusColor = 'rgba(16, 185, 129, 0.12)'; // Green
  } else if (status === 'cancelled' || status === 'voided' || status === 'void') {
    statusText = 'ANULADO';
    statusColor = 'rgba(239, 68, 68, 0.12)'; // Red
  } else if (status === 'lost') {
    statusText = 'PERDIDO';
    statusColor = 'rgba(107, 114, 128, 0.12)'; // Gray
  } else if (status === 'winner' || status === 'won') {
    statusText = 'PREMIO PENDIENTE';
    statusColor = 'rgba(245, 158, 11, 0.12)'; // Orange
  }
  
  ctx.fillStyle = statusColor;
  ctx.textAlign = 'center';
  ctx.fillText(statusText, 0, 0);
  
  // Draw border around the watermark text
  ctx.strokeStyle = statusColor;
  ctx.lineWidth = 4;
  ctx.setLineDash([8, 4]);
  const textWidth = ctx.measureText(statusText).width;
  ctx.strokeRect(-textWidth / 2 - 15, -35, textWidth + 30, 50);
  ctx.restore();
  
  // Start drawing content
  ctx.fillStyle = '#111111';
  ctx.textAlign = 'center';
  
  let y = 30;
  
  if (!isSports) {
    // Lottery Ticket
    ctx.font = 'bold 18px sans-serif';
    ctx.fillText('BANCA EL FUERTE', width / 2, y);
    y += 20;
    
    ctx.font = '11px Courier New, monospace';
    ctx.fillText('Consorcio de Loterías RD', width / 2, y);
    y += 18;
    
    ctx.fillText(`Cajero: @${ticket.sellerUser || 'cajero'}`, width / 2, y);
    y += 22;
    
    drawDashedLine(ctx, 15, y, width - 15, y);
    y += 18;
    
    // Meta info
    ctx.textAlign = 'left';
    ctx.font = '11px Courier New, monospace';
    const dateStr = new Date(ticket.createdAtEpochMs).toLocaleString('es-DO', { timeZone: 'America/Santo_Domingo' });
    ctx.fillText(`FECHA: ${dateStr}`, 15, y);
    y += 16;
    
    ctx.fillText(`SERIAL: ${ticket.serial || ticket.id}`, 15, y);
    y += 16;
    
    ctx.fillText(`TICKET ID: ${ticket.id}`, 15, y);
    y += 18;
    
    // Lotteries list
    const uniqueLots = new Set<string>();
    (ticket.plays || []).forEach((p: any) => {
      if (p.lotteryName) {
        p.lotteryName.split(/[\/,]+/).forEach((part: string) => {
          const trimmed = part.trim();
          if (trimmed) uniqueLots.add(trimmed);
        });
      }
    });
    const lotteriesStr = Array.from(uniqueLots).join(' / ');
    
    ctx.font = 'bold 11px Courier New, monospace';
    ctx.fillText('LOTERÍAS:', 15, y);
    ctx.font = '11px Courier New, monospace';
    wrapText(ctx, lotteriesStr, 85, y, width - 100, 14);
    
    const metrics = ctx.measureText(lotteriesStr);
    const lineCount = Math.ceil(metrics.width / (width - 100)) || 1;
    y += lineCount * 14 + 10;
    
    drawDashedLine(ctx, 15, y, width - 15, y);
    y += 18;
    
    // Plays Table Header
    ctx.font = 'bold 11px Courier New, monospace';
    ctx.fillText('JUGADA', 15, y);
    ctx.textAlign = 'center';
    ctx.fillText('TIPO', width / 2 + 10, y);
    ctx.textAlign = 'right';
    ctx.fillText('MONTO', width - 15, y);
    y += 16;
    
    ctx.lineWidth = 1;
    ctx.strokeStyle = '#111111';
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.moveTo(15, y - 4);
    ctx.lineTo(width - 15, y - 4);
    ctx.stroke();
    y += 6;
    
    // Plays Table Body
    ctx.font = '11px Courier New, monospace';
    (ticket.plays || []).forEach((p: any) => {
      ctx.textAlign = 'left';
      ctx.fillText(p.number, 15, y);
      ctx.textAlign = 'center';
      ctx.fillText(p.playType.toUpperCase(), width / 2 + 10, y);
      ctx.textAlign = 'right';
      ctx.fillText(`$${Number(p.amount).toFixed(2)}`, width - 15, y);
      y += 18;
    });
    
    y += 4;
    drawDashedLine(ctx, 15, y, width - 15, y);
    y += 16;
    
    // Totals
    ctx.textAlign = 'left';
    ctx.fillText('SUBTOTAL:', 15, y);
    ctx.textAlign = 'right';
    ctx.fillText(`$${Number(ticket.total).toFixed(2)}`, width - 15, y);
    y += 16;
    
    ctx.textAlign = 'left';
    ctx.fillText('DESCUENTO:', 15, y);
    ctx.textAlign = 'right';
    ctx.fillText('$0.00', width - 15, y);
    y += 18;
    
    ctx.beginPath();
    ctx.moveTo(15, y - 6);
    ctx.lineTo(width - 15, y - 6);
    ctx.stroke();
    
    ctx.font = 'bold 12px Courier New, monospace';
    ctx.textAlign = 'left';
    ctx.fillText('TOTAL APOSTADO:', 15, y);
    ctx.textAlign = 'right';
    ctx.fillText(`$${Number(ticket.total).toFixed(2)}`, width - 15, y);
    y += 18;
    
    if (ticket.totalPrize > 0) {
      ctx.font = 'bold 12px Courier New, monospace';
      ctx.fillStyle = '#dc2626';
      ctx.textAlign = 'left';
      ctx.fillText('PREMIO ACUMULADO:', 15, y);
      ctx.textAlign = 'right';
      ctx.fillText(`$${Number(ticket.totalPrize).toFixed(2)}`, width - 15, y);
      y += 18;
      ctx.fillStyle = '#111111'; // Reset
    }
  } else {
    // Sports Ticket
    ctx.font = 'bold 18px sans-serif';
    ctx.fillText('SPORTS BOOK', width / 2, y);
    y += 20;
    
    ctx.font = '11px Courier New, monospace';
    ctx.fillText(ticket.bancaName || 'BANCA DEPORTIVA', width / 2, y);
    y += 18;
    
    ctx.fillText(`Cajero: @${ticket.sellerUsername || 'cajero'}`, width / 2, y);
    y += 22;
    
    drawDashedLine(ctx, 15, y, width - 15, y);
    y += 18;
    
    // Meta info
    ctx.textAlign = 'left';
    ctx.font = '11px Courier New, monospace';
    const dateStr = new Date(ticket.soldAt).toLocaleString('es-DO', { timeZone: 'America/Santo_Domingo' });
    ctx.fillText(`FECHA: ${dateStr}`, 15, y);
    y += 16;
    
    ctx.fillText(`TICKET ID: ${ticket.ticketCode}`, 15, y);
    y += 16;
    
    ctx.fillText(`TIPO: ${String(ticket.ticketType).toUpperCase()}`, 15, y);
    y += 18;
    
    drawDashedLine(ctx, 15, y, width - 15, y);
    y += 18;
    
    // Legs
    (ticket.legs || []).forEach((leg: any) => {
      ctx.font = 'bold 11px Courier New, monospace';
      ctx.textAlign = 'left';
      
      wrapText(ctx, leg.eventLabel, 15, y, width - 30, 14);
      const metrics = ctx.measureText(leg.eventLabel);
      const lineCount = Math.ceil(metrics.width / (width - 30)) || 1;
      y += lineCount * 14;
      
      ctx.font = '11px Courier New, monospace';
      ctx.fillStyle = '#555555';
      ctx.fillText(`${leg.marketTitle} (${leg.selectionLabel})`, 15, y);
      ctx.textAlign = 'right';
      ctx.fillText(`x${Number(leg.decimalOdds).toFixed(2)}`, width - 15, y);
      y += 14;
      
      ctx.textAlign = 'left';
      ctx.fillStyle = '#888888';
      ctx.fillText('Estado:', 15, y);
      ctx.textAlign = 'right';
      ctx.font = 'bold 11px Courier New, monospace';
      ctx.fillStyle = leg.status === 'won' ? '#10b981' : leg.status === 'lost' ? '#ef4444' : '#e59b0b';
      ctx.fillText(String(leg.status).toUpperCase(), width - 15, y);
      
      ctx.fillStyle = '#111111';
      y += 20;
    });
    
    drawDashedLine(ctx, 15, y, width - 15, y);
    y += 18;
    
    ctx.font = '11px Courier New, monospace';
    ctx.textAlign = 'left';
    ctx.fillText('MONTO APOSTADO:', 15, y);
    ctx.textAlign = 'right';
    ctx.fillText(`$${Number(ticket.stake).toFixed(2)}`, width - 15, y);
    y += 16;
    
    ctx.textAlign = 'left';
    ctx.fillText('CUOTA TOTAL:', 15, y);
    ctx.textAlign = 'right';
    ctx.fillText(`x${Number(ticket.decimalOdds).toFixed(2)}`, width - 15, y);
    y += 18;
    
    ctx.beginPath();
    ctx.moveTo(15, y - 6);
    ctx.lineTo(width - 15, y - 6);
    ctx.stroke();
    
    ctx.font = 'bold 12px Courier New, monospace';
    ctx.textAlign = 'left';
    ctx.fillText('POTENCIAL PREMIO:', 15, y);
    ctx.textAlign = 'right';
    ctx.fillStyle = '#10b981';
    ctx.fillText(`$${Number(ticket.potentialPayout).toFixed(2)}`, width - 15, y);
    ctx.fillStyle = '#111111';
    y += 18;
  }
  
  // Barcode
  y += 10;
  ctx.fillStyle = '#111111';
  drawBarcodeLines(ctx, 15, y, width - 30, 32);
  y += 40;
  
  ctx.font = '9px Courier New, monospace';
  ctx.textAlign = 'center';
  const barcodeText = `*${String(ticket.serial || ticket.ticketCode || ticket.id).substring(0, 18).toUpperCase()}*`;
  ctx.fillText(barcodeText, width / 2, y);
  
  return canvas;
};

const drawDashedLine = (ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number) => {
  ctx.save();
  ctx.strokeStyle = '#111111';
  ctx.lineWidth = 1;
  ctx.setLineDash([4, 4]);
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
  ctx.restore();
};

const drawBarcodeLines = (ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number) => {
  ctx.save();
  ctx.fillStyle = '#111111';
  
  let currentX = x;
  const endX = x + w;
  const linePatterns = [2, 1, 3, 1, 1, 2, 4, 1, 2, 2, 1, 3, 2, 1, 1, 4, 2];
  let patternIdx = 0;
  
  while (currentX < endX) {
    const lineW = linePatterns[patternIdx % linePatterns.length];
    const spaceW = linePatterns[(patternIdx + 1) % linePatterns.length];
    
    ctx.fillRect(currentX, y, lineW, h);
    currentX += lineW + spaceW;
    patternIdx += 2;
  }
  ctx.restore();
};

const wrapText = (ctx: CanvasRenderingContext2D, text: string, x: number, y: number, maxWidth: number, lineHeight: number) => {
  const words = text.split(' ');
  let line = '';
  let currentY = y;
  
  for (let n = 0; n < words.length; n++) {
    const testLine = line + words[n] + ' ';
    const metrics = ctx.measureText(testLine);
    const testWidth = metrics.width;
    
    if (testWidth > maxWidth && n > 0) {
      ctx.fillText(line, x, currentY);
      line = words[n] + ' ';
      currentY += lineHeight;
    } else {
      line = testLine;
    }
  }
  ctx.fillText(line, x, currentY);
};

export const shareTicket = async (ticket: any, isSports: boolean) => {
  try {
    const canvas = drawTicketToCanvas(ticket, isSports);
    
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob((b) => resolve(b), 'image/jpeg', 0.95);
    });
    
    if (!blob) {
      throw new Error('No se pudo generar la imagen del ticket.');
    }
    
    const serialName = ticket.serial || ticket.ticketCode || ticket.id;
    const cleanSerial = String(serialName).substring(0, 12);
    const file = new File([blob], `ticket-${cleanSerial}.jpg`, { type: 'image/jpeg' });
    
    // Web Share API
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      await navigator.share({
        files: [file],
        title: 'Ticket Lotterynet',
        text: `Aquí tienes tu ticket: ${serialName}`
      });
      return { success: true, method: 'share' };
    }
    
    // Clipboard API (PNG fallback)
    try {
      const pngBlob = await new Promise<Blob | null>((resolve) => {
        canvas.toBlob((b) => resolve(b), 'image/png');
      });
      
      if (pngBlob && navigator.clipboard && navigator.clipboard.write) {
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': pngBlob })
        ]);
        alert('¡Imagen del ticket copiada al portapapeles! Puedes pegarla directamente (Ctrl+V) en WhatsApp.');
        return { success: true, method: 'clipboard' };
      }
    } catch (clipErr) {
      console.warn('Clipboard write failed, downloading instead:', clipErr);
    }
    
    // Download fallback
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `ticket-${cleanSerial}.jpg`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    
    return { success: true, method: 'download' };
  } catch (err: any) {
    console.error('Error sharing ticket:', err);
    alert(`Error al compartir el ticket: ${err.message || err}`);
    return { success: false, error: err };
  }
};

export const shareTicketTextWhatsApp = (ticket: any, isSports: boolean) => {
  let text = '';
  if (!isSports) {
    const dateStr = new Date(ticket.createdAtEpochMs).toLocaleDateString();
    const uniqueLots = new Set<string>();
    (ticket.plays || []).forEach((p: any) => {
      if (p.lotteryName) {
        p.lotteryName.split(/[\/,]+/).forEach((part: string) => {
          const trimmed = part.trim();
          if (trimmed) uniqueLots.add(trimmed);
        });
      }
    });
    const lotteriesStr = Array.from(uniqueLots).join(' / ');
    
    text = `*BANCA EL FUERTE*\n`;
    text += `*FECHA:* ${dateStr}\n`;
    text += `*SERIAL:* ${ticket.serial || ticket.id}\n`;
    text += `*LOTERÍAS:* ${lotteriesStr}\n`;
    text += `-----------------------\n`;
    (ticket.plays || []).forEach((p: any) => {
      text += `• ${p.number} (${p.playType.toUpperCase()}) - $${Number(p.amount).toFixed(2)}\n`;
    });
    text += `-----------------------\n`;
    text += `*TOTAL APOSTADO:* $${Number(ticket.total).toFixed(2)}\n`;
    if (ticket.totalPrize > 0) {
      text += `*PREMIO ACUMULADO:* $${Number(ticket.totalPrize).toFixed(2)}\n`;
    }
  } else {
    const dateStr = new Date(ticket.soldAt).toLocaleDateString();
    text = `*SPORTS BOOK - ${ticket.bancaName || 'BANCA DEPORTIVA'}*\n`;
    text += `*FECHA:* ${dateStr}\n`;
    text += `*TICKET ID:* ${ticket.ticketCode}\n`;
    text += `*TIPO:* ${String(ticket.ticketType).toUpperCase()}\n`;
    text += `-----------------------\n`;
    (ticket.legs || []).forEach((leg: any) => {
      text += `• ${leg.eventLabel}\n  ${leg.marketTitle} (${leg.selectionLabel}) x${Number(leg.decimalOdds).toFixed(2)} [${String(leg.status).toUpperCase()}]\n`;
    });
    text += `-----------------------\n`;
    text += `*APUESTA:* $${Number(ticket.stake).toFixed(2)}\n`;
    text += `*CUOTA TOTAL:* x${Number(ticket.decimalOdds).toFixed(2)}\n`;
    text += `*POTENCIAL PREMIO:* $${Number(ticket.potentialPayout).toFixed(2)}\n`;
  }
  
  const whatsappUrl = `https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`;
  window.open(whatsappUrl, '_blank');
};
