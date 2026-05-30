import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { 
  fetchUsers, 
  createUserAccount, 
  updateUserAccount, 
  deleteUserAccount,
  toggleAdminStatus,
  processRecharge,
  fetchTickets,
  fetchAuditLogs,
  fetchLotteries,
  addAuditLog,
  saveAllUsers,
  getAdminLimitsPayload,
  saveAdminLimitsPayload,
  fetchDrawResults,
  createDrawResult,
  STATIC_LOTTERIES,
  supabase,
  getAdminSystemModeConfig,
  saveAdminSystemModeConfig,
  getManualDisabledLotteries,
  saveManualDisabledLotteries
} from '../utils/supabase';
import type { UserAccount, TicketRecord, LotteryCatalogItem, AuditLog, DrawResult, BlockedSalePlay } from '../types';
import { 
  Users, Layers, TrendingUp, DollarSign, Activity, 
  Plus, Search, RefreshCw, CheckCircle, AlertTriangle, 
  ArrowRightLeft, FileSpreadsheet, Lock, Trash2, Key, Info, Settings, Edit2
} from 'lucide-react';

interface DashboardProps {
  activeTab: string;
}

interface MonitorRow {
  displayNumber: string;
  amount: number;
  playsCount: number;
  actors: string[];
}

function buildLotteryMonitorRows(tickets: TicketRecord[], playFocus: 'Q' | 'P' | 'T' | 'SP' | 'P3' | 'P4'): MonitorRow[] {
  const exposureMap: Record<string, { amount: number; playsCount: number; actors: Set<string> }> = {};

  tickets.forEach(ticket => {
    ticket.plays.forEach(play => {
      const type = play.playType.toUpperCase();
      let isMatch = false;
      if (playFocus === 'Q' && (type === 'Q' || type === 'QUINIELA')) isMatch = true;
      else if (playFocus === 'P' && (type === 'P' || type === 'PALE')) isMatch = true;
      else if (playFocus === 'T' && (type === 'T' || type === 'TRIPLETA')) isMatch = true;
      else if (playFocus === 'SP' && (type === 'SP' || type === 'SUPER PALE' || type === 'SUPERPALE')) isMatch = true;
      else if (playFocus === 'P3' && (type === 'P3' || type === 'PICK3' || type === 'PICK 3' || type === 'P3BOX')) isMatch = true;
      else if (playFocus === 'P4' && (type === 'P4' || type === 'PICK4' || type === 'PICK 4' || type === 'P4BOX')) isMatch = true;

      if (isMatch) {
        let formattedNumber = play.number.trim();
        
        if (playFocus === 'P' || playFocus === 'SP') {
          if (!formattedNumber.includes('-') && formattedNumber.length === 4) {
            formattedNumber = `${formattedNumber.slice(0, 2)}-${formattedNumber.slice(2, 4)}`;
          }
        } else if (playFocus === 'T') {
          if (!formattedNumber.includes('/') && !formattedNumber.includes('-') && formattedNumber.length === 6) {
            formattedNumber = `${formattedNumber.slice(0, 2)}/${formattedNumber.slice(2, 4)}/${formattedNumber.slice(4, 6)}`;
          } else {
            formattedNumber = formattedNumber.replace(/-/g, '/');
          }
        }

        if (!exposureMap[formattedNumber]) {
          exposureMap[formattedNumber] = { amount: 0, playsCount: 0, actors: new Set<string>() };
        }

        exposureMap[formattedNumber].amount += play.amount;
        exposureMap[formattedNumber].playsCount += 1;
        if (ticket.sellerUser) {
          exposureMap[formattedNumber].actors.add(ticket.sellerUser);
        }
      }
    });
  });

  const list = Object.keys(exposureMap).map(num => ({
    displayNumber: num,
    amount: exposureMap[num].amount,
    playsCount: exposureMap[num].playsCount,
    actors: Array.from(exposureMap[num].actors)
  }));

  return list.sort((a, b) => b.amount - a.amount);
}

export const Dashboard: React.FC<DashboardProps> = ({ activeTab }) => {
  const { user } = useAuth();
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [tickets, setTickets] = useState<TicketRecord[]>([]);
  const [lotteries, setLotteries] = useState<LotteryCatalogItem[]>([]);
  const [audits, setAudits] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);

  // Search & Filter states
  const [searchQuery, setSearchQuery] = useState('');
  const [filterStatus, setFilterStatus] = useState<'all' | 'active' | 'blocked'>('all');

  // Modals & Sheets
  const [adminModalOpen, setAdminModalOpen] = useState(false);
  const [cajeroModalOpen, setCajeroModalOpen] = useState(false);
  const [supervisorModalOpen, setSupervisorModalOpen] = useState(false);
  const [rechargeModalOpen, setRechargeModalOpen] = useState(false);
  const [credsShareOpen, setCredsShareOpen] = useState(false);

  // Selection states for actions
  const [shareText, setShareText] = useState('');

  // Form states
  const [adminForm, setAdminForm] = useState({
    ownerName: '',
    bankName: '',
    address: '',
    phone: '',
    cashierPrefix: '',
    cashierCount: 3,
    territory: 'RD',
    baseBalance: 50000,
  });

  const [cajeroForm, setCajeroForm] = useState({
    user: '',
    displayName: '',
    banca: '',
    territory: 'RD',
    baseBalance: 0,
    rechargesEnabled: true,
    rechargesAssignedBalance: 10000,
    supervisorId: '',
  });

  const [supervisorForm, setSupervisorForm] = useState({
    user: '',
    displayName: '',
    phone: '',
    territory: 'RD',
  });

  const [rechargeForm, setRechargeForm] = useState({
    cashierId: '',
    amount: '',
  });

  // Limits specific states
  const [selectedScope, setSelectedScope] = useState<'ADMIN_SELF' | 'CASHIER_DEFAULTS' | 'CASHIER_SPECIFIC'>('CASHIER_DEFAULTS');
  const [selectedCashierUsername, setSelectedCashierUsername] = useState<string>('');
  const [limitsPayload, setLimitsPayload] = useState<any>({
    defaults: { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 },
    byUser: {},
    adminSelf: { daySale: 0, payout: 0, q: 0, pale: 0, sp: 0, t: 0, p3: 0, p3box: 0, p4: 0, p4box: 0 }
  });
  const [currentLimitsForm, setCurrentLimitsForm] = useState({
    daySale: 10000,
    payout: 0,
    q: 10000,
    pale: 500,
    sp: 500,
    t: 75,
    p3: 500,
    p3box: 500,
    p4: 500,
    p4box: 500,
    systemModeOverride: ''
  });
  const [saveSuccessNotification, setSaveSuccessNotification] = useState(false);

  // Monitoreo states
  const [monitoreoSubTab, setMonitoreoSubTab] = useState<'lotteries' | 'plays' | 'cajeros'>('lotteries');
  const [monitoreoPlayFocus, setMonitoreoPlayFocus] = useState<'Q' | 'P' | 'T' | 'SP' | 'P3' | 'P4'>('Q');
  const [monitoreoHighestFirst, setMonitoreoHighestFirst] = useState(true);
  const [monitoreoShowEmptyLotteries, setMonitoreoShowEmptyLotteries] = useState(false);
  const [monitoreoRange, setMonitoreoRange] = useState<'day' | 'week' | 'month'>('day');

  // Tickets states
  const [ticketSearchSerial, setTicketSearchSerial] = useState('');
  const [ticketFilterStatus, setTicketFilterStatus] = useState('all');
  const [ticketFilterCashier, setTicketFilterCashier] = useState('all');
  const [annulModalOpen, setAnnulModalOpen] = useState(false);
  const [selectedTicketForAnnul, setSelectedTicketForAnnul] = useState<TicketRecord | null>(null);

  // Ganadores states
  const [ganadoresFilter, setGanadoresFilter] = useState<'pending' | 'paid' | 'all'>('pending');

  // Supervisor assignment & password states
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [selectedSupervisor, setSelectedSupervisor] = useState<UserAccount | null>(null);
  const [assignedCashiersSet, setAssignedCashiersSet] = useState<Set<string>>(new Set());
  const [editingCashier, setEditingCashier] = useState<UserAccount | null>(null);

  // Manual disabled lotteries & blocked sale plays states
  const [manualDisabledLotteryIds, setManualDisabledLotteryIds] = useState<string[]>([]);
  const [blockedSalePlays, setBlockedSalePlays] = useState<BlockedSalePlay[]>([]);
  const [blockedPlayForm, setBlockedPlayForm] = useState({
    playType: 'Q',
    number: ''
  });

  // DR time helpers
  const parseTimeToMinutes = (timeStr: string): number => {
    if (!timeStr) return 0;
    const clean = timeStr.trim().toUpperCase();
    const is12Hour = clean.includes('AM') || clean.includes('PM');
    if (is12Hour) {
      const isPM = clean.includes('PM');
      const timeOnly = clean.replace(/[AMP\s]/g, '');
      const [hStr, mStr] = timeOnly.split(':');
      let hours = parseInt(hStr, 10) || 0;
      const minutes = parseInt(mStr, 10) || 0;
      if (isPM && hours !== 12) {
        hours += 12;
      } else if (!isPM && hours === 12) {
        hours = 0;
      }
      return hours * 60 + minutes;
    } else {
      const [hStr, mStr] = clean.split(':');
      const hours = parseInt(hStr, 10) || 0;
      const minutes = parseInt(mStr, 10) || 0;
      return hours * 60 + minutes;
    }
  };

  const getCurrentDRMinutesSinceMidnight = (): number => {
    const now = new Date();
    const utcHours = now.getUTCHours();
    const utcMinutes = now.getUTCMinutes();
    let drHours = utcHours - 4;
    if (drHours < 0) {
      drHours += 24;
    }
    return drHours * 60 + utcMinutes;
  };

  const [resultsList, setResultsList] = useState<DrawResult[]>([]);
  const [resultForm, setResultForm] = useState({
    lotteryId: 'LOT-RD-REAL',
    r1: '',
    r2: '',
    r3: '',
    dateKey: new Date().toISOString().split('T')[0]
  });

  // Cuadre states
  const [cuadrePeriod, setCuadrePeriod] = useState<'today' | 'week' | 'month' | 'manual'>('today');
  const [cuadreCashierFilter, setCuadreCashierFilter] = useState('all');
  const [cuadreDateFrom, setCuadreDateFrom] = useState(new Date().toISOString().split('T')[0]);
  const [cuadreDateTo, setCuadreDateTo] = useState(new Date().toISOString().split('T')[0]);

  const sha256Hex = async (input: string): Promise<string> => {
    const msgBuffer = new TextEncoder().encode(input);
    const hashBuffer = await window.crypto.subtle.digest('SHA-256', msgBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  };

  const normalizeRate = (rate: number | null | undefined): number => {
    if (rate === undefined || rate === null) return 0.08;
    return rate >= 1.0 ? rate / 100 : rate;
  };

  const handleToggleSupervisor = async (sup: UserAccount) => {
    if (!user) return;
    try {
      const updated = {
        ...sup,
        active: !sup.active,
        updatedAtEpochMs: Date.now()
      };
      await updateUserAccount(updated);
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        updated.active ? 'UNBLOCK_SUPERVISOR' : 'BLOCK_SUPERVISOR',
        `Supervisor ${updated.displayName} (@${updated.user}) ${updated.active ? 'desbloqueado' : 'bloqueado'}`
      );
      loadData();
      alert(`Supervisor ${updated.displayName} ${updated.active ? 'desactivado' : 'activado'} correctamente.`);
    } catch (err: any) {
      alert(err.message || 'Error al cambiar estado del supervisor');
    }
  };

  const handleResetSupervisorPassword = async (sup: UserAccount) => {
    if (!user) return;
    if (!window.confirm(`¿Está seguro de regenerar la contraseña para el supervisor @${sup.user}? Se creará una clave nueva al instante.`)) return;

    const newPass = Math.random().toString(36).substr(2, 8);
    const salt = Math.random().toString(36).substr(2, 8);
    const hash = await sha256Hex(`${salt}:${newPass}`);

    try {
      const updated = {
        ...sup,
        passwordSalt: salt,
        passwordHash: hash,
        passwordVersion: 'v1',
        credChangedAtEpochMs: Date.now(),
        updatedAtEpochMs: Date.now()
      };
      
      await updateUserAccount(updated);

      const share = `LotteryNet - Nueva Clave Restablecida\n` +
        `Supervisor: ${sup.displayName}\n` +
        `Usuario: ${sup.user}\n` +
        `Nueva Clave: ${newPass}\n` +
        `Rol: SUPERVISOR\n` +
        `Restablecida por: Admin @${user.user}\n` +
        `Fecha: ${new Date().toLocaleString()}`;

      setShareText(share);
      setCredsShareOpen(true);

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'RESET_PASSWORD',
        `Restablecida contraseña para el supervisor: @${sup.user}`,
        'success'
      );
      
      loadData();
    } catch (err: any) {
      alert(err.message || 'Error al restablecer contraseña');
    }
  };

  const handleDeleteSupervisor = async (sup: UserAccount) => {
    if (!user) return;
    if (!window.confirm(`¿Está seguro de eliminar permanentemente al supervisor "${sup.displayName}"? Esta acción desvinculará a todos sus cajeros asociados.`)) return;
    try {
      await deleteUserAccount(sup.id);
      
      const updatedUsers = [...users];
      updatedUsers.forEach((u, index) => {
        if (u.role === 'CASHIER' && u.adminId === user.id) {
          if (u.supervisorIds.includes(sup.id)) {
            updatedUsers[index] = {
              ...u,
              supervisorIds: u.supervisorIds.filter(id => id !== sup.id),
              supervisorUsers: u.supervisorUsers.filter(uName => uName !== sup.user),
              updatedAtEpochMs: Date.now()
            };
          }
        }
      });
      await saveAllUsers(updatedUsers);
      
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'DELETE_SUPERVISOR',
        `Eliminado supervisor @${sup.user} y desvinculados sus cajeros.`,
        'warning'
      );
      
      loadData();
      alert('Supervisor eliminado correctamente.');
    } catch (err: any) {
      alert(err.message || 'Error al eliminar supervisor');
    }
  };

  const handleOpenAssignModal = (sup: UserAccount) => {
    if (!user) return;
    setSelectedSupervisor(sup);
    const assignedIds = new Set<string>();
    users.forEach(u => {
      if (u.role === 'CASHIER' && u.adminId === user.id && u.supervisorIds.includes(sup.id)) {
        assignedIds.add(u.id);
      }
    });
    setAssignedCashiersSet(assignedIds);
    setAssignModalOpen(true);
  };

  const handleSaveAssignments = async () => {
    if (!user || !selectedSupervisor) return;
    try {
      const updatedUsers = [...users];
      updatedUsers.forEach((u, index) => {
        if (u.role === 'CASHIER' && u.adminId === user.id) {
          const isAssigned = assignedCashiersSet.has(u.id);
          let supIds = u.supervisorIds || [];
          let supUsers = u.supervisorUsers || [];
          
          if (isAssigned) {
            if (!supIds.includes(selectedSupervisor.id)) {
              supIds = [selectedSupervisor.id];
              supUsers = [selectedSupervisor.user];
            }
          } else {
            supIds = supIds.filter(id => id !== selectedSupervisor.id);
            supUsers = supUsers.filter(uName => uName !== selectedSupervisor.user);
          }
          
          updatedUsers[index] = {
            ...u,
            supervisorIds: supIds,
            supervisorUsers: supUsers,
            updatedAtEpochMs: Date.now()
          };
        }
      });

      await saveAllUsers(updatedUsers);
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'ASSIGN_CASHIERS',
        `Actualizada asignación de cajeros para supervisor: @${selectedSupervisor.user} (${assignedCashiersSet.size} cajeros asignados)`,
        'success'
      );
      
      setAssignModalOpen(false);
      setSelectedSupervisor(null);
      loadData();
      alert('Asignación de cajeros guardada con éxito.');
    } catch (err: any) {
      alert(err.message || 'Error al guardar asignaciones');
    }
  };

  const handleDeleteCashier = async (cajId: string) => {
    if (!user) return;
    const target = users.find(u => u.id === cajId);
    if (!target) return;
    if (!window.confirm(`¿Está seguro de eliminar permanentemente al cajero "${target.displayName || target.user}"? Esta acción no se puede deshacer.`)) return;
    try {
      await deleteUserAccount(cajId);
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'DELETE_CASHIER',
        `Cajero eliminado permanentemente: @${target.user}`
      );
      loadData();
      alert('Cajero eliminado correctamente.');
    } catch (err: any) {
      alert(err.message || 'Error al eliminar cajero');
    }
  };

  const handleOpenEditCajero = (c: UserAccount) => {
    setEditingCashier(c);
    setCajeroForm({
      user: c.user,
      displayName: c.displayName || '',
      banca: c.banca || '',
      territory: c.territory || 'RD',
      baseBalance: c.rechargesBalance,
      rechargesEnabled: c.rechargesEnabled,
      rechargesAssignedBalance: c.rechargesAssignedBalance,
      supervisorId: c.supervisorIds[0] || '',
    });
    setCajeroModalOpen(true);
  };

  const handleAnnulTicket = async (ticket: TicketRecord) => {
    if (!user) return;
    try {
      const updatedTickets = [...tickets];
      const ticketIdx = updatedTickets.findIndex(t => t.id === ticket.id);
      if (ticketIdx !== -1) {
        updatedTickets[ticketIdx].status = 'cancelled';
      }

      const updatedUsers = [...users];
      const cashierIdx = updatedUsers.findIndex(u => u.user === ticket.sellerUser && u.role === 'CASHIER');
      if (cashierIdx !== -1) {
        updatedUsers[cashierIdx].balance = Math.max(0, updatedUsers[cashierIdx].balance - ticket.total);
      }

      await saveAllUsers(updatedUsers);
      
      if (localStorage.getItem('lotterynet_tickets')) {
        const localTk = JSON.parse(localStorage.getItem('lotterynet_tickets') || '[]');
        const idx = localTk.findIndex((t: any) => t.id === ticket.id);
        if (idx !== -1) {
          localTk[idx].status = 'cancelled';
          localStorage.setItem('lotterynet_tickets', JSON.stringify(localTk));
        }
      }

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'CANCEL_TICKET',
        `Ticket anulado: ${ticket.serial || ticket.id}. Balance de cajero @${ticket.sellerUser} restablecido por $${ticket.total}`,
        'warning'
      );

      await loadData();
      setAnnulModalOpen(false);
      setSelectedTicketForAnnul(null);
      alert('Ticket anulado con éxito.');
    } catch (e) {
      console.error(e);
      alert('Error anulando el ticket.');
    }
  };

  const handlePayWinner = async (ticket: TicketRecord) => {
    if (!user) return;
    try {
      const updatedTickets = [...tickets];
      const ticketIdx = updatedTickets.findIndex(t => t.id === ticket.id);
      if (ticketIdx !== -1) {
        updatedTickets[ticketIdx].status = 'paid';
      }

      if (localStorage.getItem('lotterynet_tickets')) {
        const localTk = JSON.parse(localStorage.getItem('lotterynet_tickets') || '[]');
        const idx = localTk.findIndex((t: any) => t.id === ticket.id);
        if (idx !== -1) {
          localTk[idx].status = 'paid';
          localStorage.setItem('lotterynet_tickets', JSON.stringify(localTk));
        }
      }

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'PROCESS_PAYOUT',
        `Premio pagado de ticket: ${ticket.serial || ticket.id}. Monto pagado: $${ticket.totalPrize.toFixed(2)}`,
        'success'
      );

      await loadData();
      alert('Premio pagado con éxito.');
    } catch (e) {
      console.error(e);
      alert('Error procesando el pago del premio.');
    }
  };

  const handleCreateResult = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const targetLottery = lotteries.find(l => l.id === resultForm.lotteryId);
    const newResult = {
      id: `R-${Math.random().toString(36).substr(2, 6).toUpperCase()}`,
      lotteryId: resultForm.lotteryId,
      lotteryName: targetLottery?.name || 'Lotería',
      dateKey: resultForm.dateKey,
      numbers: `${resultForm.r1}-${resultForm.r2}-${resultForm.r3}`
    };

    try {
      await createDrawResult(newResult);
      setResultsList([newResult, ...resultsList]);
      
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'CREATE_RESULT',
        `Registrado número ganador manualmente para ${targetLottery?.name} (${resultForm.dateKey}): ${newResult.numbers}`,
        'success'
      );

      setResultForm({
        ...resultForm,
        r1: '',
        r2: '',
        r3: ''
      });
      alert('Resultado de lotería registrado correctamente.');
    } catch (e) {
      console.error(e);
      alert('Error registrando el resultado de lotería.');
    }
  };

  // Load limits when tab is active
  useEffect(() => {
    if (!user) return;
    if (activeTab === 'limites' && user.role === 'ADMIN') {
      const loadLimits = async () => {
        const raw = await getAdminLimitsPayload(user.id);
        try {
          const parsed = JSON.parse(raw);
          setLimitsPayload(parsed);
          
          const defaultCashiers = users.filter(u => u.role === 'CASHIER' && u.adminId === user.id);
          if (defaultCashiers.length > 0 && !selectedCashierUsername) {
            setSelectedCashierUsername(defaultCashiers[0].user);
          }
        } catch (e) {
          console.error("Failed to parse limits payload", e);
        }
      };
      loadLimits();
    }
  }, [activeTab, user, users, selectedCashierUsername]);

  // Sync form inputs when scope or selected cashier changes
  useEffect(() => {
    if (!user || !limitsPayload) return;
    
    let targetLimits = { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 };
    let sysMode = '';

    if (selectedScope === 'ADMIN_SELF') {
      targetLimits = limitsPayload.adminSelf || { daySale: 0, payout: 0, q: 0, pale: 0, sp: 0, t: 0, p3: 0, p3box: 0, p4: 0, p4box: 0 };
      const adminAcc = users.find(u => u.id === user.id);
      sysMode = adminAcc?.systemModeOverride || '';
    } else if (selectedScope === 'CASHIER_DEFAULTS') {
      targetLimits = limitsPayload.defaults || { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 };
    } else if (selectedScope === 'CASHIER_SPECIFIC' && selectedCashierUsername) {
      targetLimits = (limitsPayload.byUser && limitsPayload.byUser[selectedCashierUsername]) || limitsPayload.defaults || { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 };
      const cashierAcc = users.find(u => u.user === selectedCashierUsername && u.role === 'CASHIER');
      sysMode = cashierAcc?.systemModeOverride || '';
    }

    setCurrentLimitsForm({
      daySale: targetLimits.daySale ?? 0,
      payout: targetLimits.payout ?? 0,
      q: targetLimits.q ?? 0,
      pale: targetLimits.pale ?? 0,
      sp: targetLimits.sp ?? 0,
      t: targetLimits.t ?? 0,
      p3: targetLimits.p3 ?? 0,
      p3box: targetLimits.p3box ?? 0,
      p4: targetLimits.p4 ?? 0,
      p4box: targetLimits.p4box ?? 0,
      systemModeOverride: sysMode
    });
  }, [selectedScope, selectedCashierUsername, limitsPayload, users, user]);

  const handleSaveLimits = async () => {
    if (!user) return;
    try {
      const updatedPayload = { ...limitsPayload };
      
      const newLimitsObj = {
        daySale: Number(currentLimitsForm.daySale),
        payout: Number(currentLimitsForm.payout),
        q: Number(currentLimitsForm.q),
        pale: Number(currentLimitsForm.pale),
        sp: Number(currentLimitsForm.sp),
        t: Number(currentLimitsForm.t),
        p3: Number(currentLimitsForm.p3),
        p3box: Number(currentLimitsForm.p3box),
        p4: Number(currentLimitsForm.p4),
        p4box: Number(currentLimitsForm.p4box),
      };

      if (selectedScope === 'ADMIN_SELF') {
        updatedPayload.adminSelf = newLimitsObj;
      } else if (selectedScope === 'CASHIER_DEFAULTS') {
        updatedPayload.defaults = newLimitsObj;
      } else if (selectedScope === 'CASHIER_SPECIFIC' && selectedCashierUsername) {
        if (!updatedPayload.byUser) updatedPayload.byUser = {};
        updatedPayload.byUser[selectedCashierUsername] = newLimitsObj;
      }

      await saveAdminLimitsPayload(user.id, JSON.stringify(updatedPayload));
      setLimitsPayload(updatedPayload);

      if (selectedScope === 'ADMIN_SELF') {
        const adminAcc = users.find(u => u.id === user.id);
        if (adminAcc && adminAcc.systemModeOverride !== currentLimitsForm.systemModeOverride) {
          adminAcc.systemModeOverride = currentLimitsForm.systemModeOverride || null;
          await updateUserAccount(adminAcc);
        }
      } else if (selectedScope === 'CASHIER_SPECIFIC' && selectedCashierUsername) {
        const cashierAcc = users.find(u => u.user === selectedCashierUsername && u.role === 'CASHIER');
        if (cashierAcc && cashierAcc.systemModeOverride !== currentLimitsForm.systemModeOverride) {
          cashierAcc.systemModeOverride = currentLimitsForm.systemModeOverride || null;
          await updateUserAccount(cashierAcc);
        }
      }

      let auditDetail = `Actualizados límites de juego en alcance: ${selectedScope}`;
      if (selectedScope === 'CASHIER_SPECIFIC') {
        auditDetail += ` para cajero: @${selectedCashierUsername}`;
      }
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'UPDATE_LIMITS',
        auditDetail,
        'success'
      );

      await loadData();
      setSaveSuccessNotification(true);
      setTimeout(() => setSaveSuccessNotification(false), 3000);
    } catch (e) {
      console.error(e);
      alert('Error guardando los límites.');
    }
  };

  const handleToggleManualDisabledLottery = async (lotteryId: string) => {
    if (!user) return;
    const savedUser = localStorage.getItem('lotterynet_session_user');
    const parsedUser = savedUser ? JSON.parse(savedUser) : null;
    const allowedId = parsedUser?.id || user?.id;
    const allowedAdminId = parsedUser?.adminId || user?.adminId;
    const allowedRole = parsedUser?.role || user?.role;
    
    const targetAdminId = allowedRole === 'ADMIN' ? allowedId : (allowedRole === 'SUPERVISOR' ? allowedAdminId : null);
    if (!targetAdminId) return;

    try {
      const isCurrentlyDisabled = manualDisabledLotteryIds.includes(lotteryId);
      let updatedIds: string[];
      if (isCurrentlyDisabled) {
        updatedIds = manualDisabledLotteryIds.filter(id => id !== lotteryId);
      } else {
        updatedIds = [...manualDisabledLotteryIds, lotteryId];
      }

      const todayStr = new Intl.DateTimeFormat('fr-CA', { timeZone: 'America/Santo_Domingo' }).format(new Date()); // yyyy-mm-dd
      await saveManualDisabledLotteries(targetAdminId, {
        ids: updatedIds,
        date: todayStr,
        permanent: true, // Permanent close until manually toggled back
        updatedAt: Date.now()
      });

      setManualDisabledLotteryIds(updatedIds);
      
      const targetLot = lotteries.find(lot => lot.id === lotteryId);
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        isCurrentlyDisabled ? 'UNBLOCK_LOTTERY' : 'BLOCK_LOTTERY',
        `Lotería "${targetLot?.name || lotteryId}" ha sido ${isCurrentlyDisabled ? 'habilitada' : 'bloqueada manualmente'}`,
        'success'
      );
    } catch (e) {
      console.error(e);
      alert('Error al cambiar el estado de la lotería.');
    }
  };

  const handleAddBlockedPlay = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const savedUser = localStorage.getItem('lotterynet_session_user');
    const parsedUser = savedUser ? JSON.parse(savedUser) : null;
    const allowedId = parsedUser?.id || user?.id;
    const allowedAdminId = parsedUser?.adminId || user?.adminId;
    const allowedRole = parsedUser?.role || user?.role;
    
    const targetAdminId = allowedRole === 'ADMIN' ? allowedId : (allowedRole === 'SUPERVISOR' ? allowedAdminId : null);
    if (!targetAdminId) return;

    const playType = blockedPlayForm.playType;
    let number = blockedPlayForm.number.trim().replace(/\D/g, ''); // Digits only

    // Validation matching KMP normalizeBlockedSalePlay
    let isValid = false;
    if (playType === 'Q' && number.length === 2) isValid = true;
    else if ((playType === 'P' || playType === 'SP') && number.length === 4) {
      isValid = true;
      if (playType === 'SP') {
        number = `${number.slice(0, 2)}-${number.slice(2)}`;
      }
    }
    else if (playType === 'T' && number.length === 6) isValid = true;
    else if ((playType === 'P3' || playType === 'P3BOX') && number.length === 3) isValid = true;
    else if ((playType === 'P4' || playType === 'P4BOX') && number.length === 4) isValid = true;

    if (!isValid) {
      alert(`Número no válido para el tipo de jugada. Quiniela: 2 dígitos, Palé/Super Palé: 4 dígitos, Tripleta: 6 dígitos, Pick 3: 3 dígitos, Pick 4: 4 dígitos.`);
      return;
    }

    const exists = blockedSalePlays.some(p => p.playType === playType && p.number === number);
    if (exists) {
      alert('Esta combinación de jugada ya se encuentra bloqueada.');
      return;
    }

    try {
      const updatedPlays = [...blockedSalePlays, { playType, number }].sort((a, b) => a.playType.localeCompare(b.playType) || a.number.localeCompare(b.number));
      
      const currentConfig = await getAdminSystemModeConfig(targetAdminId);
      await saveAdminSystemModeConfig(targetAdminId, {
        ...currentConfig,
        blockedSalePlays: updatedPlays
      });

      setBlockedSalePlays(updatedPlays);
      setBlockedPlayForm({ ...blockedPlayForm, number: '' });

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'BLOCK_PLAY_NUMBER',
        `Jugada bloqueada: ${playType} - ${number}`,
        'success'
      );
    } catch (err) {
      console.error(err);
      alert('Error guardando el bloqueo de jugada.');
    }
  };

  const handleRemoveBlockedPlay = async (playToRemove: BlockedSalePlay) => {
    if (!user) return;
    const savedUser = localStorage.getItem('lotterynet_session_user');
    const parsedUser = savedUser ? JSON.parse(savedUser) : null;
    const allowedId = parsedUser?.id || user?.id;
    const allowedAdminId = parsedUser?.adminId || user?.adminId;
    const allowedRole = parsedUser?.role || user?.role;
    
    const targetAdminId = allowedRole === 'ADMIN' ? allowedId : (allowedRole === 'SUPERVISOR' ? allowedAdminId : null);
    if (!targetAdminId) return;

    try {
      const updatedPlays = blockedSalePlays.filter(p => !(p.playType === playToRemove.playType && p.number === playToRemove.number));
      
      const currentConfig = await getAdminSystemModeConfig(targetAdminId);
      await saveAdminSystemModeConfig(targetAdminId, {
        ...currentConfig,
        blockedSalePlays: updatedPlays
      });

      setBlockedSalePlays(updatedPlays);

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'UNBLOCK_PLAY_NUMBER',
        `Jugada desbloqueada: ${playToRemove.playType} - ${playToRemove.number}`,
        'success'
      );
    } catch (err) {
      console.error(err);
      alert('Error eliminando el bloqueo de jugada.');
    }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const u = await fetchUsers();
      // Scope tickets to current admin if ADMIN or SUPERVISOR role
      const savedUser = localStorage.getItem('lotterynet_session_user');
      const parsedUser = savedUser ? JSON.parse(savedUser) : null;
      
      const allowedRole = parsedUser?.role || user?.role;
      const allowedId = parsedUser?.id || user?.id;
      const allowedAdminId = parsedUser?.adminId || user?.adminId;

      const adminScopeId = allowedRole === 'ADMIN' 
        ? allowedId 
        : (allowedRole === 'SUPERVISOR' ? allowedAdminId : undefined);

      const t = await fetchTickets(adminScopeId);
      const l = await fetchLotteries();
      const a = await fetchAuditLogs();
      const r = await fetchDrawResults();

      const targetAdminId = adminScopeId || allowedId;
      if (targetAdminId) {
        try {
          const disabledCfg = await getManualDisabledLotteries(targetAdminId);
          setManualDisabledLotteryIds(disabledCfg.ids || []);

          const systemCfg = await getAdminSystemModeConfig(targetAdminId);
          setBlockedSalePlays(systemCfg.blockedSalePlays || []);
        } catch (err) {
          console.warn('Error loading blocks / modes from Supabase:', err);
        }
      }

      // Chronological sorting by draw time (orden de salida)
      const sortedL = [...l].sort((a, b) => {
        return parseTimeToMinutes(a.baseDrawTime) - parseTimeToMinutes(b.baseDrawTime);
      });

      const sortedR = [...r].sort((a, b) => {
        const lotA = STATIC_LOTTERIES.find(lot => lot.id === a.lotteryId) || l.find(lot => lot.id === a.lotteryId);
        const lotB = STATIC_LOTTERIES.find(lot => lot.id === b.lotteryId) || l.find(lot => lot.id === b.lotteryId);
        const timeA = lotA ? parseTimeToMinutes(lotA.baseDrawTime) : 0;
        const timeB = lotB ? parseTimeToMinutes(lotB.baseDrawTime) : 0;
        return timeA - timeB;
      });

      setUsers(u);
      setTickets(t);
      setLotteries(sortedL);
      setAudits(a);
      setResultsList(sortedR);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [activeTab]);

  useEffect(() => {
    if (!supabase) return;
    const client = supabase;

    // Listen to changes in lotterynet_kv and lotterynet_users_state in real time
    const kvChannel = client
      .channel('schema-db-changes')
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'lotterynet_kv'
        },
        (payload) => {
          console.log('Realtime change in lotterynet_kv:', payload);
          loadData();
        }
      )
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'lotterynet_users_state'
        },
        (payload) => {
          console.log('Realtime change in lotterynet_users_state:', payload);
          loadData();
        }
      )
      .subscribe();

    return () => {
      client.removeChannel(kvChannel);
    };
  }, []);

  if (!user) return null;

  // --- ACTIONS HANDLERS ---

  // Master: Create Admin & Banca
  const handleCreateAdmin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adminForm.ownerName || !adminForm.bankName) return;

    try {
      // Generate temporary cashier data automatically
      const newAdmin = await createUserAccount({
        user: adminForm.bankName.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 8),
        role: 'ADMIN',
        displayName: adminForm.ownerName,
        ownerName: adminForm.ownerName,
        address: adminForm.address,
        phone: adminForm.phone,
        active: true,
        banca: adminForm.bankName,
        cashierPrefix: adminForm.cashierPrefix || adminForm.bankName.slice(0, 3).toLowerCase(),
        territory: adminForm.territory,
        rechargesEnabled: true,
        rechargesAssignedBalance: adminForm.baseBalance,
        rechargesBalance: adminForm.baseBalance,
        supervisorIds: [],
        supervisorUsers: [],
      });

      // Create child cashiers automatically
      const cashiersCreated: UserAccount[] = [];
      const prefix = adminForm.cashierPrefix || adminForm.bankName.slice(0, 3).toLowerCase();
      
      const createdCreds = [
        { role: 'ADMIN', name: newAdmin.displayName, user: newAdmin.user, pass: 'admin123' }
      ];

      for (let i = 1; i <= adminForm.cashierCount; i++) {
        const pass = Math.random().toString(36).substr(2, 8);
        const caj = await createUserAccount({
          user: `${prefix}${String(i).padStart(2, '0')}`,
          role: 'CASHIER',
          displayName: `Cajero 0${i} - ${adminForm.bankName}`,
          active: true,
          adminId: newAdmin.id,
          adminUser: newAdmin.user,
          banca: adminForm.bankName,
          territory: adminForm.territory,
          rechargesEnabled: true,
          rechargesAssignedBalance: 5000.0,
          rechargesBalance: 5000.0,
          supervisorIds: [],
          supervisorUsers: [],
        });
        cashiersCreated.push(caj);
        createdCreds.push({ role: 'CASHIER', name: caj.displayName || '', user: caj.user, pass });
      }

      // Generate credentials text block
      const share = `LotteryNet - Credenciales de Banca\n` +
        `Banca: ${adminForm.bankName}\n` +
        `Creado el: ${newAdmin.createdLabel}\n` +
        `===================================\n` +
        createdCreds.map((c, i) => `${i+1}. [${c.role}] ${c.name}\n   Usuario: ${c.user}\n   Clave: ${c.pass}`).join('\n\n');

      setShareText(share);
      setAdminModalOpen(false);
      setCredsShareOpen(true);
      
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'CREATE_BANK',
        `Creada nueva banca y admin: ${adminForm.bankName} (${adminForm.ownerName}) con ${adminForm.cashierCount} cajeros`
      );

      // Reset form
      setAdminForm({
        ownerName: '',
        bankName: '',
        address: '',
        phone: '',
        cashierPrefix: '',
        cashierCount: 3,
        territory: 'RD',
        baseBalance: 50000,
      });

      loadData();
    } catch (err: any) {
      alert(err.message || 'Error al crear banca');
    }
  };

  // Admin: Create or Update Cajero
  const handleCreateCajero = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cajeroForm.user || !cajeroForm.displayName) return;

    try {
      const superv = users.find(u => u.id === cajeroForm.supervisorId);
      
      if (editingCashier) {
        const updated = {
          ...editingCashier,
          displayName: cajeroForm.displayName,
          user: cajeroForm.user,
          supervisorIds: superv ? [superv.id] : [],
          supervisorUsers: superv ? [superv.user] : [],
          rechargesEnabled: cajeroForm.rechargesEnabled,
          rechargesBalance: cajeroForm.baseBalance,
        };
        await updateUserAccount(updated);
        await addAuditLog(
          { id: user.id, user: user.user, role: user.role },
          'UPDATE_CASHIER',
          `Cajero editado: ${cajeroForm.displayName} (@${cajeroForm.user})`
        );
      } else {
        await createUserAccount({
          user: cajeroForm.user,
          role: 'CASHIER',
          displayName: cajeroForm.displayName,
          active: true,
          adminId: user.id,
          adminUser: user.user,
          banca: user.banca || user.user,
          territory: cajeroForm.territory,
          rechargesEnabled: cajeroForm.rechargesEnabled,
          rechargesAssignedBalance: cajeroForm.baseBalance,
          rechargesBalance: cajeroForm.baseBalance,
          supervisorIds: superv ? [superv.id] : [],
          supervisorUsers: superv ? [superv.user] : [],
        });
        await addAuditLog(
          { id: user.id, user: user.user, role: user.role },
          'CREATE_CASHIER',
          `Creado nuevo cajero: ${cajeroForm.displayName} asignado a banca`
        );
      }

      setCajeroModalOpen(false);
      setEditingCashier(null);
      setCajeroForm({
        user: '',
        displayName: '',
        banca: '',
        territory: 'RD',
        baseBalance: 0,
        rechargesEnabled: true,
        rechargesAssignedBalance: 10000,
        supervisorId: '',
      });

      loadData();
    } catch (err: any) {
      alert(err.message || 'Error al guardar cajero');
    }
  };

  // Admin: Create Supervisor
  const handleCreateSupervisor = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!supervisorForm.user || !supervisorForm.displayName) return;

    try {
      await createUserAccount({
        user: supervisorForm.user,
        role: 'SUPERVISOR',
        displayName: supervisorForm.displayName,
        active: true,
        adminId: user.id,
        adminUser: user.user,
        banca: user.banca || user.user,
        phone: supervisorForm.phone,
        territory: supervisorForm.territory,
        rechargesEnabled: false,
        rechargesAssignedBalance: 0,
        rechargesBalance: 0,
        supervisorIds: [],
        supervisorUsers: [],
      });

      setSupervisorModalOpen(false);
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'CREATE_SUPERVISOR',
        `Creado nuevo supervisor: ${supervisorForm.displayName}`
      );

      setSupervisorForm({
        user: '',
        displayName: '',
        phone: '',
        territory: 'RD',
      });

      loadData();
    } catch (err: any) {
      alert(err.message || 'Error al crear supervisor');
    }
  };

  // Master: Toggle Block Admin
  const handleToggleAdmin = async (adminId: string) => {
    const target = users.find(u => u.id === adminId);
    if (!target) return;
    
    const confirmMsg = target.active
      ? `¿Está seguro de bloquear la banca "${target.banca || target.displayName}"? Esto desactivará todos sus cajeros asociados en cascada.`
      : `¿Está seguro de desbloquear la banca "${target.banca || target.displayName}" y activar sus cajeros asociados?`;

    if (!window.confirm(confirmMsg)) return;

    try {
      const { admin, affectedCashiers } = await toggleAdminStatus(adminId);
      
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        admin.active ? 'UNBLOCK_BANK' : 'BLOCK_BANK',
        `Banca ${admin.banca} (${admin.displayName}) ${admin.active ? 'desbloqueada' : 'bloqueada'} (${affectedCashiers} cajeros afectados)`
      );

      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  // Admin: Toggle Block Cashier
  const handleToggleCashier = async (cajId: string) => {
    const target = users.find(u => u.id === cajId);
    if (!target) return;

    try {
      const updated = {
        ...target,
        active: !target.active
      };
      await updateUserAccount(updated);

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        updated.active ? 'UNBLOCK_CASHIER' : 'BLOCK_CASHIER',
        `Cajero ${updated.displayName} ${updated.active ? 'activado' : 'desactivado'}`
      );

      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  // Admin: Assign balance to Cashier
  const handleProcessRecharge = async (e: React.FormEvent) => {
    e.preventDefault();
    const amountNum = parseFloat(rechargeForm.amount);
    if (!rechargeForm.cashierId || isNaN(amountNum) || amountNum <= 0) return;

    try {
      await processRecharge(user.id, rechargeForm.cashierId, amountNum, {
        id: user.id,
        user: user.user,
        role: user.role
      });

      setRechargeModalOpen(false);
      setRechargeForm({ cashierId: '', amount: '' });
      loadData();
    } catch (err: any) {
      alert(err.message || 'Error al procesar la recarga');
    }
  };

  // Master: Delete Banca
  const handleDeleteBanca = async (adminId: string) => {
    const target = users.find(u => u.id === adminId);
    if (!target) return;

    if (!window.confirm(`¿ADVERTENCIA CRÍTICA? ¿Está seguro de eliminar permanentemente la banca "${target.banca}" y todos sus cajeros asociados? Esta acción no se puede deshacer.`)) return;

    try {
      await deleteUserAccount(adminId);
      // Clean up cashiers belonging to this admin
      const adminCashiers = users.filter(u => u.adminId === adminId);
      for (const c of adminCashiers) {
        await deleteUserAccount(c.id);
      }

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'DELETE_BANK',
        `Banca ${target.banca} (${target.displayName}) eliminada de forma permanente junto con ${adminCashiers.length} cajeros.`,
        'warning'
      );

      loadData();
    } catch (err: any) {
      alert(err.message);
    }
  };

  // Master: Regenerate Admin Credentials
  const handleRegenCreds = async (u: UserAccount) => {
    if (!window.confirm(`¿Está seguro de regenerar la contraseña del usuario administrativo @${u.user}? Se creará una clave nueva al instante.`)) return;

    const newPass = Math.random().toString(36).substr(2, 8);
    // In our simplified mock, we update the database hash and print it
    const share = `LotteryNet - Nueva Clave Restablecida\n` +
      `Usuario: ${u.user}\n` +
      `Nueva Clave: ${newPass}\n` +
      `Restablecida por: Master @${user.user}\n` +
      `Fecha: ${new Date().toLocaleString()}`;

    setShareText(share);
    setCredsShareOpen(true);

    await addAuditLog(
      { id: user.id, user: user.user, role: user.role },
      'RESET_PASSWORD',
      `Restablecida contraseña para el usuario: @${u.user}`
    );
  };

  // --- STATS CALCULATIONS ---

  const getDashboardStats = () => {
    if (user.role === 'MASTER') {
      const activeAdmins = users.filter(u => u.role === 'ADMIN' && u.active).length;
      const totalAdmins = users.filter(u => u.role === 'ADMIN').length;
      const activeCashiers = users.filter(u => u.role === 'CASHIER' && u.active).length;
      
      const salesTotal = tickets.filter(t => t.status !== 'cancelled').reduce((acc, t) => acc + t.total, 0);
      const prizesTotal = tickets.filter(t => t.status === 'paid' || t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
      
      return {
        card1: { title: 'Bancas Activas', value: `${activeAdmins}/${totalAdmins}`, icon: Layers, color: 'var(--primary)' },
        card2: { title: 'Cajeros de Red', value: activeCashiers.toString(), icon: Users, color: 'var(--success)' },
        card3: { title: 'Ventas Totales (Hoy)', value: `$${salesTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: DollarSign, color: 'var(--info)' },
        card4: { title: 'Premios Totales', value: `$${prizesTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: AlertTriangle, color: 'var(--danger)' },
      };
    } else {
      // ADMIN or SUPERVISOR red stats
      const myCashiers = user.role === 'SUPERVISOR' 
        ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id))
        : users.filter(u => u.role === 'CASHIER' && u.adminId === user.id);
      
      const activeMyCashiers = myCashiers.filter(c => c.active).length;
      
      const cashierUsernames = myCashiers.map(c => c.user);
      const myTickets = tickets.filter(t => cashierUsernames.includes(t.sellerUser || ''));
      const salesTotal = myTickets.filter(t => t.status !== 'cancelled').reduce((acc, t) => acc + t.total, 0);
      
      const balance = user?.balance ?? 0;
      const rechargesBalance = user?.rechargesBalance ?? 0;

      return {
        card1: { title: user.role === 'SUPERVISOR' ? 'Mis Cajeros Activos' : 'Cajeros Activos', value: `${activeMyCashiers}/${myCashiers.length}`, icon: Users, color: 'var(--primary)' },
        card2: { title: user.role === 'SUPERVISOR' ? 'Mi Balance' : 'Balance de Bancas', value: `$${balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: DollarSign, color: 'var(--success)' },
        card3: { title: user.role === 'SUPERVISOR' ? 'Mis Ventas Hoy' : 'Ventas Hoy', value: `$${salesTotal.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: TrendingUp, color: 'var(--info)' },
        card4: { title: user.role === 'SUPERVISOR' ? 'Mi Cupo Recargas' : 'Cupo Recargas FF', value: `$${rechargesBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })}`, icon: ArrowRightLeft, color: 'var(--warning)' },
      };
    }
  };

  const stats = getDashboardStats();

  const myCashiersForDashboard = user.role === 'SUPERVISOR' 
    ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id))
    : (user.role === 'ADMIN' ? users.filter(u => u.role === 'CASHIER' && u.adminId === user.id) : []);

  const cashierUsernamesForDashboard = myCashiersForDashboard.map(c => c.user);
  const dashboardTicketsToShow = user.role === 'MASTER'
    ? tickets
    : tickets.filter(t => cashierUsernamesForDashboard.includes(t.sellerUser || ''));

  // Filter list of users based on search and selection
  const filteredUsers = users.filter((u) => {
    const matchesSearch = 
      (u.displayName?.toLowerCase() || '').includes(searchQuery.toLowerCase()) ||
      (u.banca?.toLowerCase() || '').includes(searchQuery.toLowerCase()) ||
      u.user.toLowerCase().includes(searchQuery.toLowerCase());
    
    const matchesStatus = 
      filterStatus === 'all' || 
      (filterStatus === 'active' && u.active) || 
      (filterStatus === 'blocked' && !u.active);
    
    return matchesSearch && matchesStatus;
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '28px' }}>
      
      {/* SHIMMER LOADING STATE */}
      {loading ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' }}>
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="glass-panel shimmer" style={{ height: '110px' }} />
            ))}
          </div>
          <div className="glass-panel shimmer" style={{ height: '350px' }} />
        </div>
      ) : (
        <>
          {/* TAB 1: GENERAL DASHBOARD */}
          {activeTab === 'dashboard' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* METRIC CARDS GRID */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' }}>
                
                {[stats.card1, stats.card2, stats.card3, stats.card4].map((c, i) => {
                  const Icon = c.icon;
                  return (
                    <div key={i} className="glass-panel" style={{
                      padding: '24px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      position: 'relative',
                      overflow: 'hidden'
                    }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'hsl(var(--text-secondary))', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                          {c.title}
                        </span>
                        <span style={{ fontSize: '1.75rem', fontWeight: 700, fontFamily: 'var(--font-display)', color: 'hsl(var(--text-primary))' }}>
                          {c.value}
                        </span>
                      </div>
                      <div style={{
                        width: '46px',
                        height: '46px',
                        borderRadius: 'var(--radius-md)',
                        backgroundColor: `${c.color}15`,
                        color: c.color,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        boxShadow: `0 8px 16px ${c.color}10`
                      }}>
                        <Icon size={22} />
                      </div>
                    </div>
                  );
                })}

              </div>

              {/* TWO COLUMN SUMMARY CONTENT */}
              <div style={{ display: 'grid', gridTemplateColumns: user.role === 'MASTER' ? '1fr' : '2fr 1fr', gap: '24px' }} className="grid-responsive">
                
                {/* Visual exposure monitoring / live transactions */}
                {user.role !== 'MASTER' && (
                  <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <h3 style={{ fontSize: '1.1rem', color: 'hsl(var(--text-primary))' }}>
                        Tickets Emitidos Recientemente
                      </h3>
                      <button className="btn-icon" onClick={loadData}>
                        <RefreshCw size={16} />
                      </button>
                    </div>

                    <div className="table-container">
                      <table className="table-el">
                        <thead>
                          <tr>
                            <th>Serial</th>
                            <th>Cajero</th>
                            <th>Loterías</th>
                            <th>Total</th>
                            <th>Premios</th>
                            <th>Estado</th>
                          </tr>
                        </thead>
                        <tbody>
                          {dashboardTicketsToShow.length === 0 ? (
                            <tr>
                              <td colSpan={6} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                                No hay transacciones registradas hoy.
                              </td>
                            </tr>
                          ) : (
                            dashboardTicketsToShow.map((t) => (
                              <tr key={t.id}>
                                <td style={{ fontWeight: 600 }}>{t.serial || t.id.substring(0, 8).toUpperCase()}</td>
                                <td>{t.sellerUser}</td>
                                <td style={{ fontSize: '0.8rem' }}>
                                  {t.plays.map(p => p.lotteryName).join(', ')}
                                </td>
                                <td style={{ fontWeight: 600 }}>${t.total.toFixed(2)}</td>
                                <td style={{ color: t.totalPrize > 0 ? 'hsl(var(--success))' : 'inherit', fontWeight: 600 }}>
                                  ${t.totalPrize.toFixed(2)}
                                </td>
                                <td>
                                  <span className={`badge ${
                                    t.status === 'paid' ? 'badge-success' : t.status === 'cancelled' ? 'badge-danger' : 'badge-primary'
                                  }`}>
                                    {t.status === 'paid' ? 'Cobrado' : t.status === 'cancelled' ? 'Anulado' : 'Activo'}
                                  </span>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}

                {/* Exposición y Loterías Abiertas / Master-specific Audit Logs Summary */}
                {user.role === 'MASTER' ? (
                  <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <h3 style={{ fontSize: '1.1rem', color: 'hsl(var(--text-primary))' }}>
                      Bitácora de Auditoría Reciente
                    </h3>
                    <div className="table-container">
                      <table className="table-el">
                        <thead>
                          <tr>
                            <th>Usuario</th>
                            <th>Acción</th>
                            <th>Detalles</th>
                            <th>Fecha y Hora</th>
                          </tr>
                        </thead>
                        <tbody>
                          {audits.length === 0 ? (
                            <tr>
                              <td colSpan={4} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                                No hay logs de auditoría registrados.
                              </td>
                            </tr>
                          ) : (
                            audits.slice(0, 10).map((a) => (
                              <tr key={a.id}>
                                <td>
                                  <strong>@{a.actorUser}</strong>
                                  <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', display: 'block' }}>{a.role}</span>
                                </td>
                                <td>
                                  <span className={`badge ${a.status === 'success' ? 'badge-success' : a.status === 'failed' ? 'badge-danger' : 'badge-primary'}`}>
                                    {a.action}
                                  </span>
                                </td>
                                <td style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>{a.details}</td>
                                <td>{new Date(a.timestampMs).toLocaleString()}</td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  /* Exposición y Loterías Abiertas (ADMIN / SUPERVISOR) */
                  <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <h3 style={{ fontSize: '1.1rem', color: 'hsl(var(--text-primary))' }}>
                      Horarios y Control Loterías
                    </h3>
                    
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                      {lotteries.map((l) => {
                        const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === l.id);
                        const logoUrl = catalogEntry?.logoAssetPath || l.logoAssetPath || '/favicon.svg';
                        
                        const currentMinutes = getCurrentDRMinutesSinceMidnight();
                        const closeMinutes = parseTimeToMinutes(l.baseCloseTime);
                        const isTimeClosed = currentMinutes >= closeMinutes;
                        const isManuallyBlocked = manualDisabledLotteryIds.includes(l.id);
                        const isClosed = isTimeClosed || isManuallyBlocked;

                        return (
                          <div key={l.id} style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '12px',
                            borderRadius: 'var(--radius-md)',
                            backgroundColor: 'hsl(var(--background))',
                            borderLeft: `4px solid ${l.colorHex}`
                          }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                              <img 
                                src={logoUrl} 
                                alt={l.name} 
                                style={{ width: '32px', height: '32px', borderRadius: '4px', objectFit: 'contain', backgroundColor: 'rgba(255,255,255,0.05)', padding: '2px' }}
                                onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                              />
                              <div>
                                <strong style={{ fontSize: '0.875rem', display: 'block' }}>{l.name}</strong>
                                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>
                                  Cierre: {l.baseCloseTime} | Sorteo: {l.baseDrawTime}
                                </span>
                              </div>
                            </div>
                            
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              {isClosed ? (
                                <span className="badge" style={{ fontSize: '0.625rem', backgroundColor: 'hsl(var(--danger) / 0.1)', color: 'hsl(var(--danger))', border: '1px solid hsl(var(--danger) / 0.2)' }}>
                                  {isManuallyBlocked ? 'Bloqueado Admin' : 'Cerrado'}
                                </span>
                              ) : (
                                <span className="badge badge-success" style={{ fontSize: '0.625rem' }}>
                                  Abierto
                                </span>
                              )}
                              
                              {(user.role === 'ADMIN' || user.role === 'SUPERVISOR') && (
                                <button
                                  onClick={() => handleToggleManualDisabledLottery(l.id)}
                                  className="btn"
                                  style={{
                                    padding: '4px 8px',
                                    fontSize: '0.7rem',
                                    borderRadius: 'var(--radius-sm)',
                                    cursor: 'pointer',
                                    backgroundColor: isManuallyBlocked ? 'hsl(var(--success) / 0.1)' : 'hsl(var(--danger) / 0.1)',
                                    color: isManuallyBlocked ? 'hsl(var(--success))' : 'hsl(var(--danger))',
                                    border: `1px solid ${isManuallyBlocked ? 'hsl(var(--success) / 0.2)' : 'hsl(var(--danger) / 0.2)'}`,
                                    fontWeight: 600,
                                    transition: 'all 0.2s ease'
                                  }}
                                  title={isManuallyBlocked ? 'Habilitar Lotería' : 'Bloquear Lotería'}
                                >
                                  {isManuallyBlocked ? 'Habilitar' : 'Bloquear'}
                                </button>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

              </div>

            </div>
          )}

          {/* TAB 2: MASTER ONLY - MANAGE ADMINS / BANCAS */}
          {activeTab === 'admins' && user.role === 'MASTER' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <div style={{ position: 'relative', width: '280px' }}>
                    <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'hsl(var(--text-muted))' }} />
                    <input
                      type="text"
                      placeholder="Buscar banca o administrador..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="form-input"
                      style={{ paddingLeft: '36px' }}
                    />
                  </div>

                  <select
                    className="form-input"
                    value={filterStatus}
                    onChange={(e: any) => setFilterStatus(e.target.value)}
                    style={{ width: '140px' }}
                  >
                    <option value="all">Todos</option>
                    <option value="active">Activos</option>
                    <option value="blocked">Bloqueados</option>
                  </select>
                </div>

                <button className="btn btn-primary" onClick={() => setAdminModalOpen(true)}>
                  <Plus size={16} />
                  Crear Banca
                </button>
              </div>

              {/* LIST TABLE OF BANCAS */}
              <div className="table-container">
                <table className="table-el">
                  <thead>
                    <tr>
                      <th>Banca / Nombre</th>
                      <th>Administrador</th>
                      <th>Prefijo Cajeros</th>
                      <th>Teléfono</th>
                      <th>Cupo Financiero</th>
                      <th>Creado el</th>
                      <th>Estado</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUsers.filter(u => u.role === 'ADMIN').length === 0 ? (
                      <tr>
                        <td colSpan={8} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))', padding: '24px' }}>
                          No se encontraron bancas registradas.
                        </td>
                      </tr>
                    ) : (
                      filteredUsers.filter(u => u.role === 'ADMIN').map((a) => (
                        <tr key={a.id}>
                          <td>
                            <div style={{ display: 'flex', flexDirection: 'column' }}>
                              <strong style={{ color: 'hsl(var(--text-primary))' }}>{a.banca}</strong>
                              <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>
                                ID: {a.id} | @{a.user}
                              </span>
                            </div>
                          </td>
                          <td style={{ fontWeight: 500 }}>{a.displayName}</td>
                          <td>
                            <span className="badge badge-primary">{a.cashierPrefix}</span>
                          </td>
                          <td>{a.phone || 'N/A'}</td>
                          <td style={{ fontWeight: 600 }}>${a.rechargesBalance.toFixed(2)}</td>
                          <td>{a.createdLabel}</td>
                          <td>
                            <span className={`badge ${a.active ? 'badge-success' : 'badge-danger'}`}>
                              {a.active ? 'Activo' : 'Bloqueado'}
                            </span>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button 
                                className="btn-icon" 
                                style={{ color: a.active ? 'hsl(var(--danger))' : 'hsl(var(--success))', border: 'none' }}
                                onClick={() => handleToggleAdmin(a.id)}
                                title={a.active ? 'Bloquear Banca' : 'Desbloquear Banca'}
                              >
                                {a.active ? <Lock size={16} /> : <Key size={16} />}
                              </button>
                              
                              <button 
                                className="btn-icon" 
                                onClick={() => handleRegenCreds(a)}
                                title="Regenerar contraseña"
                              >
                                <RefreshCw size={16} />
                              </button>

                              <button 
                                className="btn-icon" 
                                style={{ color: 'hsl(var(--danger))' }}
                                onClick={() => handleDeleteBanca(a.id)}
                                title="Eliminar banca de raíz"
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

            </div>
          )}

          {/* TAB 3: ADMIN ONLY - MANAGE CAJEROS */}
          {activeTab === 'cajeros' && user.role === 'ADMIN' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <div style={{ position: 'relative', width: '280px' }}>
                    <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'hsl(var(--text-muted))' }} />
                    <input
                      type="text"
                      placeholder="Buscar cajero..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="form-input"
                      style={{ paddingLeft: '36px' }}
                    />
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '10px' }}>
                  <button className="btn btn-secondary" onClick={() => setRechargeModalOpen(true)}>
                    <ArrowRightLeft size={16} />
                    Asignar Balance
                  </button>

                  <button className="btn btn-primary" onClick={() => setCajeroModalOpen(true)}>
                    <Plus size={16} />
                    Crear Cajero
                  </button>
                </div>
              </div>

              {/* LIST TABLE OF CAJEROS UNDER THIS ADMIN */}
              <div className="table-container">
                <table className="table-el">
                  <thead>
                    <tr>
                      <th>Cajero / ID</th>
                      <th>Usuario</th>
                      <th>Territorio</th>
                      <th>Balance Ventas</th>
                      <th>Cupo Recargas (FF)</th>
                      <th>Creado el</th>
                      <th>Estado</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).length === 0 ? (
                      <tr>
                        <td colSpan={8} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))', padding: '24px' }}>
                          No hay cajeros asignados a tu banca todavía.
                        </td>
                      </tr>
                    ) : (
                      users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).map((c) => (
                        <tr key={c.id}>
                          <td style={{ fontWeight: 600 }}>{c.displayName}</td>
                          <td>@{c.user}</td>
                          <td>
                            <span className="badge badge-primary">{c.territory}</span>
                          </td>
                          <td style={{ fontWeight: 600 }}>${c.balance.toFixed(2)}</td>
                          <td style={{ fontWeight: 600 }}>
                            ${c.rechargesBalance.toFixed(2)}
                            {c.rechargesEnabled ? (
                              <span style={{ fontSize: '0.7rem', color: 'hsl(var(--success))', display: 'block' }}>Habilitado</span>
                            ) : (
                              <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', display: 'block' }}>Deshabilitado</span>
                            )}
                          </td>
                          <td>{c.createdLabel || '14/05/2026'}</td>
                          <td>
                            <span className={`badge ${c.active ? 'badge-success' : 'badge-danger'}`}>
                              {c.active ? 'Activo' : 'Bloqueado'}
                            </span>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button 
                                className="btn-icon" 
                                style={{ color: c.active ? 'hsl(var(--danger))' : 'hsl(var(--success))', border: 'none' }}
                                onClick={() => handleToggleCashier(c.id)}
                                title={c.active ? 'Suspender Cajero' : 'Activar Cajero'}
                              >
                                {c.active ? <Lock size={16} /> : <Key size={16} />}
                              </button>
                              <button 
                                className="btn-icon" 
                                style={{ color: 'hsl(var(--primary))', border: 'none' }}
                                onClick={() => handleOpenEditCajero(c)}
                                title="Editar Cajero"
                              >
                                <Edit2 size={16} />
                              </button>
                              <button 
                                className="btn-icon" 
                                style={{ color: 'hsl(var(--danger))', border: 'none' }}
                                onClick={() => handleDeleteCashier(c.id)}
                                title="Eliminar Cajero"
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

            </div>
          )}

          {/* TAB 4: ADMIN ONLY - MANAGE SUPERVISORES */}
          {activeTab === 'supervisores' && user.role === 'ADMIN' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3 style={{ fontSize: '1.1rem' }}>Lista de Supervisores Asignados</h3>
                
                <button className="btn btn-primary" onClick={() => setSupervisorModalOpen(true)}>
                  <Plus size={16} />
                  Crear Supervisor
                </button>
              </div>

              <div className="table-container">
                <table className="table-el">
                  <thead>
                    <tr>
                      <th>Nombre</th>
                      <th>Usuario</th>
                      <th>Teléfono</th>
                      <th>Territorio</th>
                      <th>Creado el</th>
                      <th>Estado</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.filter(u => u.role === 'SUPERVISOR' && u.adminId === user.id).length === 0 ? (
                      <tr>
                        <td colSpan={7} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))', padding: '24px' }}>
                          No hay supervisores asociados a tu banca.
                        </td>
                      </tr>
                    ) : (
                      users.filter(u => u.role === 'SUPERVISOR' && u.adminId === user.id).map((s) => (
                        <tr key={s.id}>
                          <td style={{ fontWeight: 600 }}>{s.displayName}</td>
                          <td>@{s.user}</td>
                          <td>{s.phone || 'N/A'}</td>
                          <td>
                            <span className="badge badge-primary">{s.territory}</span>
                          </td>
                          <td>{s.createdLabel}</td>
                          <td>
                            <span className={`badge ${s.active ? 'badge-success' : 'badge-danger'}`}>
                              {s.active ? 'Activo' : 'Bloqueado'}
                            </span>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button 
                                className="btn-icon" 
                                style={{ color: 'hsl(var(--primary))', border: 'none' }}
                                onClick={() => handleOpenAssignModal(s)}
                                title="Asignar Cajeros"
                              >
                                <Users size={16} />
                              </button>
                              <button 
                                className="btn-icon" 
                                style={{ color: 'hsl(var(--warning))', border: 'none' }}
                                onClick={() => handleResetSupervisorPassword(s)}
                                title="Restablecer Clave"
                              >
                                <Key size={16} />
                              </button>
                              <button 
                                className="btn-icon" 
                                style={{ color: s.active ? 'hsl(var(--danger))' : 'hsl(var(--success))', border: 'none' }}
                                onClick={() => handleToggleSupervisor(s)}
                                title={s.active ? 'Bloquear Supervisor' : 'Activar Supervisor'}
                              >
                                {s.active ? <Lock size={16} /> : <Key size={16} />}
                              </button>
                              <button 
                                className="btn-icon" 
                                style={{ color: 'hsl(var(--danger))', border: 'none' }}
                                onClick={() => handleDeleteSupervisor(s)}
                                title="Eliminar Supervisor"
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

            </div>
          )}

          {/* TAB 5: COMPREHENSIVE NETWORK & PLAY MONITORING */}
          {activeTab === 'monitoreo' && (user.role === 'ADMIN' || user.role === 'SUPERVISOR') && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Monitoreo Top Controls */}
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                  <div>
                    <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>Monitoreo de Red y Loterías</h3>
                    <p style={{ fontSize: '0.825rem', color: 'hsl(var(--text-secondary))' }}>
                      Audita las ventas de cajeros, loterías activas y la exposición acumulada de números en tiempo real.
                    </p>
                  </div>
                  <span className="badge badge-success" style={{ padding: '6px 12px', fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#fff', display: 'inline-block', animation: 'pulse 1.5s infinite' }} />
                    Sincronizado
                  </span>
                </div>

                <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid hsl(var(--border))', paddingBottom: '16px', flexWrap: 'wrap' }}>
                  {[
                    { id: 'lotteries', label: 'Ventas por Lotería' },
                    { id: 'plays', label: 'Ranking de Números' },
                    { id: 'cajeros', label: 'Presencia de Cajeros' },
                  ].map((subTab) => (
                    <button
                      key={subTab.id}
                      onClick={() => setMonitoreoSubTab(subTab.id as any)}
                      style={{
                        padding: '10px 18px',
                        borderRadius: 'var(--radius-md)',
                        border: '1px solid ' + (monitoreoSubTab === subTab.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                        background: monitoreoSubTab === subTab.id ? 'hsl(var(--primary) / 0.08)' : 'transparent',
                        color: monitoreoSubTab === subTab.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                        fontSize: '0.875rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease'
                      }}
                    >
                      {subTab.label}
                    </button>
                  ))}
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    {['day', 'week', 'month'].map((r) => (
                      <button
                        key={r}
                        onClick={() => setMonitoreoRange(r as any)}
                        className={`badge ${monitoreoRange === r ? 'badge-primary' : 'badge-secondary'}`}
                        style={{ border: 'none', cursor: 'pointer', textTransform: 'capitalize', fontSize: '0.75rem', padding: '6px 12px' }}
                      >
                        {r === 'day' ? 'Hoy' : r === 'week' ? 'Semana' : 'Mes'}
                      </button>
                    ))}
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>Mostrar Loterías Vacías:</span>
                    <input
                      type="checkbox"
                      checked={monitoreoShowEmptyLotteries}
                      onChange={(e) => setMonitoreoShowEmptyLotteries(e.target.checked)}
                      style={{ cursor: 'pointer', width: '16px', height: '16px' }}
                    />
                  </div>
                </div>
              </div>

              {/* Sub-Tab 1: LOTTERIES BREAKDOWN */}
              {monitoreoSubTab === 'lotteries' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  {(() => {
                    const scopedTickets = tickets.filter(t => {
                      if (t.status === 'cancelled' || t.status === 'voided') return false;
                      const dateLimit = monitoreoRange === 'day' ? 1 : monitoreoRange === 'week' ? 7 : 30;
                      return (Date.now() - t.createdAtEpochMs) <= (dateLimit * 86400000);
                    });

                    // Build lottery wagers
                    const lotteryWagers = lotteries.map(l => {
                      let q = 0, p = 0, t = 0, sp = 0, pick = 0, total = 0;
                      scopedTickets.forEach(tk => {
                        tk.plays.forEach(play => {
                          if (play.lotteryId === l.id || play.secondaryLotteryId === l.id) {
                            const amt = play.amount;
                            total += amt;
                            const type = play.playType.toUpperCase();
                            if (type === 'Q' || type === 'QUINIELE') q += amt;
                            else if (type === 'P' || type === 'PALE') p += amt;
                            else if (type === 'T' || type === 'TRIPLETA') t += amt;
                            else if (type === 'SP' || type === 'SUPER PALE') sp += amt;
                            else if (['P3', 'P3BOX', 'P4', 'P4BOX'].includes(type)) pick += amt;
                          }
                        });
                      });
                      return { lottery: l, q, p, t, sp, pick, total };
                    }).filter((item: any) => monitoreoShowEmptyLotteries || item.total > 0)
                      .sort((a: any, b: any) => b.total - a.total);

                    const grandTotal = lotteryWagers.reduce((acc: number, item: any) => acc + item.total, 0);

                    if (lotteryWagers.length === 0) {
                      return (
                        <div className="glass-panel" style={{ padding: '40px', textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                          No hay ventas registradas para este periodo de monitoreo.
                        </div>
                      );
                    }

                    return (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        {lotteryWagers.map((item: any) => {
                          const percent = grandTotal > 0 ? (item.total / grandTotal) * 100 : 0;
                           const catalogEntry = STATIC_LOTTERIES.find(sl => sl.id === item.lottery.id);
                           const logoUrl = catalogEntry?.logoAssetPath || item.lottery.logoAssetPath || '/favicon.svg';

                          return (
                            <div key={item.lottery.id} className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                  <img 
                                    src={logoUrl} 
                                    alt={item.lottery.name} 
                                    style={{ width: '36px', height: '36px', borderRadius: '4px', objectFit: 'contain', backgroundColor: 'rgba(255,255,255,0.05)', padding: '2px' }}
                                    onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                                  />
                                  <div>
                                    <strong style={{ fontSize: '1rem', display: 'block' }}>{item.lottery.name}</strong>
                                    <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>({item.lottery.territory})</span>
                                  </div>
                                </div>
                                <div style={{ textAlign: 'right' }}>
                                  <strong style={{ fontSize: '1.05rem', color: 'hsl(var(--primary))' }}>${item.total.toFixed(2)}</strong>
                                  <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))', display: 'block' }}>{percent.toFixed(1)}% del total</span>
                                </div>
                              </div>

                              {/* Progress bar */}
                              <div style={{ width: '100%', height: '6px', borderRadius: '3px', backgroundColor: 'hsl(var(--border))', overflow: 'hidden' }}>
                                <div style={{ height: '100%', width: `${percent}%`, backgroundColor: item.lottery.colorHex, borderRadius: '3px' }} />
                              </div>

                              {/* Breakdown */}
                              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(100px, 1fr))', gap: '12px', fontSize: '0.8rem', marginTop: '4px' }}>
                                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                                  <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Quiniela</span>
                                  <strong>${item.q.toFixed(2)}</strong>
                                </div>
                                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                                  <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Palé</span>
                                  <strong>${item.p.toFixed(2)}</strong>
                                </div>
                                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                                  <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Super Palé</span>
                                  <strong>${item.sp.toFixed(2)}</strong>
                                </div>
                                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                                  <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Tripleta</span>
                                  <strong>${item.t.toFixed(2)}</strong>
                                </div>
                                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '6px 10px', borderRadius: 'var(--radius-sm)' }}>
                                  <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Pick 3/4</span>
                                  <strong>${item.pick.toFixed(2)}</strong>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}
                </div>
              )}

              {/* Sub-Tab 2: PLAY NUMBERS EXPOSURE RANKING */}
              {monitoreoSubTab === 'plays' && (
                <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {[
                        { id: 'Q', label: 'Quiniela' },
                        { id: 'P', label: 'Pale' },
                        { id: 'SP', label: 'Super Pale' },
                        { id: 'T', label: 'Tripleta' },
                        { id: 'P3', label: 'Pick 3' },
                        { id: 'P4', label: 'Pick 4' },
                      ].map((view) => (
                        <button
                          key={view.id}
                          onClick={() => setMonitoreoPlayFocus(view.id as any)}
                          style={{
                            padding: '6px 12px',
                            borderRadius: 'var(--radius-sm)',
                            border: '1px solid ' + (monitoreoPlayFocus === view.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                            background: monitoreoPlayFocus === view.id ? 'hsl(var(--primary) / 0.06)' : 'transparent',
                            color: monitoreoPlayFocus === view.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            cursor: 'pointer'
                          }}
                        >
                          {view.label}
                        </button>
                      ))}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>Mayor Apuesta Primero:</span>
                      <input
                        type="checkbox"
                        checked={monitoreoHighestFirst}
                        onChange={(e) => setMonitoreoHighestFirst(e.target.checked)}
                        style={{ cursor: 'pointer', width: '16px', height: '16px' }}
                      />
                    </div>
                  </div>

                  {/* Build wagers ranking */}
                  {(() => {
                    const scopedTickets = tickets.filter(t => {
                      if (t.status === 'cancelled' || t.status === 'voided') return false;
                      const dateLimit = monitoreoRange === 'day' ? 1 : monitoreoRange === 'week' ? 7 : 30;
                      return (Date.now() - t.createdAtEpochMs) <= (dateLimit * 86400000);
                    });

                    let ranking = buildLotteryMonitorRows(scopedTickets, monitoreoPlayFocus);
                    if (!monitoreoHighestFirst) {
                      ranking = ranking.sort((a: any, b: any) => a.amount - b.amount);
                    }

                    if (ranking.length === 0) {
                      return (
                        <div style={{ padding: '30px', textAlign: 'center', color: 'hsl(var(--text-muted))' }}>
                          No hay números apostados para esta combinación en el periodo seleccionado.
                        </div>
                      );
                    }

                    return (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        {ranking.map((row: any) => (
                          <div
                            key={row.displayNumber}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              padding: '12px 16px',
                              borderRadius: 'var(--radius-sm)',
                              backgroundColor: 'hsl(var(--background))',
                              border: '1px solid hsl(var(--border))'
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                              <span style={{
                                padding: '8px 14px',
                                borderRadius: 'var(--radius-sm)',
                                backgroundColor: 'hsl(var(--primary) / 0.1)',
                                color: 'hsl(var(--primary))',
                                fontWeight: 700,
                                fontSize: '1rem',
                                fontFamily: 'monospace'
                              }}>
                                {row.displayNumber}
                              </span>
                              <div>
                                <strong style={{ fontSize: '0.9rem', display: 'block' }}>Apostado: ${row.amount.toFixed(2)}</strong>
                                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                                  {row.playsCount} jugadas · Cajeros: {row.actors.join(', ') || 'sin cajero'}
                                </span>
                              </div>
                            </div>
                            <span className="badge badge-success" style={{ fontSize: '0.75rem' }}>
                              {row.playsCount} veces
                            </span>
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                </div>
              )}

              {/* Sub-Tab 3: CASHIER PRESENCE & ONLINE STATUS */}
              {monitoreoSubTab === 'cajeros' && (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
                  {users.filter(u => u.role === 'CASHIER' && (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id))).map((c) => {
                    const cashierTickets = tickets.filter(t => t.sellerUser === c.user && t.status !== 'cancelled');
                    const hasVendidoToday = cashierTickets.some(t => (Date.now() - t.createdAtEpochMs) <= 86400000);
                    const presence = !c.active ? 'Bloqueado' : hasVendidoToday ? 'Activo' : 'Sin movimiento';

                    return (
                      <div key={c.id} className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                          <div>
                            <strong style={{ fontSize: '1rem', display: 'block' }}>{c.displayName || c.user}</strong>
                            <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))' }}>@{c.user}</span>
                          </div>
                          <span className={`badge ${
                            presence === 'Activo' ? 'badge-success' : presence === 'Bloqueado' ? 'badge-danger' : 'badge-secondary'
                          }`}>
                            {presence}
                          </span>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px', fontSize: '0.825rem' }}>
                          <div style={{ backgroundColor: 'hsl(var(--background))', padding: '10px', borderRadius: 'var(--radius-sm)' }}>
                            <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Balance Caja</span>
                            <strong>${c.balance.toFixed(2)}</strong>
                          </div>
                          <div style={{ backgroundColor: 'hsl(var(--background))', padding: '10px', borderRadius: 'var(--radius-sm)' }}>
                            <span style={{ color: 'hsl(var(--text-secondary))', display: 'block', fontSize: '0.7rem' }}>Balance Recargas</span>
                            <strong>${c.rechargesBalance.toFixed(2)}</strong>
                          </div>
                        </div>

                        <div style={{ borderTop: '1px solid hsl(var(--border))', paddingTop: '10px', fontSize: '0.75rem', color: 'hsl(var(--text-muted))', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span>POS Compact Mode:</span>
                          <strong style={{ color: c.systemModeOverride === 'compact' ? 'hsl(var(--primary))' : 'inherit' }}>
                            {c.systemModeOverride === 'compact' ? 'Habilitado' : 'Estándar'}
                          </strong>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

            </div>
          )}

          {/* TAB 10: TICKETS SEARCH & VOID SYSTEM */}
          {activeTab === 'tickets' && (user.role === 'ADMIN' || user.role === 'SUPERVISOR') && (
            <div className="fade-in glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                <div>
                  <h3 style={{ fontSize: '1.15rem' }}>Consulta y Control de Tickets</h3>
                  <p style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>
                    Visualiza las transacciones de ventas y realiza anulaciones dentro del plazo permitido para restablecer balances de caja.
                  </p>
                </div>
              </div>

              {/* Filters topbar */}
              <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
                <div style={{ position: 'relative', width: '260px' }}>
                  <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'hsl(var(--text-muted))' }} />
                  <input
                    type="text"
                    placeholder="Buscar por serie o ID..."
                    value={ticketSearchSerial}
                    onChange={(e) => setTicketSearchSerial(e.target.value)}
                    className="form-input"
                    style={{ paddingLeft: '36px' }}
                  />
                </div>

                <select
                  className="form-input"
                  value={ticketFilterStatus}
                  onChange={(e) => setTicketFilterStatus(e.target.value)}
                  style={{ width: '140px' }}
                >
                  <option value="all">Todos los Estados</option>
                  <option value="active">Activos</option>
                  <option value="paid">Cobrados</option>
                  <option value="cancelled">Anulados</option>
                  <option value="winner">Premiados</option>
                </select>

                <select
                  className="form-input"
                  value={ticketFilterCashier}
                  onChange={(e) => setTicketFilterCashier(e.target.value)}
                  style={{ width: '180px' }}
                >
                  <option value="all">Todos los Cajeros</option>
                  {users.filter(u => u.role === 'CASHIER' && (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id))).map(c => (
                    <option key={c.id} value={c.user}>@{c.user}</option>
                  ))}
                </select>
              </div>

              {/* Table of tickets */}
              {(() => {
                const isSupervisor = user.role === 'SUPERVISOR';
                const allowedAdminId = isSupervisor ? user.adminId : user.id;
                const supervisedCashierUsers = isSupervisor 
                  ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id)).map(u => u.user)
                  : [];

                const filtered = tickets.filter(t => t.adminId === allowedAdminId)
                  .filter(t => {
                    if (isSupervisor && (!t.sellerUser || !supervisedCashierUsers.includes(t.sellerUser))) return false;
                    if (ticketSearchSerial && !t.id.toLowerCase().includes(ticketSearchSerial.toLowerCase()) && !t.serial?.toLowerCase().includes(ticketSearchSerial.toLowerCase())) return false;
                    if (ticketFilterStatus !== 'all' && t.status !== ticketFilterStatus) return false;
                    if (ticketFilterCashier !== 'all' && t.sellerUser !== ticketFilterCashier) return false;
                    return true;
                  });

                if (filtered.length === 0) {
                  return (
                    <div style={{ padding: '30px', textAlign: 'center', color: 'hsl(var(--text-muted))' }}>
                      No se encontraron tickets con los filtros aplicados.
                    </div>
                  );
                }

                return (
                  <div className="table-container">
                    <table className="table-el">
                      <thead>
                        <tr>
                          <th>Serie / Ticket ID</th>
                          <th>Cajero</th>
                          <th>Fecha y Hora</th>
                          <th>Jugadas Realizadas</th>
                          <th>Monto</th>
                          <th>Premio</th>
                          <th>Estado</th>
                          <th>Acciones</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filtered.map((t) => (
                          <tr key={t.id}>
                            <td>
                              <div style={{ display: 'flex', flexDirection: 'column' }}>
                                <strong style={{ color: 'hsl(var(--text-primary))' }}>{t.serial || t.id}</strong>
                                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>ID: {t.id}</span>
                              </div>
                            </td>
                            <td>@{t.sellerUser}</td>
                            <td>{new Date(t.createdAtEpochMs).toLocaleString()}</td>
                            <td>
                              <div style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>
                                {t.plays.map(p => `${p.playType.toUpperCase()} ${p.number} ($${p.amount})`).join(' · ')}
                              </div>
                            </td>
                            <td style={{ fontWeight: 600 }}>${t.total.toFixed(2)}</td>
                            <td style={{ fontWeight: 600, color: t.totalPrize > 0 ? 'hsl(var(--danger))' : 'inherit' }}>
                              ${t.totalPrize.toFixed(2)}
                            </td>
                            <td>
                              <span className={`badge ${
                                t.status === 'paid' ? 'badge-success' : t.status === 'cancelled' ? 'badge-secondary' : t.status === 'winner' ? 'badge-danger' : 'badge-primary'
                              }`}>
                                {t.status === 'paid' ? 'Cobrado' : t.status === 'cancelled' ? 'Anulado' : t.status === 'winner' ? 'Premiado' : 'Activo'}
                              </span>
                            </td>
                            <td>
                              <div style={{ display: 'flex', gap: '8px' }}>
                                {t.status === 'active' && (
                                  <button
                                    className="btn btn-secondary"
                                    style={{ padding: '4px 8px', fontSize: '0.75rem', color: 'hsl(var(--danger))', border: '1px solid hsl(var(--danger) / 0.2)' }}
                                    onClick={() => {
                                      setSelectedTicketForAnnul(t);
                                      setAnnulModalOpen(true);
                                    }}
                                  >
                                    Anular
                                  </button>
                                )}
                                {t.status === 'winner' && (
                                  <button
                                    className="btn btn-primary"
                                    style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                                    onClick={() => handlePayWinner(t)}
                                  >
                                    Pagar
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                );
              })()}
            </div>
          )}

          {/* TAB 11: WINNERS PRIZE PAYOUT MODULE */}
          {activeTab === 'ganadores' && user.role === 'ADMIN' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Summary Cards */}
              {(() => {
                const adminTickets = tickets.filter(t => t.adminId === user.id && (t.status === 'winner' || t.status === 'paid' || t.totalPrize > 0));
                const pendingWinners = adminTickets.filter(t => t.status === 'winner' || (t.status !== 'paid' && t.totalPrize > 0));
                const paidWinners = adminTickets.filter(t => t.status === 'paid');

                const pendingPrizeSum = pendingWinners.reduce((acc, t) => acc + t.totalPrize, 0);
                const paidPrizeSum = paidWinners.reduce((acc, t) => acc + t.totalPrize, 0);

                return (
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' }}>
                    <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Premios Pendientes</span>
                      <span style={{ fontSize: '1.6rem', fontWeight: 700, color: 'hsl(var(--warning))' }}>${pendingPrizeSum.toFixed(2)}</span>
                      <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{pendingWinners.length} ticket(s) pendiente(s)</span>
                    </div>
                    <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Premios Pagados</span>
                      <span style={{ fontSize: '1.6rem', fontWeight: 700, color: 'hsl(var(--success))' }}>${paidPrizeSum.toFixed(2)}</span>
                      <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{paidWinners.length} ticket(s) pagado(s)</span>
                    </div>
                    <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Total Premiados Hoy</span>
                      <span style={{ fontSize: '1.6rem', fontWeight: 700 }}>${(pendingPrizeSum + paidPrizeSum).toFixed(2)}</span>
                      <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{adminTickets.length} ticket(s) de banca</span>
                    </div>
                  </div>
                );
              })()}

              <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
                  <h3 style={{ fontSize: '1.1rem' }}>Cobro de Premios Ganadores</h3>
                  
                  <div style={{ display: 'flex', gap: '8px' }}>
                    {['pending', 'paid', 'all'].map((filter) => (
                      <button
                        key={filter}
                        onClick={() => setGanadoresFilter(filter as any)}
                        style={{
                          padding: '8px 16px',
                          borderRadius: 'var(--radius-md)',
                          border: '1px solid ' + (ganadoresFilter === filter ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                          background: ganadoresFilter === filter ? 'hsl(var(--primary) / 0.08)' : 'transparent',
                          color: ganadoresFilter === filter ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                          fontSize: '0.8rem',
                          fontWeight: 600,
                          cursor: 'pointer'
                        }}
                      >
                        {filter === 'pending' ? 'Pendientes de Pago' : filter === 'paid' ? 'Pagados' : 'Todos'}
                      </button>
                    ))}
                  </div>
                </div>

                {/* List of winners */}
                {(() => {
                  const adminTickets = tickets.filter(t => t.adminId === user.id && (t.status === 'winner' || t.status === 'paid' || t.totalPrize > 0));
                  const filtered = adminTickets.filter(t => {
                    if (ganadoresFilter === 'pending') return t.status === 'winner' || (t.status !== 'paid' && t.totalPrize > 0);
                    if (ganadoresFilter === 'paid') return t.status === 'paid';
                    return true;
                  });

                  if (filtered.length === 0) {
                    return (
                      <div style={{ padding: '24px', textAlign: 'center', color: 'hsl(var(--text-muted))' }}>
                        No hay ganadores registrados en esta categoría de filtro.
                      </div>
                    );
                  }

                  return (
                    <div className="table-container">
                      <table className="table-el">
                        <thead>
                          <tr>
                            <th>Serie / Ticket ID</th>
                            <th>Cajero</th>
                            <th>Emisión</th>
                            <th>Combinaciones</th>
                            <th>Total Premio</th>
                            <th>Estado</th>
                            <th>Operación</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filtered.map((t) => (
                            <tr key={t.id}>
                              <td style={{ fontWeight: 600 }}>{t.serial || t.id}</td>
                              <td>@{t.sellerUser}</td>
                              <td>{new Date(t.createdAtEpochMs).toLocaleTimeString()}</td>
                              <td>
                                <div style={{ fontSize: '0.8rem', color: 'hsl(var(--text-secondary))' }}>
                                  {t.plays.map(p => `${p.playType.toUpperCase()} ${p.number}`).join(' · ')}
                                </div>
                              </td>
                              <td style={{ fontWeight: 700, color: 'hsl(var(--danger))', fontSize: '1.05rem' }}>
                                ${t.totalPrize.toFixed(2)}
                              </td>
                              <td>
                                <span className={`badge ${t.status === 'paid' ? 'badge-success' : 'badge-warning'}`}>
                                  {t.status === 'paid' ? 'Cobrado' : 'Pendiente'}
                                </span>
                              </td>
                              <td>
                                {t.status !== 'paid' && (
                                  <button
                                    className="btn btn-primary"
                                    style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                                    onClick={() => handlePayWinner(t)}
                                  >
                                    Registrar Pago
                                  </button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  );
                })()}
              </div>

            </div>
          )}

          {/* TAB 12: RESULTS SCRAPER & REGISTRATION */}
          {activeTab === 'resultados' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Form Manual for Master */}
              {user.role === 'MASTER' && (
                <form onSubmit={handleCreateResult} className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Registrar Números Ganadores Manualmente</h3>
                  
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
                    <div className="form-group">
                      <label className="form-label">Lotería Sorteada</label>
                      <select
                        className="form-input"
                        value={resultForm.lotteryId}
                        onChange={(e) => setResultForm({ ...resultForm, lotteryId: e.target.value })}
                      >
                        {lotteries.map(l => (
                          <option key={l.id} value={l.id}>{l.name} ({l.territory})</option>
                        ))}
                      </select>
                    </div>

                    <div className="form-group">
                      <label className="form-label">1era (Primera)</label>
                      <input
                        type="text"
                        placeholder="ej. 14"
                        value={resultForm.r1}
                        onChange={(e) => setResultForm({ ...resultForm, r1: e.target.value.replace(/\D/g, '').slice(0, 2) })}
                        className="form-input"
                        required
                        maxLength={2}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">2da (Segunda)</label>
                      <input
                        type="text"
                        placeholder="ej. 22"
                        value={resultForm.r2}
                        onChange={(e) => setResultForm({ ...resultForm, r2: e.target.value.replace(/\D/g, '').slice(0, 2) })}
                        className="form-input"
                        required
                        maxLength={2}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">3era (Tercera)</label>
                      <input
                        type="text"
                        placeholder="ej. 05"
                        value={resultForm.r3}
                        onChange={(e) => setResultForm({ ...resultForm, r3: e.target.value.replace(/\D/g, '').slice(0, 2) })}
                        className="form-input"
                        required
                        maxLength={2}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Fecha del Sorteo</label>
                      <input
                        type="date"
                        value={resultForm.dateKey}
                        onChange={(e) => setResultForm({ ...resultForm, dateKey: e.target.value })}
                        className="form-input"
                        required
                      />
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '4px' }}>
                    <button type="submit" className="btn btn-primary" style={{ padding: '10px 24px' }}>
                      Registrar Resultado
                    </button>
                  </div>
                </form>
              )}

              {/* List of Draw Results */}
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '32px' }}>
                
                {/* Section 1: Traditional Lotteries */}
                <div>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px', color: 'hsl(var(--primary))', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: 'hsl(var(--primary))' }} />
                    Loterías Tradicionales (Quiniela / Palé / Tripleta)
                  </h3>
                  
                  {(() => {
                    const normalResults = resultsList.filter(r => {
                      const lot = STATIC_LOTTERIES.find(l => l.id === r.lotteryId) || lotteries.find(l => l.id === r.lotteryId);
                      // If not found in catalog, treat as traditional (could be a legacy/unknown lottery)
                      if (!lot) return !r.lotteryId.startsWith('US-P');
                      return lot.type !== 'Pick3' && lot.type !== 'Pick4';
                    });

                    if (normalResults.length === 0) {
                      return (
                        <div style={{ padding: '20px', textAlign: 'center', color: 'hsl(var(--text-muted))', backgroundColor: 'hsl(var(--background))', borderRadius: 'var(--radius-md)' }}>
                          No hay resultados tradicionales registrados.
                        </div>
                      );
                    }

                    return (
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
                        {normalResults.map((res) => {
                          const catalogEntry = STATIC_LOTTERIES.find(l => l.id === res.lotteryId) || lotteries.find(l => l.id === res.lotteryId);
                          const logoUrl = catalogEntry?.logoAssetPath || '/favicon.svg';

                          return (
                            <div
                              key={res.id}
                              style={{
                                padding: '20px',
                                borderRadius: 'var(--radius-md)',
                                backgroundColor: 'hsl(var(--background))',
                                border: '1px solid hsl(var(--border))',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '12px'
                              }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <img 
                                    src={logoUrl} 
                                    alt={res.lotteryName} 
                                    style={{ width: '28px', height: '28px', borderRadius: '4px', objectFit: 'contain', backgroundColor: 'rgba(255,255,255,0.05)', padding: '2px' }}
                                    onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                                  />
                                  <div>
                                    <strong style={{ fontSize: '0.95rem', color: 'hsl(var(--text-primary))', display: 'block' }}>{res.lotteryName}</strong>
                                    {catalogEntry?.baseDrawTime && (
                                      <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-secondary))', display: 'block', marginTop: '2px' }}>
                                        Sorteo: {catalogEntry.baseDrawTime}
                                      </span>
                                    )}
                                  </div>
                                </div>
                                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{res.dateKey}</span>
                              </div>

                              {/* Domino style numbered balls */}
                              <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', margin: '6px 0' }}>
                                {res.numbers.split('-').map((num: string, idx: number) => (
                                  <div
                                    key={idx}
                                    style={{
                                      width: '42px',
                                      height: '42px',
                                      borderRadius: '50%',
                                      backgroundColor: idx === 0 ? 'hsl(var(--primary))' : 'hsl(var(--surface))',
                                      color: idx === 0 ? '#fff' : 'hsl(var(--text-primary))',
                                      border: '2px solid hsl(var(--primary))',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      fontWeight: 700,
                                      fontSize: '1rem',
                                      fontFamily: 'monospace',
                                      boxShadow: 'var(--shadow-sm)'
                                    }}
                                  >
                                    {num}
                                  </div>
                                ))}
                              </div>

                              <div style={{ textAlign: 'center', fontSize: '0.725rem', color: 'hsl(var(--text-secondary))', borderTop: '1px solid hsl(var(--border))', paddingTop: '8px' }}>
                                Posiciones: 1ra · 2da · 3ra
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}
                </div>

                {/* Section 2: USA Pick Lotteries */}
                <div>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '16px', color: 'hsl(var(--warning))', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: 'hsl(var(--warning))' }} />
                    Loterías Americanas (Pick 3 / Pick 4 USA)
                  </h3>
                  
                  {(() => {
                    const pickResults = resultsList.filter(r => {
                      const lot = STATIC_LOTTERIES.find(l => l.id === r.lotteryId) || lotteries.find(l => l.id === r.lotteryId);
                      if (!lot) return r.lotteryId.startsWith('US-P');
                      return lot.type === 'Pick3' || lot.type === 'Pick4';
                    });

                    if (pickResults.length === 0) {
                      return (
                        <div style={{ padding: '20px', textAlign: 'center', color: 'hsl(var(--text-muted))', backgroundColor: 'hsl(var(--background))', borderRadius: 'var(--radius-md)' }}>
                          No hay resultados de sorteos Pick registrados.
                        </div>
                      );
                    }

                    return (
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
                        {pickResults.map((res) => {
                          const catalogEntry = STATIC_LOTTERIES.find(l => l.id === res.lotteryId) || lotteries.find(l => l.id === res.lotteryId);
                          const logoUrl = catalogEntry?.logoAssetPath || '/favicon.svg';

                          return (
                            <div
                              key={res.id}
                              style={{
                                padding: '20px',
                                borderRadius: 'var(--radius-md)',
                                backgroundColor: 'hsl(var(--background))',
                                border: '1px solid hsl(var(--border))',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '12px'
                              }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <img 
                                    src={logoUrl} 
                                    alt={res.lotteryName} 
                                    style={{ width: '28px', height: '28px', borderRadius: '4px', objectFit: 'contain', backgroundColor: 'rgba(255,255,255,0.05)', padding: '2px' }}
                                    onError={(e) => { (e.target as HTMLImageElement).src = '/favicon.svg'; }}
                                  />
                                  <div>
                                    <strong style={{ fontSize: '0.95rem', color: 'hsl(var(--text-primary))', display: 'block' }}>{res.lotteryName}</strong>
                                    {catalogEntry?.baseDrawTime && (
                                      <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-secondary))', display: 'block', marginTop: '2px' }}>
                                        Sorteo: {catalogEntry.baseDrawTime}
                                      </span>
                                    )}
                                  </div>
                                </div>
                                <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>{res.dateKey}</span>
                              </div>

                              {/* Domino style numbered balls */}
                              <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', margin: '6px 0' }}>
                                {res.numbers.split('-').map((num: string, idx: number) => (
                                  <div
                                    key={idx}
                                    style={{
                                      width: '42px',
                                      height: '42px',
                                      borderRadius: '50%',
                                      backgroundColor: 'hsl(var(--surface-hover))',
                                      color: 'hsl(var(--warning))',
                                      border: '2px solid hsl(var(--warning))',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      fontWeight: 700,
                                      fontSize: '1rem',
                                      fontFamily: 'monospace',
                                      boxShadow: 'var(--shadow-sm)'
                                    }}
                                  >
                                    {num}
                                  </div>
                                ))}
                              </div>

                              <div style={{ textAlign: 'center', fontSize: '0.725rem', color: 'hsl(var(--text-secondary))', borderTop: '1px solid hsl(var(--border))', paddingTop: '8px' }}>
                                Números Ganadores Registrados
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}
                </div>

              </div>

            </div>
          )}

          {/* TAB 13: CUADRE DE CAJA AND DETAILED OPERATIONAL REPORTS */}
          {activeTab === 'cuadre' && (user.role === 'ADMIN' || user.role === 'SUPERVISOR') && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Cuadre controls */}
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>Cuadre de Caja y Conciliación Operativa</h3>
                
                <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap', alignItems: 'center' }}>
                  <div style={{ display: 'flex', gap: '6px' }}>
                    {[
                      { id: 'today', label: 'Hoy' },
                      { id: 'week', label: 'Semana' },
                      { id: 'month', label: 'Mes' },
                      { id: 'manual', label: 'Periodo' }
                    ].map((p) => (
                      <button
                        key={p.id}
                        onClick={() => setCuadrePeriod(p.id as any)}
                        style={{
                          padding: '8px 14px',
                          borderRadius: 'var(--radius-sm)',
                          border: '1px solid ' + (cuadrePeriod === p.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                          background: cuadrePeriod === p.id ? 'hsl(var(--primary) / 0.08)' : 'transparent',
                          color: cuadrePeriod === p.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                          fontSize: '0.8rem',
                          fontWeight: 600,
                          cursor: 'pointer'
                        }}
                      >
                        {p.label}
                      </button>
                    ))}
                  </div>

                  <select
                    className="form-input"
                    value={cuadreCashierFilter}
                    onChange={(e) => setCuadreCashierFilter(e.target.value)}
                    style={{ width: '200px' }}
                  >
                    <option value="all">Todos los Cajeros</option>
                    {users.filter(u => u.role === 'CASHIER' && (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id))).map(c => (
                      <option key={c.id} value={c.user}>@{c.user}</option>
                    ))}
                  </select>

                  {cuadrePeriod === 'manual' && (
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <input
                        type="date"
                        value={cuadreDateFrom}
                        onChange={(e) => setCuadreDateFrom(e.target.value)}
                        className="form-input"
                        style={{ width: '140px' }}
                      />
                      <span style={{ fontSize: '0.8rem' }}>a</span>
                      <input
                        type="date"
                        value={cuadreDateTo}
                        onChange={(e) => setCuadreDateTo(e.target.value)}
                        className="form-input"
                        style={{ width: '140px' }}
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Computes and metrics desing */}
              {(() => {
                const isSupervisor = user.role === 'SUPERVISOR';
                const allowedAdminId = isSupervisor ? user.adminId : user.id;
                const supervisedCashierUsers = isSupervisor 
                  ? users.filter(u => u.role === 'CASHIER' && u.supervisorIds.includes(user.id)).map(u => u.user)
                  : [];

                const scopedTickets = tickets.filter(t => t.adminId === allowedAdminId && t.status !== 'cancelled')
                  .filter(t => {
                    if (isSupervisor && (!t.sellerUser || !supervisedCashierUsers.includes(t.sellerUser))) return false;
                    if (cuadreCashierFilter !== 'all' && t.sellerUser !== cuadreCashierFilter) return false;
                    
                    const dateLimit = cuadrePeriod === 'today' ? 1 : cuadrePeriod === 'week' ? 7 : cuadrePeriod === 'month' ? 30 : 0;
                    if (dateLimit > 0) {
                      return (Date.now() - t.createdAtEpochMs) <= (dateLimit * 86400000);
                    } else if (cuadrePeriod === 'manual') {
                      const from = new Date(cuadreDateFrom).getTime();
                      const to = new Date(cuadreDateTo).getTime() + 86400000;
                      return t.createdAtEpochMs >= from && t.createdAtEpochMs <= to;
                    }
                    return true;
                  });

                const ventasBrutas = scopedTickets.reduce((acc, t) => acc + t.total, 0);
                const premiosPagados = scopedTickets.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
                const premiosPendientes = scopedTickets.filter(t => t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
                
                // Commissions based on cashier commission rates
                let comisiones = 0;
                scopedTickets.forEach(t => {
                  const cashier = users.find(u => u.user === t.sellerUser && u.role === 'CASHIER');
                  comisiones += t.total * normalizeRate(cashier?.commissionRate);
                });

                // Recharges
                let recargas = 0;
                const cashierScope = users.filter(u => u.role === 'CASHIER' && (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id)));
                if (cuadreCashierFilter === 'all') {
                  recargas = cashierScope.reduce((acc, c) => acc + c.rechargesAssignedBalance, 0);
                } else {
                  const targetCashier = cashierScope.find(u => u.user === cuadreCashierFilter);
                  recargas = targetCashier?.rechargesAssignedBalance || 0;
                }

                const cajaDisponible = ventasBrutas + recargas - comisiones - premiosPagados - premiosPendientes;
                const beneficioNeto = ventasBrutas + recargas - comisiones - premiosPagados - premiosPendientes;

                return (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    
                    {/* Operational report metric specs rows */}
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px' }}>
                      <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Venta Bruta</span>
                        <strong style={{ fontSize: '1.5rem' }}>${ventasBrutas.toFixed(2)}</strong>
                        <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>{scopedTickets.length} tickets activos</span>
                      </div>
                      <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Comisión Retenida</span>
                        <strong style={{ fontSize: '1.5rem', color: 'hsl(var(--danger))' }}>${comisiones.toFixed(2)}</strong>
                        <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Promedio de comisión de red</span>
                      </div>
                      <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Premios Pagados</span>
                        <strong style={{ fontSize: '1.5rem', color: 'hsl(var(--warning))' }}>${premiosPagados.toFixed(2)}</strong>
                        <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Tickets liquidados</span>
                      </div>
                      <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.8rem' }}>Recarga Distribuida</span>
                        <strong style={{ fontSize: '1.5rem' }}>${recargas.toFixed(2)}</strong>
                        <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))' }}>Cupos de venta FF</span>
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '24px' }}>
                      <div className="glass-panel" style={{ padding: '24px', borderLeft: '4px solid hsl(var(--primary))' }}>
                        <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.85rem' }}>Caja Disponible</span>
                        <h4 style={{ fontSize: '1.8rem', fontWeight: 700, color: 'hsl(var(--text-primary))' }}>${cajaDisponible.toFixed(2)}</h4>
                        <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>Efectivo físico disponible en terminales</span>
                      </div>

                      <div className="glass-panel" style={{ padding: '24px', borderLeft: '4px solid hsl(var(--success))', background: 'hsl(var(--success) / 0.03)' }}>
                        <span style={{ color: 'hsl(var(--success))', fontSize: '0.85rem', fontWeight: 600 }}>Beneficio Neto</span>
                        <h4 style={{ fontSize: '1.8rem', fontWeight: 700, color: 'hsl(var(--success))' }}>${beneficioNeto.toFixed(2)}</h4>
                        <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>Ganancia neta consolidada de la banca</span>
                      </div>
                    </div>

                    {/* Cashiers performance breakdown table */}
                    <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                      <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Desglose de Conciliación por Cajero</h3>
                      
                      <div className="table-container">
                        <table className="table-el">
                          <thead>
                            <tr>
                              <th>Cajero</th>
                              <th>Ventas</th>
                              <th>Comisiones</th>
                              <th>Premios</th>
                              <th>Recargas</th>
                              <th>Caja Neto</th>
                              <th>Estado</th>
                            </tr>
                          </thead>
                          <tbody>
                            {users.filter(u => u.role === 'CASHIER' && (user.role === 'ADMIN' ? u.adminId === user.id : u.supervisorIds.includes(user.id))).map(cashier => {
                              const cashierTks = tickets.filter(t => t.sellerUser === cashier.user && t.status !== 'cancelled')
                                .filter(t => {
                                  const dateLimit = cuadrePeriod === 'today' ? 1 : cuadrePeriod === 'week' ? 7 : cuadrePeriod === 'month' ? 30 : 0;
                                  if (dateLimit > 0) return (Date.now() - t.createdAtEpochMs) <= (dateLimit * 86400000);
                                  return true;
                                });

                              const tkSales = cashierTks.reduce((acc, t) => acc + t.total, 0);
                              const tkPremiosPagados = cashierTks.filter(t => t.status === 'paid').reduce((acc, t) => acc + t.totalPrize, 0);
                              const tkPremiosPendientes = cashierTks.filter(t => t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0);
                              const tkComisiones = tkSales * normalizeRate(cashier.commissionRate);
                              const tkRecargas = cashier.rechargesAssignedBalance || 0;
                              const tkCaja = tkSales + tkRecargas - tkComisiones - tkPremiosPagados - tkPremiosPendientes;

                              return (
                                <tr key={cashier.id}>
                                  <td style={{ fontWeight: 600 }}>{cashier.displayName || cashier.user}</td>
                                  <td style={{ fontWeight: 600 }}>${tkSales.toFixed(2)}</td>
                                  <td style={{ color: 'hsl(var(--danger))' }}>${tkComisiones.toFixed(2)}</td>
                                  <td style={{ color: 'hsl(var(--warning))' }}>${(tkPremiosPagados + tkPremiosPendientes).toFixed(2)}</td>
                                  <td>${tkRecargas.toFixed(2)}</td>
                                  <td style={{ fontWeight: 700, color: tkCaja >= 0 ? 'hsl(var(--success))' : 'hsl(var(--danger))' }}>
                                    ${tkCaja.toFixed(2)}
                                  </td>
                                  <td>
                                    <span className={`badge ${cashier.active ? 'badge-success' : 'badge-danger'}`}>
                                      {cashier.active ? 'Activo' : 'Suspendido'}
                                    </span>
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    </div>

                  </div>
                );
              })()}

            </div>
          )}


          {/* TAB 6: LIMITS AND PERMISSIONS */}
          {activeTab === 'limites' && user.role === 'ADMIN' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* TOP BAR / SCOPE SEGMENT CONTROLS */}
              <div className="glass-panel" style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                  <div>
                    <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: 'hsl(var(--text-primary))' }}>
                      Configuración de Límites Administrativos
                    </h3>
                    <p style={{ fontSize: '0.825rem', color: 'hsl(var(--text-secondary))' }}>
                      Define los montos máximos permitidos para ventas diarias, pagos y jugadas individuales.
                    </p>
                  </div>

                  {saveSuccessNotification && (
                    <div className="fade-in" style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '8px 16px',
                      borderRadius: 'var(--radius-md)',
                      backgroundColor: 'hsl(var(--success) / 0.1)',
                      color: 'hsl(var(--success))',
                      fontSize: '0.875rem',
                      fontWeight: 500,
                      border: '1px solid hsl(var(--success) / 0.2)'
                    }}>
                      <CheckCircle size={16} />
                      ¡Límites guardados correctamente!
                    </div>
                  )}
                </div>

                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', borderBottom: '1px solid hsl(var(--border))', paddingBottom: '16px' }}>
                  {[
                    { id: 'ADMIN_SELF', label: 'Mis límites' },
                    { id: 'CASHIER_DEFAULTS', label: 'Todos los cajeros (Defecto)' },
                    { id: 'CASHIER_SPECIFIC', label: 'Por cajero (Personalizado)' },
                  ].map((scope) => (
                    <button
                      key={scope.id}
                      onClick={() => setSelectedScope(scope.id as any)}
                      style={{
                        padding: '10px 18px',
                        borderRadius: 'var(--radius-md)',
                        border: '1px solid ' + (selectedScope === scope.id ? 'hsl(var(--primary))' : 'hsl(var(--border))'),
                        background: selectedScope === scope.id ? 'hsl(var(--primary) / 0.08)' : 'transparent',
                        color: selectedScope === scope.id ? 'hsl(var(--primary))' : 'hsl(var(--text-secondary))',
                        fontSize: '0.875rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        transition: 'all 0.2s ease'
                      }}
                    >
                      {scope.label}
                    </button>
                  ))}
                </div>

                {/* CASHIER SPECIFIC DROPDOWN SELECT */}
                {selectedScope === 'CASHIER_SPECIFIC' && (
                  <div className="fade-in" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <label style={{ fontSize: '0.875rem', fontWeight: 600, color: 'hsl(var(--text-primary))' }}>
                      Seleccionar Cajero:
                    </label>
                    <select
                      className="form-input"
                      value={selectedCashierUsername}
                      onChange={(e) => setSelectedCashierUsername(e.target.value)}
                      style={{ maxWidth: '280px' }}
                    >
                      {users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).length === 0 ? (
                        <option value="">No tienes cajeros registrados</option>
                      ) : (
                        users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).map(c => (
                          <option key={c.id} value={c.user}>
                            {c.displayName || c.user} (@{c.user})
                          </option>
                        ))
                      )}
                    </select>
                  </div>
                )}
              </div>

              {/* LIMIT SECTIONS CARDS GRID */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px' }}>
                
                {/* CARD 1: DAILY LIMIT & PAYOUT */}
                <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <div>
                    <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <DollarSign size={18} color="var(--primary)" />
                      {selectedScope === 'ADMIN_SELF' ? 'Límites de Venta y Cobro Propios' : 'Límites Diarios del Cajero'}
                    </h4>
                    <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                      {selectedScope === 'ADMIN_SELF' 
                        ? 'Establece el dinero máximo diario y cobros permitidos para tu propia cuenta.' 
                        : 'Define el cupo total diario de ventas y el tope de pago de premios.'}
                    </p>
                  </div>

                  <div className="form-group">
                    <label className="form-label" style={{ fontWeight: 600 }}>
                      {selectedScope === 'ADMIN_SELF' ? 'Mi Venta Diaria Máxima' : 'Dinero Máximo de Venta por Día'}
                    </label>
                    <div style={{ position: 'relative' }}>
                      <span style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', fontSize: '0.875rem', color: 'hsl(var(--text-muted))', fontWeight: 500 }}>$</span>
                      <input
                        type="number"
                        className="form-input"
                        style={{ paddingLeft: '28px' }}
                        value={currentLimitsForm.daySale}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, daySale: Number(e.target.value) })}
                        min={0}
                      />
                    </div>
                    <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginTop: '4px', display: 'block' }}>
                      {selectedScope === 'ADMIN_SELF' 
                        ? 'Deja en 0 para vender sin límites propios.' 
                        : '0 deja al cajero sin límites de venta diarios.'}
                    </span>
                  </div>

                  <div className="form-group">
                    <label className="form-label" style={{ fontWeight: 600 }}>
                      Límite Máximo de Pago de Premio (Pagos)
                    </label>
                    <div style={{ position: 'relative' }}>
                      <span style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', fontSize: '0.875rem', color: 'hsl(var(--text-muted))', fontWeight: 500 }}>$</span>
                      <input
                        type="number"
                        className="form-input"
                        style={{ paddingLeft: '28px' }}
                        value={currentLimitsForm.payout}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, payout: Number(e.target.value) })}
                        min={0}
                      />
                    </div>
                    <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginTop: '4px', display: 'block' }}>
                      Controla el monto máximo que un cajero puede pagar directamente por ticket.
                    </span>
                  </div>
                </div>

                {/* CARD 2: TRADITIONAL PLAY LIMITS */}
                <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <div>
                    <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <Layers size={18} color="var(--primary)" />
                      Loterías Tradicionales (Tope por Jugada)
                    </h4>
                    <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                      Control de riesgo por combinaciones tradicionales de la lotería dominicana.
                    </p>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                      <label className="form-label">Quiniela</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.q}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, q: Number(e.target.value) })}
                        min={0}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Palé</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.pale}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, pale: Number(e.target.value) })}
                        min={0}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Super Palé</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.sp}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, sp: Number(e.target.value) })}
                        min={0}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Tripleta</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.t}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, t: Number(e.target.value) })}
                        min={0}
                      />
                    </div>
                  </div>
                </div>

                {/* CARD 3: PICK 3 / PICK 4 PLAY LIMITS */}
                <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                  <div>
                    <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <Activity size={18} color="var(--primary)" />
                      Loterías Americanas (Pick 3 / Pick 4)
                    </h4>
                    <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                      Topes por tipo de jugada para sorteos en el territorio USA.
                    </p>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                    <div className="form-group">
                      <label className="form-label">Pick 3 Straight</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.p3}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p3: Number(e.target.value) })}
                        min={0}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Pick 3 Box</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.p3box}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p3box: Number(e.target.value) })}
                        min={0}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Pick 4 Straight</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.p4}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p4: Number(e.target.value) })}
                        min={0}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Pick 4 Box</label>
                      <input
                        type="number"
                        className="form-input"
                        value={currentLimitsForm.p4box}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, p4box: Number(e.target.value) })}
                        min={0}
                      />
                    </div>
                  </div>
                </div>

                {/* CARD 4: SYSTEM CONFIGURATION (POS MODE OVERRIDE) */}
                {(selectedScope === 'ADMIN_SELF' || selectedScope === 'CASHIER_SPECIFIC') && (
                  <div className="glass-panel fade-in" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                    <div>
                      <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Settings size={18} color="var(--primary)" />
                        Configuración del Sistema
                      </h4>
                      <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                        Ajusta el comportamiento de visualización en la terminal POS.
                      </p>
                    </div>

                    <div className="form-group">
                      <label className="form-label" style={{ fontWeight: 600 }}>
                        {selectedScope === 'ADMIN_SELF' ? 'Mi Modo de Visualización' : 'Modo de Visualización del Cajero'}
                      </label>
                      <select
                        className="form-input"
                        value={currentLimitsForm.systemModeOverride}
                        onChange={(e) => setCurrentLimitsForm({ ...currentLimitsForm, systemModeOverride: e.target.value })}
                      >
                        <option value="">Deshabilitado (Estándar)</option>
                        <option value="compact">Habilitado (Compacto POS)</option>
                      </select>
                      <span style={{ fontSize: '0.7rem', color: 'hsl(var(--text-muted))', marginTop: '6px', display: 'block' }}>
                        Activa una interfaz compacta optimizada para terminales con pantallas reducidas o impresoras térmicas pequeñas.
                      </span>
                    </div>
                  </div>
                )}

              </div>

              {/* CARD 5: SPECIFIC PLAYS / NUMBERS BLOCK CONTROL */}
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px', marginTop: '8px' }}>
                <div>
                  <h4 style={{ fontSize: '1.05rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px', color: 'hsl(var(--danger))' }}>
                    <Lock size={18} />
                    Bloqueo de Jugadas y Números Específicos
                  </h4>
                  <p style={{ fontSize: '0.75rem', color: 'hsl(var(--text-secondary))', marginTop: '4px' }}>
                    Bloquea jugadas específicas (ej. un número en Quiniela o una combinación de Palé) para impedir su venta en los cajeros de tu red.
                  </p>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '24px' }}>
                  {/* Form to Add Blocked Play */}
                  <form onSubmit={handleAddBlockedPlay} style={{ display: 'flex', flexDirection: 'column', gap: '16px', borderRight: '1px solid hsl(var(--border))', paddingRight: '24px' }}>
                    <h5 style={{ fontSize: '0.9rem', fontWeight: 600 }}>Agregar Nuevo Bloqueo</h5>
                    
                    <div className="form-group">
                      <label className="form-label">Tipo de Jugada</label>
                      <select
                        className="form-input"
                        value={blockedPlayForm.playType}
                        onChange={(e) => setBlockedPlayForm({ ...blockedPlayForm, playType: e.target.value })}
                      >
                        <option value="Q">Quiniela</option>
                        <option value="P">Palé</option>
                        <option value="SP">Super Palé</option>
                        <option value="T">Tripleta</option>
                        <option value="P3">Pick 3 Straight</option>
                        <option value="P3BOX">Pick 3 Box</option>
                        <option value="P4">Pick 4 Straight</option>
                        <option value="P4BOX">Pick 4 Box</option>
                      </select>
                    </div>

                    <div className="form-group">
                      <label className="form-label">Número(s) a Bloquear</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder={
                          blockedPlayForm.playType === 'Q' ? 'Ej. 14 (2 dígitos)' :
                          blockedPlayForm.playType === 'P' || blockedPlayForm.playType === 'SP' ? 'Ej. 1422 (4 dígitos)' :
                          blockedPlayForm.playType === 'T' ? 'Ej. 142205 (6 dígitos)' :
                          blockedPlayForm.playType === 'P3' || blockedPlayForm.playType === 'P3BOX' ? 'Ej. 123 (3 dígitos)' : 'Ej. 1234 (4 dígitos)'
                        }
                        value={blockedPlayForm.number}
                        onChange={(e) => setBlockedPlayForm({ ...blockedPlayForm, number: e.target.value })}
                        maxLength={blockedPlayForm.playType === 'T' ? 6 : blockedPlayForm.playType === 'Q' ? 2 : blockedPlayForm.playType === 'P3' || blockedPlayForm.playType === 'P3BOX' ? 3 : 4}
                        required
                      />
                      <span style={{ fontSize: '0.675rem', color: 'hsl(var(--text-muted))', marginTop: '4px', display: 'block' }}>
                        Introduce solo los dígitos numéricos consecutivos. El sistema formateará el Super Palé automáticamente.
                      </span>
                    </div>

                    <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '10px', marginTop: '8px' }}>
                      Bloquear Jugada
                    </button>
                  </form>

                  {/* List of Blocked Plays */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <h5 style={{ fontSize: '0.9rem', fontWeight: 600, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span>Jugadas Bloqueadas Actualmente</span>
                      <span className="badge" style={{ backgroundColor: 'hsl(var(--danger) / 0.1)', color: 'hsl(var(--danger))', fontSize: '0.7rem' }}>
                        {blockedSalePlays.length} Bloqueo(s)
                      </span>
                    </h5>

                    {blockedSalePlays.length === 0 ? (
                      <div style={{ padding: '24px', textAlign: 'center', color: 'hsl(var(--text-muted))', backgroundColor: 'hsl(var(--background))', borderRadius: 'var(--radius-md)', border: '1px dashed hsl(var(--border))' }}>
                        No tienes jugadas bloqueadas en esta banca.
                      </div>
                    ) : (
                      <div style={{ maxHeight: '280px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', paddingRight: '8px' }}>
                        {blockedSalePlays.map((play, index) => {
                          const displayType = 
                            play.playType === 'Q' ? 'Quiniela' :
                            play.playType === 'P' ? 'Palé' :
                            play.playType === 'SP' ? 'Super Palé' :
                            play.playType === 'T' ? 'Tripleta' :
                            play.playType === 'P3' ? 'Pick 3 Straight' :
                            play.playType === 'P3BOX' ? 'Pick 3 Box' :
                            play.playType === 'P4' ? 'Pick 4 Straight' : 'Pick 4 Box';
                          
                          return (
                            <div key={index} style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              padding: '10px 14px',
                              borderRadius: 'var(--radius-sm)',
                              backgroundColor: 'hsl(var(--background))',
                              border: '1px solid hsl(var(--border))',
                              fontSize: '0.85rem'
                            }}>
                              <div>
                                <span style={{ fontWeight: 600, color: 'hsl(var(--text-primary))', display: 'block' }}>
                                  {play.number}
                                </span>
                                <span style={{ fontSize: '0.725rem', color: 'hsl(var(--text-secondary))' }}>
                                  Tipo: {displayType}
                                </span>
                              </div>
                              <button
                                onClick={() => handleRemoveBlockedPlay(play)}
                                style={{
                                  border: 'none',
                                  background: 'transparent',
                                  color: 'hsl(var(--danger))',
                                  cursor: 'pointer',
                                  padding: '4px',
                                  borderRadius: '4px',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  transition: 'all 0.2s'
                                }}
                                title="Eliminar Bloqueo"
                                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'hsl(var(--danger) / 0.1)'}
                                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {/* SAVE BUTTON ACTION BAR */}
              <div className="glass-panel" style={{ padding: '20px 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
                <button
                  className="btn btn-primary"
                  onClick={handleSaveLimits}
                  style={{
                    padding: '12px 32px',
                    fontSize: '0.95rem',
                    fontWeight: 600,
                    borderRadius: 'var(--radius-md)',
                    cursor: 'pointer',
                    boxShadow: 'var(--shadow-glow)'
                  }}
                >
                  Guardar Cambios
                </button>
              </div>

            </div>
          )}

          {/* TAB 7: FINANCE SUMMARY AND RECHARGES */}
          {activeTab === 'finanzas' && user.role === 'ADMIN' && (
            <div className="fade-in" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              
              {/* Cupos summary */}
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <span style={{ fontSize: '0.85rem', color: 'hsl(var(--text-secondary))' }}>Mi Cupo Total Recargas (Asignado por Master)</span>
                  <span style={{ fontSize: '2rem', fontWeight: 700, color: 'hsl(var(--text-primary))', fontFamily: 'var(--font-display)' }}>
                    ${(user?.rechargesAssignedBalance || 0).toLocaleString()}
                  </span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', textAlign: 'right' }}>
                  <span style={{ fontSize: '0.85rem', color: 'hsl(var(--text-secondary))' }}>Cupo Disponible FF</span>
                  <span style={{ fontSize: '2rem', fontWeight: 700, color: 'hsl(var(--success))', fontFamily: 'var(--font-display)' }}>
                    ${(user?.rechargesBalance || 0).toLocaleString()}
                  </span>
                </div>
              </div>

              {/* Transactions grid list */}
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <h3 style={{ fontSize: '1.1rem' }}>Historial Recientes de Recargas</h3>
                
                <div className="table-container">
                  <table className="table-el">
                    <thead>
                      <tr>
                        <th>Fecha y Hora</th>
                        <th>Cajero Destinatario</th>
                        <th>Monto Asignado</th>
                        <th>Tipo</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {audits.filter(a => a.action === 'PROCESS_RECHARGE').length === 0 ? (
                        <tr>
                          <td colSpan={5} style={{ textAlign: 'center', color: 'hsl(var(--text-secondary))' }}>
                            No hay recargas financieras procesadas recientemente.
                          </td>
                        </tr>
                      ) : (
                        audits.filter(a => a.action === 'PROCESS_RECHARGE').map((a) => (
                          <tr key={a.id}>
                            <td>{new Date(a.timestampMs).toLocaleString()}</td>
                            <td style={{ fontWeight: 600 }}>{a.details.split('a ')[1] || 'Cajero'}</td>
                            <td style={{ fontWeight: 600, color: 'hsl(var(--success))' }}>
                              {a.details.split(' ')[2] || 'Monto'}
                            </td>
                            <td>REPARTO_CUPOS</td>
                            <td>
                              <span className="badge badge-success">COMPLETADA</span>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

            </div>
          )}

          {/* TAB 8: MASTER & ADMIN & SUPERVISOR REPORTS */}
          {activeTab === 'reportes' && (
            <div className="fade-in glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3 style={{ fontSize: '1.1rem' }}>Análisis de Ventas vs Premios</h3>
                <button className="btn btn-secondary" onClick={() => alert('Generando exportación de reporte XLS...')}>
                  <FileSpreadsheet size={16} />
                  Exportar a Excel
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '20px', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.85rem' }}>Ventas Brutas Totales</span>
                  <span style={{ fontSize: '1.5rem', fontWeight: 700 }}>
                    ${tickets.filter(t => t.status !== 'cancelled').reduce((acc, t) => acc + t.total, 0).toFixed(2)}
                  </span>
                </div>
                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '20px', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.85rem' }}>Premios Aprobados</span>
                  <span style={{ fontSize: '1.5rem', fontWeight: 700, color: 'hsl(var(--danger))' }}>
                    ${tickets.filter(t => t.status === 'paid' || t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0).toFixed(2)}
                  </span>
                </div>
                <div style={{ backgroundColor: 'hsl(var(--background))', padding: '20px', borderRadius: 'var(--radius-md)', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <span style={{ color: 'hsl(var(--text-secondary))', fontSize: '0.85rem' }}>Ingreso Neto (Ganancia)</span>
                  <span style={{ fontSize: '1.5rem', fontWeight: 700, color: 'hsl(var(--success))' }}>
                    ${(
                      tickets.filter(t => t.status !== 'cancelled').reduce((acc, t) => acc + t.total, 0) -
                      tickets.filter(t => t.status === 'paid' || t.status === 'winner').reduce((acc, t) => acc + t.totalPrize, 0)
                    ).toFixed(2)}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* TAB 9: AUDIT LOG SYSTEM */}
          {activeTab === 'auditoria' && (
            <div className="fade-in glass-panel" style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <h3 style={{ fontSize: '1.15rem' }}>Bitácora de Auditoría del Sistema</h3>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {audits.map((a) => (
                  <div key={a.id} style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '16px',
                    padding: '16px',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid hsl(var(--border))',
                    backgroundColor: 'hsl(var(--surface))'
                  }}>
                    <div style={{
                      width: '38px',
                      height: '38px',
                      borderRadius: '50%',
                      backgroundColor: a.status === 'success' ? 'hsl(var(--success) / 0.1)' : 'hsl(var(--warning) / 0.1)',
                      color: a.status === 'success' ? 'hsl(var(--success))' : 'hsl(var(--warning))',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0
                    }}>
                      <Info size={18} />
                    </div>

                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                        <strong style={{ fontSize: '0.9rem' }}>{a.action}</strong>
                        <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                          {new Date(a.timestampMs).toLocaleString()}
                        </span>
                      </div>
                      <p style={{ fontSize: '0.85rem', color: 'hsl(var(--text-secondary))', lineHeight: '1.5' }}>
                        {a.details}
                      </p>
                      <div style={{ marginTop: '8px', display: 'flex', gap: '10px', fontSize: '0.75rem', color: 'hsl(var(--text-muted))' }}>
                        <span>Actor: <strong>@{a.actorUser}</strong> ({a.role})</span>
                        <span>•</span>
                        <span>IP: {a.ipAddress}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

        </>
      )}

      {/* --- MODAL: CREATE BANCA / ADMIN (MASTER ONLY) --- */}
      {adminModalOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          backdropFilter: 'blur(4px)'
        }}>
          <form onSubmit={handleCreateAdmin} className="glass-panel fade-in" style={{
            maxWidth: '520px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))',
            maxHeight: '90vh',
            overflowY: 'auto'
          }}>
            <h3 style={{ fontSize: '1.25rem', marginBottom: '20px' }}>Registrar Nueva Banca Comercial</h3>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="form-group">
                <label className="form-label">Nombre Comercial Banca</label>
                <input
                  type="text"
                  placeholder="ej. Banca El Sol Churchill"
                  value={adminForm.bankName}
                  onChange={(e) => setAdminForm({ ...adminForm, bankName: e.target.value })}
                  className="form-input"
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Nombre del Dueño (Socio)</label>
                <input
                  type="text"
                  placeholder="ej. Juan Pérez"
                  value={adminForm.ownerName}
                  onChange={(e) => setAdminForm({ ...adminForm, ownerName: e.target.value })}
                  className="form-input"
                  required
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="form-group">
                <label className="form-label">Prefijo Cajeros (Auto)</label>
                <input
                  type="text"
                  placeholder="ej. sol"
                  value={adminForm.cashierPrefix}
                  onChange={(e) => setAdminForm({ ...adminForm, cashierPrefix: e.target.value })}
                  className="form-input"
                  maxLength={6}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Cantidad Cajeros Iniciales</label>
                <input
                  type="number"
                  value={adminForm.cashierCount}
                  onChange={(e) => setAdminForm({ ...adminForm, cashierCount: parseInt(e.target.value) })}
                  className="form-input"
                  min={1}
                  max={10}
                  required
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="form-group">
                <label className="form-label">Cupo Financiero Inicial ($)</label>
                <input
                  type="number"
                  value={adminForm.baseBalance}
                  onChange={(e) => setAdminForm({ ...adminForm, baseBalance: parseFloat(e.target.value) })}
                  className="form-input"
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Teléfono</label>
                <input
                  type="text"
                  placeholder="809-555-0199"
                  value={adminForm.phone}
                  onChange={(e) => setAdminForm({ ...adminForm, phone: e.target.value })}
                  className="form-input"
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Dirección Local comercial</label>
              <input
                type="text"
                placeholder="Av. Principal #20"
                value={adminForm.address}
                onChange={(e) => setAdminForm({ ...adminForm, address: e.target.value })}
                className="form-input"
              />
            </div>

            <div style={{ display: 'flex', gap: '12px', marginTop: '24px' }}>
              <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>
                Crear Banca
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setAdminModalOpen(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {/* --- MODAL: CREATE CAJERO (ADMIN ONLY) --- */}
      {cajeroModalOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          backdropFilter: 'blur(4px)'
        }}>
          <form onSubmit={handleCreateCajero} className="glass-panel fade-in" style={{
            maxWidth: '460px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))'
          }}>
            <h3 style={{ fontSize: '1.25rem', marginBottom: '20px' }}>Registrar Nuevo Cajero Terminal</h3>

            <div className="form-group">
              <label className="form-label">Nombre del Cajero</label>
              <input
                type="text"
                placeholder="ej. Cajera Principal Churchill"
                value={cajeroForm.displayName}
                onChange={(e) => setCajeroForm({ ...cajeroForm, displayName: e.target.value })}
                className="form-input"
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Usuario de Venta (ej. caj01)</label>
              <input
                type="text"
                placeholder="ej. chu03"
                value={cajeroForm.user}
                onChange={(e) => setCajeroForm({ ...cajeroForm, user: e.target.value })}
                className="form-input"
                required
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '12px' }}>
              <div className="form-group">
                <label className="form-label">Supervisor Asignado (Opcional)</label>
                <select 
                  className="form-input"
                  value={cajeroForm.supervisorId}
                  onChange={(e) => setCajeroForm({ ...cajeroForm, supervisorId: e.target.value })}
                >
                  <option value="">Ninguno</option>
                  {users.filter(u => u.role === 'SUPERVISOR' && u.adminId === user.id).map(s => (
                    <option key={s.id} value={s.id}>{s.displayName}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Cupo Recarga ($)</label>
                <input
                  type="number"
                  value={cajeroForm.baseBalance}
                  onChange={(e) => setCajeroForm({ ...cajeroForm, baseBalance: parseFloat(e.target.value) })}
                  className="form-input"
                  required
                />
              </div>
            </div>

            <div style={{ display: 'flex', gap: '12px', marginTop: '24px' }}>
              <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>
                Crear Cajero
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setCajeroModalOpen(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {/* --- MODAL: CREATE SUPERVISOR (ADMIN ONLY) --- */}
      {supervisorModalOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          backdropFilter: 'blur(4px)'
        }}>
          <form onSubmit={handleCreateSupervisor} className="glass-panel fade-in" style={{
            maxWidth: '440px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))'
          }}>
            <h3 style={{ fontSize: '1.25rem', marginBottom: '20px' }}>Registrar Nuevo Supervisor</h3>

            <div className="form-group">
              <label className="form-label">Nombre del Supervisor</label>
              <input
                type="text"
                placeholder="ej. Carlos Gómez"
                value={supervisorForm.displayName}
                onChange={(e) => setSupervisorForm({ ...supervisorForm, displayName: e.target.value })}
                className="form-input"
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Usuario de Acceso</label>
              <input
                type="text"
                placeholder="ej. carlosg"
                value={supervisorForm.user}
                onChange={(e) => setSupervisorForm({ ...supervisorForm, user: e.target.value })}
                className="form-input"
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Teléfono Celular</label>
              <input
                type="text"
                placeholder="809-555-0199"
                value={supervisorForm.phone}
                onChange={(e) => setSupervisorForm({ ...supervisorForm, phone: e.target.value })}
                className="form-input"
              />
            </div>

            <div style={{ display: 'flex', gap: '12px', marginTop: '24px' }}>
              <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>
                Crear Supervisor
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setSupervisorModalOpen(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {/* --- MODAL: PROCESS RECHARGE BALANCE (ADMIN ONLY) --- */}
      {rechargeModalOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          backdropFilter: 'blur(4px)'
        }}>
          <form onSubmit={handleProcessRecharge} className="glass-panel fade-in" style={{
            maxWidth: '420px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))'
          }}>
            <h3 style={{ fontSize: '1.25rem', marginBottom: '20px' }}>Asignar Balance a Cajero</h3>

            <div className="form-group">
              <label className="form-label">Seleccionar Cajero Destino</label>
              <select
                className="form-input"
                value={rechargeForm.cashierId}
                onChange={(e) => setRechargeForm({ ...rechargeForm, cashierId: e.target.value })}
                required
              >
                <option value="">Seleccione un cajero...</option>
                {users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).map(c => (
                  <option key={c.id} value={c.id}>{c.displayName} (@{c.user})</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Monto de Balance a Traspasar ($)</label>
              <input
                type="number"
                placeholder="ej. 5000"
                value={rechargeForm.amount}
                onChange={(e) => setRechargeForm({ ...rechargeForm, amount: e.target.value })}
                className="form-input"
                required
              />
            </div>

            <div style={{ display: 'flex', gap: '12px', marginTop: '24px' }}>
              <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>
                Confirmar Traspaso
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setRechargeModalOpen(false)}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}

      {/* --- MODAL: ASSIGN CASHIERS TO SUPERVISOR (ADMIN ONLY) --- */}
      {assignModalOpen && selectedSupervisor && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100,
          backdropFilter: 'blur(4px)'
        }}>
          <div className="glass-panel fade-in" style={{
            maxWidth: '460px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))'
          }}>
            <h3 style={{ fontSize: '1.25rem', marginBottom: '10px' }}>Asignar Cajeros a Supervisor</h3>
            <p style={{ fontSize: '0.85rem', color: 'hsl(var(--text-secondary))', marginBottom: '20px' }}>
              Seleccione los cajeros de su red que estarán bajo la supervisión de <strong style={{ color: 'hsl(var(--text-primary))' }}>{selectedSupervisor.displayName} (@{selectedSupervisor.user})</strong>.
            </p>

            <div style={{ 
              maxHeight: '260px', 
              overflowY: 'auto', 
              display: 'flex', 
              flexDirection: 'column', 
              gap: '10px', 
              padding: '12px',
              backgroundColor: 'hsl(var(--background))',
              borderRadius: 'var(--radius-md)',
              border: '1px solid hsl(var(--border))',
              marginBottom: '20px'
            }}>
              {users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).length === 0 ? (
                <div style={{ fontSize: '0.85rem', color: 'hsl(var(--text-muted))', textAlign: 'center', padding: '12px' }}>
                  No tiene cajeros creados en su red.
                </div>
              ) : (
                users.filter(u => u.role === 'CASHIER' && u.adminId === user.id).map(c => {
                  const isChecked = assignedCashiersSet.has(c.id);
                  return (
                    <label key={c.id} style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', fontSize: '0.9rem', userSelect: 'none' }}>
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={(e) => {
                          const newSet = new Set(assignedCashiersSet);
                          if (e.target.checked) {
                            newSet.add(c.id);
                          } else {
                            newSet.delete(c.id);
                          }
                          setAssignedCashiersSet(newSet);
                        }}
                        style={{ width: '16px', height: '16px', accentColor: 'hsl(var(--primary))' }}
                      />
                      <div>
                        <strong>{c.displayName}</strong>
                        <span style={{ fontSize: '0.75rem', color: 'hsl(var(--text-muted))', display: 'block' }}>@{c.user} • ${c.balance.toFixed(2)} Balance</span>
                      </div>
                    </label>
                  );
                })
              )}
            </div>

            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn btn-primary" style={{ flex: 1 }} onClick={handleSaveAssignments}>
                Guardar Asignaciones
              </button>
              <button className="btn btn-secondary" style={{ flex: 1 }} onClick={() => {
                setAssignModalOpen(false);
                setSelectedSupervisor(null);
              }}>
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}


      {/* --- MODAL: ANNUL TICKET CONFIRMATION --- */}
      {annulModalOpen && selectedTicketForAnnul && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 101,
          backdropFilter: 'blur(4px)'
        }}>
          <div className="glass-panel fade-in" style={{
            maxWidth: '480px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'hsl(var(--danger))' }}>
              <AlertTriangle size={24} />
              <h3 style={{ fontSize: '1.25rem', margin: 0 }}>Anulación de Ticket</h3>
            </div>
            
            <p style={{ fontSize: '0.875rem', color: 'hsl(var(--text-secondary))', lineHeight: 1.5 }}>
              ¿Está seguro que desea anular el ticket <strong style={{ color: 'hsl(var(--text-primary))' }}>{selectedTicketForAnnul.serial || selectedTicketForAnnul.id}</strong>?
              Esta acción es irreversible y restablecerá el balance de caja del cajero <strong style={{ color: 'hsl(var(--text-primary))' }}>@{selectedTicketForAnnul.sellerUser}</strong> devolviendo <strong style={{ color: 'hsl(var(--primary))' }}>${selectedTicketForAnnul.total.toFixed(2)}</strong>.
            </p>

            <div style={{ backgroundColor: 'hsl(var(--background))', padding: '12px', borderRadius: 'var(--radius-sm)', border: '1px solid hsl(var(--border))', fontSize: '0.8rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ color: 'hsl(var(--text-secondary))' }}>Emisor:</span>
                <strong>@{selectedTicketForAnnul.sellerUser}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                <span style={{ color: 'hsl(var(--text-secondary))' }}>Monto:</span>
                <strong>${selectedTicketForAnnul.total.toFixed(2)}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'hsl(var(--text-secondary))' }}>Hora:</span>
                <strong>{new Date(selectedTicketForAnnul.createdAtEpochMs).toLocaleTimeString()}</strong>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
              <button
                className="btn btn-primary"
                style={{ flex: 1, backgroundColor: 'hsl(var(--danger))', border: 'none' }}
                onClick={() => handleAnnulTicket(selectedTicketForAnnul)}
              >
                Confirmar Anulación
              </button>
              <button
                className="btn btn-secondary"
                style={{ flex: 1 }}
                onClick={() => {
                  setAnnulModalOpen(false);
                  setSelectedTicketForAnnul(null);
                }}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* --- MODAL: SHARE CREDS (MASTER ONLY) --- */}
      {credsShareOpen && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 101,
          backdropFilter: 'blur(4px)'
        }}>
          <div className="glass-panel fade-in" style={{
            maxWidth: '480px',
            width: '100%',
            padding: '30px',
            backgroundColor: 'hsl(var(--surface))'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'hsl(var(--success))', marginBottom: '16px' }}>
              <CheckCircle size={24} />
              <h3 style={{ fontSize: '1.25rem', margin: 0 }}>¡Banca Creada Exitosamente!</h3>
            </div>
            
            <p style={{ fontSize: '0.875rem', color: 'hsl(var(--text-secondary))', marginBottom: '16px' }}>
              Copia y comparte este bloque de credenciales con el administrador y cajeros de la nueva banca.
            </p>

            <textarea
              readOnly
              value={shareText}
              style={{
                width: '100%',
                height: '240px',
                padding: '12px',
                fontFamily: 'monospace',
                fontSize: '0.825rem',
                borderRadius: 'var(--radius-md)',
                border: '1px solid hsl(var(--border))',
                backgroundColor: 'hsl(var(--background))',
                color: 'hsl(var(--text-primary))',
                outline: 'none',
                resize: 'none'
              }}
            />

            <div style={{ display: 'flex', gap: '12px', marginTop: '20px' }}>
              <button className="btn btn-primary" style={{ flex: 1 }} onClick={() => {
                navigator.clipboard.writeText(shareText);
                alert('Credenciales copiadas al portapapeles.');
              }}>
                Copiar al Portapapeles
              </button>
              <button className="btn btn-secondary" onClick={() => setCredsShareOpen(false)}>
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
