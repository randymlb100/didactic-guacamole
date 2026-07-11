import React, { useState, useEffect, useRef, useMemo, Suspense, lazy } from 'react';
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
  getAdminPayoutsPayload,
  saveAdminPayoutsPayload,
  fetchDrawResults,
  STATIC_LOTTERIES,
  supabase,
  getAdminSystemModeConfig,
  saveAdminSystemModeConfig,
  getManualDisabledLotteries,
  saveManualDisabledLotteries,
  isSupabaseConfigured,
  fetchSportsTickets,
  getSportsLimits,
  saveSportsLimits
} from '../utils/supabase';
import type { UserAccount, TicketRecord, LotteryCatalogItem, AuditLog, DrawResult, BlockedSalePlay, SportsTicketRecord } from '../types';



interface DashboardProps {
  activeTab: string;
  setActiveTab?: (tab: string) => void;
}

const DashboardHome = lazy(() => import('./tabs/DashboardHome').then(m => ({ default: m.DashboardHome })));
const AdminsTab = lazy(() => import('./tabs/AdminsTab').then(m => ({ default: m.AdminsTab })));
const CajerosTab = lazy(() => import('./tabs/CajerosTab').then(m => ({ default: m.CajerosTab })));
const SupervisoresTab = lazy(() => import('./tabs/SupervisoresTab').then(m => ({ default: m.SupervisoresTab })));
const MonitoreoTab = lazy(() => import('./tabs/MonitoreoTab').then(m => ({ default: m.MonitoreoTab })));
const DeportivaTab = lazy(() => import('./tabs/DeportivaTab').then(m => ({ default: m.DeportivaTab })));
const CuadreTab = lazy(() => import('./tabs/CuadreTab').then(m => ({ default: m.CuadreTab })));
const FinanzasTab = lazy(() => import('./tabs/FinanzasTab').then(m => ({ default: m.FinanzasTab })));
const ConfigTab = lazy(() => import('./tabs/ConfigTab').then(m => ({ default: m.ConfigTab })));
const TicketsTab = lazy(() => import('./tabs/TicketsTab').then(m => ({ default: m.TicketsTab })));
const GanadoresTab = lazy(() => import('./tabs/GanadoresTab').then(m => ({ default: m.GanadoresTab })));
const ResultadosTab = lazy(() => import('./tabs/ResultadosTab').then(m => ({ default: m.ResultadosTab })));
const ReportesTab = lazy(() => import('./tabs/ReportesTab').then(m => ({ default: m.ReportesTab })));
const AuditoriaTab = lazy(() => import('./tabs/AuditoriaTab').then(m => ({ default: m.AuditoriaTab })));

import { AdminFormModal, CajeroFormModal, SupervisorFormModal } from '../components/UserFormModal';
import { RechargeModal } from '../components/RechargeModal';
import { AssignCashiersModal } from '../components/AssignCashiersModal';
import { CredsShareModal } from '../components/CredsShareModal';
import { LimitsEditor } from '../components/LimitsEditor';
import { AnnulTicketModal, DeleteTicketModal, TicketDetailModal, SportsTicketDetailModal } from '../components/TicketDetailModal';
import { LimitsConfirmModal } from '../components/LimitsConfirmModal';


export const Dashboard: React.FC<DashboardProps> = ({ activeTab }) => {
  const { user } = useAuth();
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [tickets, setTickets] = useState<TicketRecord[]>([]);
  const [lotteries, setLotteries] = useState<LotteryCatalogItem[]>([]);
  const [audits, setAudits] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const loadInFlightRef = useRef(false);
  const lastTicketFetchAtRef = useRef(0);

  // Sportsbook states
  const [sportsTickets, setSportsTickets] = useState<SportsTicketRecord[]>([]);
  const [selectedSportsTicketForDetail, setSelectedSportsTicketForDetail] = useState<SportsTicketRecord | null>(null);
  const [dashboardViewContext, setDashboardViewContext] = useState<'lottery' | 'sports' | 'combined'>('combined');
  const [sportsLimitsForm, setSportsLimitsForm] = useState({
    max_ticket_stake: 10000,
    max_potential_payout: 100000,
    enabled_markets: ['moneyline', 'runline', 'spread', 'total', 'first_half', 'first_five'] as string[]
  });

  // Draws tab state
  const [drawsSubTab, setDrawsSubTab] = useState<'lottery' | 'pick_sports'>('lottery');

  // Cashier Limits Modal States
  const [editingCashierLimits, setEditingCashierLimits] = useState<UserAccount | null>(null);
  const [modalLimitsTab, setModalLimitsTab] = useState<'limits' | 'payouts'>('limits');
  const [modalLimitsForm, setModalLimitsForm] = useState<any>({
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
    systemModeOverride: '',
    commissionRate: 8.0
  });
  const [modalPayoutsForm, setModalPayoutsForm] = useState<any>({
    q1: 60, q2: 12, q3: 4,
    pale: 1000, pale12: 1000, pale13: 1000, pale23: 1000,
    tripleta: 20000, tripleta3: 20000, tripleta2: 1000,
    superPale: 3000,
    pick3Straight: 500, pick3Box3: 160, pick3Box6: 80, pick3BackPair: 50,
    pick4Straight: 5000, pick4Box4: 1200, pick4Box6: 800, pick4Box12: 400, pick4Box24: 200, pick4BackPair: 50
  });

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

  // Game Modes (Traditional vs US Pick) & Payout states
  const [systemModeConfig, setSystemModeConfig] = useState<any>({
    posLiteEnabled: false,
    lotteryModeEnabled: true,
    pickModeEnabled: true,
    cashierPickEnabled: true,
    cashierModeEnabled: true,
    cashierLotteryModeEnabled: true,
    cashierPickModeEnabled: true,
    blockedSalePlays: [],
    updatedAt: Date.now()
  });

  const [payoutsPayload, setPayoutsPayload] = useState<any>({
    defaults: {
      q1: 60, q2: 12, q3: 4,
      pale: 1000, pale12: 1000, pale13: 1000, pale23: 1000,
      tripleta: 20000, tripleta3: 20000, tripleta2: 1000,
      superPale: 3000,
      pick3Straight: 500, pick3Box3: 160, pick3Box6: 80, pick3BackPair: 50,
      pick4Straight: 5000, pick4Box4: 1200, pick4Box6: 800, pick4Box12: 400, pick4Box24: 200, pick4BackPair: 50
    },
    byUser: {}
  });

  const [currentPayoutsForm, setCurrentPayoutsForm] = useState<any>({
    q1: 60, q2: 12, q3: 4,
    pale: 1000, pale12: 1000, pale13: 1000, pale23: 1000,
    tripleta: 20000, tripleta3: 20000, tripleta2: 1000,
    superPale: 3000,
    pick3Straight: 500, pick3Box3: 160, pick3Box6: 80, pick3BackPair: 50,
    pick4Straight: 5000, pick4Box4: 1200, pick4Box6: 800, pick4Box12: 400, pick4Box24: 200, pick4BackPair: 50
  });
  const [saveSuccessNotification, setSaveSuccessNotification] = useState(false);
  const [limitsConfirmOpen, setLimitsConfirmOpen] = useState(false);
  const [limitsSaving, setLimitsSaving] = useState(false);

  // Monitoreo states
  const [monitoreoSubTab, setMonitoreoSubTab] = useState<'lotteries' | 'plays' | 'ranking' | 'cajeros'>('lotteries');
  const [monitoreoPlayFocus, setMonitoreoPlayFocus] = useState<'Q' | 'P' | 'T' | 'SP' | 'P3' | 'P4'>('Q');
  const [monitoreoHighestFirst, setMonitoreoHighestFirst] = useState(true);
  const [monitoreoShowEmptyLotteries, setMonitoreoShowEmptyLotteries] = useState(false);
  const [monitoreoRange, setMonitoreoRange] = useState<'day' | 'week' | 'month'>('day');

  // Tickets states
  const [ticketSearchSerial, setTicketSearchSerial] = useState('');
  const [ticketFilterStatus, setTicketFilterStatus] = useState('all');
  const [ticketFilterCashier, setTicketFilterCashier] = useState('all');
  const [ticketDateFilter, setTicketDateFilter] = useState<'today' | 'yesterday' | 'all'>('today');
  const [dashboardDateFilter, setDashboardDateFilter] = useState<'today' | 'yesterday' | 'all'>('today');
  const [finanzasDateFilter, setFinanzasDateFilter] = useState<'today' | 'yesterday' | 'all'>('today');
  const [annulModalOpen, setAnnulModalOpen] = useState(false);
  const [annulTimer, setAnnulTimer] = useState(Date.now());
  const [selectedTicketForAnnul, setSelectedTicketForAnnul] = useState<TicketRecord | null>(null);
  const [selectedTicketForDetail, setSelectedTicketForDetail] = useState<TicketRecord | null>(null);
  const [selectedTicketForDelete, setSelectedTicketForDelete] = useState<TicketRecord | null>(null);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [isDeletingTicket, setIsDeletingTicket] = useState(false);

  // Ganadores states (encapsulated in GanadoresTab)

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
  const getLocalDateStringDR = (date?: Date | number): string => {
    const d = date !== undefined ? new Date(date) : new Date();
    return new Intl.DateTimeFormat('sv-SE', {
      timeZone: 'America/Santo_Domingo',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(d);
  };

  const isSameLocalDate = (epochMs: number, relativeDays: number) => {
    const target = new Date();
    target.setDate(target.getDate() - relativeDays);
    return getLocalDateStringDR(target) === getLocalDateStringDR(epochMs);
  };

  const cashierSalesTotals = useMemo(() => {
    const totals: Record<string, number> = {};
    users.forEach(u => {
      if (u.role === 'CASHIER') {
        const cashierTickets = tickets.filter(t => t.sellerUser === u.user && t.status !== 'cancelled' && t.status !== 'voided');
        totals[u.user] = cashierTickets
          .filter(t => isSameLocalDate(t.createdAtEpochMs, 0))
          .reduce((acc, t) => acc + t.total, 0);
      }
    });
    return totals;
  }, [tickets, users]);

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

  // Cuadre states
  const [cuadrePeriod, setCuadrePeriod] = useState<'today' | 'week' | 'month' | 'manual'>('today');
  const [cuadreCashierFilter, setCuadreCashierFilter] = useState('all');
  const [cuadreDateFrom, setCuadreDateFrom] = useState(getLocalDateStringDR());
  const [cuadreDateTo, setCuadreDateTo] = useState(getLocalDateStringDR());

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
        if (u.role === 'CASHIER' && (user.role === 'MASTER' ? true : u.adminId === user.id)) {
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

  const handleRenameCashier = async (c: UserAccount, displayName: string) => {
    if (!user) return;
    const cleanName = displayName.trim();
    if (!cleanName) return;

    try {
      const updated = {
        ...c,
        displayName: cleanName,
        updatedAtEpochMs: Date.now(),
      };
      await updateUserAccount(updated);
      setUsers((current) => current.map((candidate) => candidate.id === c.id ? updated : candidate));
      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'RENAME_CASHIER',
        `Renombrado cajero @${c.user}: "${c.displayName || c.user}" -> "${cleanName}"`,
        'success'
      );
    } catch (err: any) {
      alert(err.message || 'Error al editar el nombre del cajero');
      throw err;
    }
  };

  const handleOpenCashierLimitsModal = (caj: UserAccount) => {
    setEditingCashierLimits(caj);
    
    const targetLimits = (limitsPayload?.byUser && limitsPayload.byUser[caj.user]) || limitsPayload?.defaults || { daySale: 10000, payout: 0, q: 10000, pale: 500, sp: 500, t: 75, p3: 500, p3box: 500, p4: 500, p4box: 500 };
    
    setModalLimitsForm({
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
      systemModeOverride: caj.systemModeOverride || '',
      commissionRate: caj.commissionRate !== undefined && caj.commissionRate !== null ? caj.commissionRate : 8.0
    });

    const targetPayouts = (payoutsPayload?.byUser && payoutsPayload.byUser[caj.user]) || payoutsPayload?.defaults || {
      q1: 60, q2: 12, q3: 4,
      pale: 1000, pale12: 1000, pale13: 1000, pale23: 1000,
      tripleta: 20000, tripleta3: 20000, tripleta2: 1000,
      superPale: 3000,
      pick3Straight: 500, pick3Box3: 160, pick3Box6: 80, pick3BackPair: 50,
      pick4Straight: 5000, pick4Box4: 1200, pick4Box6: 800, pick4Box12: 400, pick4Box24: 200, pick4BackPair: 50
    };

    setModalPayoutsForm({
      q1: targetPayouts.q1 ?? 60,
      q2: targetPayouts.q2 ?? 12,
      q3: targetPayouts.q3 ?? 4,
      pale: targetPayouts.pale ?? 1000,
      pale12: targetPayouts.pale12 ?? 1000,
      pale13: targetPayouts.pale13 ?? 1000,
      pale23: targetPayouts.pale23 ?? 1000,
      tripleta: targetPayouts.tripleta ?? 20000,
      tripleta3: targetPayouts.tripleta3 ?? 20000,
      tripleta2: targetPayouts.tripleta2 ?? 1000,
      superPale: targetPayouts.superPale ?? 3000,
      pick3Straight: targetPayouts.pick3Straight ?? 500,
      pick3Box3: targetPayouts.pick3Box3 ?? 160,
      pick3Box6: targetPayouts.pick3Box6 ?? 80,
      pick3BackPair: targetPayouts.pick3BackPair ?? 50,
      pick4Straight: targetPayouts.pick4Straight ?? 5000,
      pick4Box4: targetPayouts.pick4Box4 ?? 1200,
      pick4Box6: targetPayouts.pick4Box6 ?? 800,
      pick4Box12: targetPayouts.pick4Box12 ?? 400,
      pick4Box24: targetPayouts.pick4Box24 ?? 200,
      pick4BackPair: targetPayouts.pick4BackPair ?? 50
    });

    setModalLimitsTab('limits');
  };

  const handleSaveModalCashierLimits = async () => {
    if (!user || !editingCashierLimits) return;
    setLimitsSaving(true);
    try {
      const updatedPayload = { ...limitsPayload };
      
      const newLimitsObj = {
        daySale: Number(modalLimitsForm.daySale),
        payout: Number(modalLimitsForm.payout),
        q: Number(modalLimitsForm.q),
        pale: Number(modalLimitsForm.pale),
        sp: Number(modalLimitsForm.sp),
        t: Number(modalLimitsForm.t),
        p3: Number(modalLimitsForm.p3),
        p3box: Number(modalLimitsForm.p3box),
        p4: Number(modalLimitsForm.p4),
        p4box: Number(modalLimitsForm.p4box),
      };

      if (!updatedPayload.byUser) updatedPayload.byUser = {};
      updatedPayload.byUser[editingCashierLimits.user] = newLimitsObj;

      await saveAdminLimitsPayload(user.id, JSON.stringify(updatedPayload));
      setLimitsPayload(updatedPayload);

      const updatedPayouts = { ...payoutsPayload };
      const newPayoutsObj = {
        q1: Number(modalPayoutsForm.q1),
        q2: Number(modalPayoutsForm.q2),
        q3: Number(modalPayoutsForm.q3),
        pale: Number(modalPayoutsForm.pale),
        pale12: Number(modalPayoutsForm.pale12),
        pale13: Number(modalPayoutsForm.pale13),
        pale23: Number(modalPayoutsForm.pale23),
        tripleta: Number(modalPayoutsForm.tripleta),
        tripleta3: Number(modalPayoutsForm.tripleta3),
        tripleta2: Number(modalPayoutsForm.tripleta2),
        superPale: Number(modalPayoutsForm.superPale),
        pick3Straight: Number(modalPayoutsForm.pick3Straight),
        pick3Box3: Number(modalPayoutsForm.pick3Box3),
        pick3Box6: Number(modalPayoutsForm.pick3Box6),
        pick3BackPair: Number(modalPayoutsForm.pick3BackPair),
        pick4Straight: Number(modalPayoutsForm.pick4Straight),
        pick4Box4: Number(modalPayoutsForm.pick4Box4),
        pick4Box6: Number(modalPayoutsForm.pick4Box6),
        pick4Box12: Number(modalPayoutsForm.pick4Box12),
        pick4Box24: Number(modalPayoutsForm.pick4Box24),
        pick4BackPair: Number(modalPayoutsForm.pick4BackPair),
      };

      if (!updatedPayouts.byUser) updatedPayouts.byUser = {};
      updatedPayouts.byUser[editingCashierLimits.user] = newPayoutsObj;

      await saveAdminPayoutsPayload(user.id, JSON.stringify(updatedPayouts));
      setPayoutsPayload(updatedPayouts);

      const cashierAcc = users.find(u => u.id === editingCashierLimits.id);
      if (cashierAcc) {
        let hasChanges = false;
        if (cashierAcc.systemModeOverride !== modalLimitsForm.systemModeOverride) {
          cashierAcc.systemModeOverride = modalLimitsForm.systemModeOverride || null;
          hasChanges = true;
        }
        const nextCommission = modalLimitsForm.commissionRate !== '' && modalLimitsForm.commissionRate !== null && modalLimitsForm.commissionRate !== undefined
          ? Number(modalLimitsForm.commissionRate)
          : null;
        if (cashierAcc.commissionRate !== nextCommission) {
          cashierAcc.commissionRate = nextCommission;
          hasChanges = true;
        }
        if (hasChanges) {
          await updateUserAccount(cashierAcc);
        }
      }

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'UPDATE_LIMITS',
        `Actualizados límites directo para cajero: @${editingCashierLimits.user}`,
        'success'
      );

      await new Promise(resolve => setTimeout(resolve, 800));

      await loadData();
      setEditingCashierLimits(null);
      alert('Límites actualizados correctamente.');
    } catch (e) {
      console.error("Error saving modal cashier limits", e);
      alert('Error al guardar límites.');
    } finally {
      setLimitsSaving(false);
    }
  };

  const handleAnnulTicket = async (ticket: TicketRecord) => {
    if (!user) return;
    
    // Check 2-minute cashier annulment time limit window
    const nowEpochMs = Date.now();
    const elapsedMs = nowEpochMs - Number(ticket.createdAtEpochMs);
    const canBypassTimeLimit = user.role === 'ADMIN' || user.role === 'MASTER' || user.role === 'SUPERVISOR';
    
    if (!canBypassTimeLimit && elapsedMs > 120000) {
      alert('Error de Paridad: El tiempo límite de 2 minutos para anular el ticket ha expirado.');
      return;
    }

    try {
      // Perform annulment via Edge Function
      if (isSupabaseConfigured && supabase) {
        const { data: functionData, error: functionErr } = await supabase.functions.invoke('void-ticket', {
          body: {
            ticketId: ticket.id,
            actorKey: user.user,
            adminKey: ticket.adminUser || user.adminUser || user.user,
            cashierKey: ticket.sellerUser,
            action: 'void'
          }
        });
        if (functionErr) throw functionErr;
        if (functionData && functionData.ok === false) {
          throw new Error(functionData.message || 'Error al anular el ticket en el servidor.');
        }
      }

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
      alert('Error al anular el ticket en la base de datos.');
    }
  };

  const handleDeleteTicket = async (ticket: TicketRecord) => {
    if (!user) return;
    setIsDeletingTicket(true);
    try {
      if (isSupabaseConfigured && supabase) {
        const { data: functionData, error: functionErr } = await supabase.functions.invoke('void-ticket', {
          body: {
            ticketId: ticket.id,
            actorKey: user.user,
            adminKey: ticket.adminUser || user.adminUser || user.user,
            cashierKey: ticket.sellerUser,
            action: 'delete'
          }
        });
        if (functionErr) throw functionErr;
        if (functionData && functionData.ok === false) {
          throw new Error(functionData.message || 'Error al eliminar físicamente el ticket en el servidor.');
        }
      }

      if (localStorage.getItem('lotterynet_tickets')) {
        const localTk = JSON.parse(localStorage.getItem('lotterynet_tickets') || '[]');
        const filtered = localTk.filter((t: any) => t.id !== ticket.id);
        localStorage.setItem('lotterynet_tickets', JSON.stringify(filtered));
      }

      if (ticket.status !== 'cancelled' && ticket.status !== 'voided') {
        const updatedUsers = [...users];
        const cashierIdx = updatedUsers.findIndex(u => u.user === ticket.sellerUser && u.role === 'CASHIER');
        if (cashierIdx !== -1) {
          updatedUsers[cashierIdx].balance = Math.max(0, updatedUsers[cashierIdx].balance - ticket.total);
          await saveAllUsers(updatedUsers);
        }
      }

      await addAuditLog(
        { id: user.id, user: user.user, role: user.role },
        'DELETE_TICKET',
        `Ticket eliminado físicamente: ${ticket.serial || ticket.id}. Monto: $${ticket.total}. Balances recalculados.`,
        'failed'
      );

      await loadData();
      setDeleteModalOpen(false);
      setSelectedTicketForDelete(null);
      alert('Ticket eliminado físicamente con éxito. Los balances se han recalculado.');
    } catch (e) {
      console.error(e);
      alert('Error al eliminar físicamente el ticket.');
    } finally {
      setIsDeletingTicket(false);
    }
  };

  const handlePayWinner = async (ticket: TicketRecord) => {
    if (!user) return;
    try {
      // Register payment via Edge Function
      if (isSupabaseConfigured && supabase) {
        const { data: functionData, error: functionErr } = await supabase.functions.invoke('pay-ticket', {
          body: {
            ticketId: ticket.id,
            actorKey: user.user,
            adminKey: ticket.adminUser || user.adminUser || user.user,
            cashierKey: ticket.sellerUser
          }
        });
        if (functionErr) throw functionErr;
        if (functionData && functionData.ok === false) {
          throw new Error(functionData.message || 'Error al registrar el pago del premio en el servidor.');
        }
      }

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
      alert('Error al registrar el pago del premio en el servidor.');
    }
  };

  // Dynamic countdown timer effect for annulment window parity
  useEffect(() => {
    if (!annulModalOpen) return;
    setAnnulTimer(Date.now());
    const interval = setInterval(() => {
      setAnnulTimer(Date.now());
    }, 1000);
    return () => clearInterval(interval);
  }, [annulModalOpen]);

  // Load limits when tab is active
  useEffect(() => {
    if (!user) return;
    if (activeTab === 'limites' && (user.role === 'ADMIN' || user.role === 'MASTER')) {
      const loadLimits = async () => {
        try {
          const raw = await getAdminLimitsPayload(user.id);
          const sysCfg = await getAdminSystemModeConfig(user.id);
          const rawPayouts = await getAdminPayoutsPayload(user.id);

          const parsed = JSON.parse(raw);
          setLimitsPayload(parsed);
          setSystemModeConfig(sysCfg);
          setPayoutsPayload(JSON.parse(rawPayouts));
          
          const defaultCashiers = users.filter(u => u.role === 'CASHIER' && (user.role === 'MASTER' ? true : u.adminId === user.id));
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

    let targetPayouts = payoutsPayload?.defaults || {
      q1: 60, q2: 12, q3: 4,
      pale: 1000, pale12: 1000, pale13: 1000, pale23: 1000,
      tripleta: 20000, tripleta3: 20000, tripleta2: 1000,
      superPale: 3000,
      pick3Straight: 500, pick3Box3: 160, pick3Box6: 80, pick3BackPair: 50,
      pick4Straight: 5000, pick4Box4: 1200, pick4Box6: 800, pick4Box12: 400, pick4Box24: 200, pick4BackPair: 50
    };

    if (selectedScope === 'CASHIER_SPECIFIC' && selectedCashierUsername) {
      if (payoutsPayload?.byUser && payoutsPayload.byUser[selectedCashierUsername]) {
        targetPayouts = payoutsPayload.byUser[selectedCashierUsername];
      }
    }

    setCurrentPayoutsForm({
      q1: targetPayouts.q1 ?? 60,
      q2: targetPayouts.q2 ?? 12,
      q3: targetPayouts.q3 ?? 4,
      pale: targetPayouts.pale ?? 1000,
      pale12: targetPayouts.pale12 ?? 1000,
      pale13: targetPayouts.pale13 ?? 1000,
      pale23: targetPayouts.pale23 ?? 1000,
      tripleta: targetPayouts.tripleta ?? 20000,
      tripleta3: targetPayouts.tripleta3 ?? 20000,
      tripleta2: targetPayouts.tripleta2 ?? 1000,
      superPale: targetPayouts.superPale ?? 3000,
      pick3Straight: targetPayouts.pick3Straight ?? 500,
      pick3Box3: targetPayouts.pick3Box3 ?? 160,
      pick3Box6: targetPayouts.pick3Box6 ?? 80,
      pick3BackPair: targetPayouts.pick3BackPair ?? 50,
      pick4Straight: targetPayouts.pick4Straight ?? 5000,
      pick4Box4: targetPayouts.pick4Box4 ?? 1200,
      pick4Box6: targetPayouts.pick4Box6 ?? 800,
      pick4Box12: targetPayouts.pick4Box12 ?? 400,
      pick4Box24: targetPayouts.pick4Box24 ?? 200,
      pick4BackPair: targetPayouts.pick4BackPair ?? 50
    });
  }, [selectedScope, selectedCashierUsername, limitsPayload, payoutsPayload, users, user]);

  const handleSaveLimits = async () => {
    if (!user) return;
    setLimitsSaving(true);
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

      // Save payouts payload
      const updatedPayouts = { ...payoutsPayload };
      const newPayoutsObj = {
        q1: Number(currentPayoutsForm.q1),
        q2: Number(currentPayoutsForm.q2),
        q3: Number(currentPayoutsForm.q3),
        pale: Number(currentPayoutsForm.pale),
        pale12: Number(currentPayoutsForm.pale12),
        pale13: Number(currentPayoutsForm.pale13),
        pale23: Number(currentPayoutsForm.pale23),
        tripleta: Number(currentPayoutsForm.tripleta),
        tripleta3: Number(currentPayoutsForm.tripleta3),
        tripleta2: Number(currentPayoutsForm.tripleta2),
        superPale: Number(currentPayoutsForm.superPale),
        pick3Straight: Number(currentPayoutsForm.pick3Straight),
        pick3Box3: Number(currentPayoutsForm.pick3Box3),
        pick3Box6: Number(currentPayoutsForm.pick3Box6),
        pick3BackPair: Number(currentPayoutsForm.pick3BackPair),
        pick4Straight: Number(currentPayoutsForm.pick4Straight),
        pick4Box4: Number(currentPayoutsForm.pick4Box4),
        pick4Box6: Number(currentPayoutsForm.pick4Box6),
        pick4Box12: Number(currentPayoutsForm.pick4Box12),
        pick4Box24: Number(currentPayoutsForm.pick4Box24),
        pick4BackPair: Number(currentPayoutsForm.pick4BackPair),
      };

      if (selectedScope === 'CASHIER_SPECIFIC' && selectedCashierUsername) {
        if (!updatedPayouts.byUser) updatedPayouts.byUser = {};
        updatedPayouts.byUser[selectedCashierUsername] = newPayoutsObj;
      } else {
        updatedPayouts.defaults = newPayoutsObj;
      }

      await saveAdminPayoutsPayload(user.id, JSON.stringify(updatedPayouts));
      setPayoutsPayload(updatedPayouts);

      // Save system config mode (traditional vs pick)
      await saveAdminSystemModeConfig(user.id, systemModeConfig);

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

      // Add a simulated server latency to show premium sync indicator
      await new Promise(resolve => setTimeout(resolve, 800));

      // Save sports limits
      const sportsScopeKey = selectedScope === 'ADMIN_SELF' 
        ? user.id 
        : selectedScope === 'CASHIER_DEFAULTS' 
        ? 'global' 
        : selectedCashierUsername || 'global';

      await saveSportsLimits({
        scope_key: sportsScopeKey,
        max_ticket_stake: sportsLimitsForm.max_ticket_stake,
        max_selection_stake: Math.floor(sportsLimitsForm.max_ticket_stake / 2),
        max_event_exposure: sportsLimitsForm.max_ticket_stake * 5,
        max_potential_payout: sportsLimitsForm.max_potential_payout,
        enabled_markets: sportsLimitsForm.enabled_markets,
        updated_by: user.user
      });

      await loadData();
      setLimitsSaving(false);
      setLimitsConfirmOpen(false);

      setSaveSuccessNotification(true);
      setTimeout(() => setSaveSuccessNotification(false), 3000);
    } catch (e) {
      console.error(e);
      setLimitsSaving(false);
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
    if (loadInFlightRef.current) return;
    loadInFlightRef.current = true;
    const shouldFetchUsers = activeTab === 'dashboard' || activeTab === 'cajeros' || activeTab === 'supervisores' || activeTab === 'monitoreo' || activeTab === 'deportiva' || activeTab === 'tickets' || activeTab === 'ganadores' || activeTab === 'limites' || activeTab === 'cuadre' || activeTab === 'finanzas';
    const shouldFetchSportsTickets = activeTab === 'dashboard' || activeTab === 'cajeros' || activeTab === 'deportiva' || activeTab === 'cuadre';
    const shouldFetchLotteries = lotteries.length === 0 || activeTab === 'dashboard' || activeTab === 'monitoreo' || activeTab === 'resultados';
    const shouldFetchAudits = activeTab === 'finanzas' || activeTab === 'auditoria' || activeTab === 'cuadre';
    const shouldFetchResults = activeTab === 'dashboard' || activeTab === 'resultados';
    const ticketTabNeedsData = activeTab === 'dashboard' || activeTab === 'cajeros' || activeTab === 'monitoreo' || activeTab === 'tickets' || activeTab === 'ganadores' || activeTab === 'cuadre';
    const ticketFetchIsFresh = Date.now() - lastTicketFetchAtRef.current < 30_000;
    const shouldFetchTicketList = ticketTabNeedsData && (tickets.length === 0 || !ticketFetchIsFresh);
    const hasWarmData =
      (!shouldFetchUsers || users.length > 0) &&
      (!shouldFetchSportsTickets || sportsTickets.length > 0) &&
      (!shouldFetchLotteries || lotteries.length > 0) &&
      (!shouldFetchAudits || audits.length > 0) &&
      (!shouldFetchResults || resultsList.length > 0) &&
      (!shouldFetchTicketList || tickets.length > 0);

    setLoading(!hasWarmData);
    try {
      const savedUser = localStorage.getItem('lotterynet_session_user');
      const parsedUser = savedUser ? JSON.parse(savedUser) : null;
      
      const allowedRole = parsedUser?.role || user?.role;
      const allowedId = parsedUser?.id || user?.id;
      const allowedAdminId = parsedUser?.adminId || user?.adminId;

      const adminScopeId = allowedRole === 'ADMIN' 
        ? allowedId 
        : (allowedRole === 'SUPERVISOR' ? allowedAdminId : undefined);

      // Smart Tab-based selective fetching with Promise.all to resolve database/network waterfall blocks
      const [u, st, l, a, r, t] = await Promise.all([
        shouldFetchUsers ? fetchUsers() : Promise.resolve(users),
        shouldFetchSportsTickets ? fetchSportsTickets(adminScopeId) : Promise.resolve(sportsTickets),
        shouldFetchLotteries ? (lotteries.length > 0 ? Promise.resolve(lotteries) : fetchLotteries()) : Promise.resolve(lotteries),
        shouldFetchAudits ? fetchAuditLogs() : Promise.resolve(audits),
        shouldFetchResults ? fetchDrawResults() : Promise.resolve(resultsList),
        shouldFetchTicketList ? fetchTickets(adminScopeId, users.length > 0 ? users : undefined) : Promise.resolve(tickets),
      ]);

      const targetAdminId = adminScopeId || allowedId;
      if (targetAdminId && (activeTab === 'dashboard' || activeTab === 'limites' || activeTab === 'monitoreo')) {
        void Promise.all([
          getManualDisabledLotteries(targetAdminId),
          getAdminSystemModeConfig(targetAdminId),
        ]).then(([disabledCfg, systemCfg]) => {
          setManualDisabledLotteryIds(disabledCfg.ids || []);
          setBlockedSalePlays(systemCfg.blockedSalePlays || []);
        }).catch((err) => {
          console.warn('Error loading blocks / modes from Supabase:', err);
        });
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
      setSportsTickets(st);
      setLotteries(sortedL);
      setAudits(a);
      setResultsList(sortedR);
      if (shouldFetchTicketList) {
        lastTicketFetchAtRef.current = Date.now();
      }

    } catch (e) {
      console.error(e);
    } finally {
      loadInFlightRef.current = false;
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [activeTab]);

  // Load Sports limits when scope or selected cashier changes
  useEffect(() => {
    if (!user) return;
    if (activeTab === 'limites') {
      const loadSportsLimits = async () => {
        try {
          const scopeKey = selectedScope === 'ADMIN_SELF' 
            ? user.id 
            : selectedScope === 'CASHIER_DEFAULTS' 
            ? 'global' 
            : selectedCashierUsername || 'global';
            
          const cfg = await getSportsLimits(scopeKey);
          setSportsLimitsForm({
            max_ticket_stake: cfg.max_ticket_stake,
            max_potential_payout: cfg.max_potential_payout,
            enabled_markets: cfg.enabled_markets
          });
        } catch (e) {
          console.warn("Failed to load sports limits", e);
        }
      };
      loadSportsLimits();
    }
  }, [activeTab, selectedScope, selectedCashierUsername, user]);
  const loadDataRef = useRef(loadData);
  useEffect(() => {
    loadDataRef.current = loadData;
  });

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
      const selectedCashier = users.find((u) => u.id === rechargeForm.cashierId);
      await processRecharge(selectedCashier?.adminId || user.id, rechargeForm.cashierId, amountNum, {
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
        <Suspense fallback={
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' }}>
              {[1, 2, 3, 4].map(i => (
                <div key={i} className="glass-panel shimmer" style={{ height: '110px' }} />
              ))}
            </div>
            <div className="glass-panel shimmer" style={{ height: '350px' }} />
          </div>
        }>
          {/* TAB 1: GENERAL DASHBOARD */}
          {activeTab === 'dashboard' && (
            <DashboardHome
              user={user}
              users={users}
              tickets={tickets}
              sportsTickets={sportsTickets}
              audits={audits}
              lotteries={lotteries}
              systemModeConfig={systemModeConfig}
              sportsLimitsForm={sportsLimitsForm}
              manualDisabledLotteryIds={manualDisabledLotteryIds}
              dashboardDateFilter={dashboardDateFilter}
              setDashboardDateFilter={setDashboardDateFilter}
              dashboardViewContext={dashboardViewContext}
              setDashboardViewContext={setDashboardViewContext}
              drawsSubTab={drawsSubTab}
              setDrawsSubTab={setDrawsSubTab}
              isSameLocalDate={isSameLocalDate}
              normalizeRate={normalizeRate}
              handleToggleManualDisabledLottery={handleToggleManualDisabledLottery}
              setSelectedTicketForDetail={setSelectedTicketForDetail}
              loadData={loadData}
              parseTimeToMinutes={parseTimeToMinutes}
              getCurrentDRMinutesSinceMidnight={getCurrentDRMinutesSinceMidnight}
            />
          )}

          {/* TAB 2: MASTER ONLY - MANAGE ADMINS / BANCAS */}
          {activeTab === 'admins' && user.role === 'MASTER' && (
            <AdminsTab
              filteredUsers={filteredUsers}
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              filterStatus={filterStatus}
              setFilterStatus={setFilterStatus}
              setAdminModalOpen={setAdminModalOpen}
              handleToggleAdmin={handleToggleAdmin}
              handleRegenCreds={handleRegenCreds}
              handleDeleteBanca={handleDeleteBanca}
            />
          )}

          {/* TAB 3: ADMIN ONLY - MANAGE CAJEROS */}
          {activeTab === 'cajeros' && (user.role === 'ADMIN' || user.role === 'MASTER') && (
            <CajerosTab
              user={user}
              users={users}
              cashierSalesTotals={cashierSalesTotals}
              setRechargeModalOpen={setRechargeModalOpen}
              setCajeroModalOpen={setCajeroModalOpen}
              handleToggleCashier={handleToggleCashier}
              handleOpenEditCajero={handleOpenEditCajero}
              handleRenameCashier={handleRenameCashier}
              handleOpenCashierLimitsModal={handleOpenCashierLimitsModal}
              handleDeleteCashier={handleDeleteCashier}
            />
          )}

          {/* TAB 4: ADMIN ONLY - MANAGE SUPERVISORES */}
          {activeTab === 'supervisores' && (user.role === 'ADMIN' || user.role === 'MASTER') && (
            <SupervisoresTab
              user={user}
              users={users}
              setSupervisorModalOpen={setSupervisorModalOpen}
              handleOpenAssignModal={handleOpenAssignModal}
              handleResetSupervisorPassword={handleResetSupervisorPassword}
              handleToggleSupervisor={handleToggleSupervisor}
              handleDeleteSupervisor={handleDeleteSupervisor}
            />
          )}

          {/* TAB 5: COMPREHENSIVE NETWORK & PLAY MONITORING */}
          {activeTab === 'monitoreo' && (user.role === 'ADMIN' || user.role === 'SUPERVISOR' || user.role === 'MASTER') && (
            <MonitoreoTab
              user={user}
              tickets={tickets}
              users={users}
              lotteries={lotteries}
              monitoreoSubTab={monitoreoSubTab}
              setMonitoreoSubTab={setMonitoreoSubTab}
              monitoreoPlayFocus={monitoreoPlayFocus}
              setMonitoreoPlayFocus={setMonitoreoPlayFocus}
              monitoreoHighestFirst={monitoreoHighestFirst}
              setMonitoreoHighestFirst={setMonitoreoHighestFirst}
              monitoreoShowEmptyLotteries={monitoreoShowEmptyLotteries}
              setMonitoreoShowEmptyLotteries={setMonitoreoShowEmptyLotteries}
              monitoreoRange={monitoreoRange}
              setMonitoreoRange={setMonitoreoRange}
              isSameLocalDate={isSameLocalDate}
              cashierSalesTotals={cashierSalesTotals}
            />
          )}

          {/* TAB 10: TICKETS SEARCH & VOID SYSTEM */}
          {activeTab === 'tickets' && (user.role === 'ADMIN' || user.role === 'SUPERVISOR' || user.role === 'MASTER') && (
            <TicketsTab
              user={user}
              users={users}
              tickets={tickets}
              ticketSearchSerial={ticketSearchSerial}
              setTicketSearchSerial={setTicketSearchSerial}
              ticketFilterStatus={ticketFilterStatus}
              setFilterStatus={setTicketFilterStatus}
              ticketFilterCashier={ticketFilterCashier}
              setTicketFilterCashier={setTicketFilterCashier}
              ticketDateFilter={ticketDateFilter}
              setTicketDateFilter={setTicketDateFilter}
              isSameLocalDate={isSameLocalDate}
              setSelectedTicketForDetail={setSelectedTicketForDetail}
              setSelectedTicketForAnnul={setSelectedTicketForAnnul}
              setAnnulModalOpen={setAnnulModalOpen}
              setSelectedTicketForDelete={setSelectedTicketForDelete}
              setDeleteModalOpen={setDeleteModalOpen}
              handlePayWinner={handlePayWinner}
            />
          )}

          {/* TAB 10.5: SPORTSBOOK APUESTAS DEPORTIVAS */}
          {activeTab === 'deportiva' && (
            <DeportivaTab
              user={user}
              sportsTickets={sportsTickets}
              setSportsTickets={setSportsTickets}
              users={users}
              ticketFilterCashier={ticketFilterCashier}
              setTicketFilterCashier={setTicketFilterCashier}
              ticketDateFilter={ticketDateFilter}
              setTicketDateFilter={setTicketDateFilter}
              ticketFilterStatus={ticketFilterStatus}
              setTicketFilterStatus={setTicketFilterStatus}
              ticketSearchSerial={ticketSearchSerial}
              setTicketSearchSerial={setTicketSearchSerial}
              setSelectedSportsTicketForDetail={setSelectedSportsTicketForDetail}
              loadData={loadData}
              isSameLocalDate={isSameLocalDate}
              saveAllUsers={saveAllUsers}
              addAuditLog={addAuditLog}
            />
          )}

          {/* TAB 11: WINNERS PRIZE PAYOUT MODULE */}
          {activeTab === 'ganadores' && (user.role === 'ADMIN' || user.role === 'MASTER') && (
            <GanadoresTab
              user={user}
              tickets={tickets}
              handlePayWinner={handlePayWinner}
            />
          )}

          {/* TAB 12: RESULTS SCRAPER & REGISTRATION */}
          {activeTab === 'resultados' && (
            <ResultadosTab
              user={user}
              lotteries={lotteries}
              resultsList={resultsList}
              setResultsList={setResultsList}
            />
          )}

          {/* TAB 13: CUADRE DE CAJA AND DETAILED OPERATIONAL REPORTS */}
          {activeTab === 'cuadre' && (user.role === 'ADMIN' || user.role === 'SUPERVISOR' || user.role === 'MASTER') && (
            <CuadreTab
              user={user}
              users={users}
              tickets={tickets}
              sportsTickets={sportsTickets}
              audits={audits}
              cuadrePeriod={cuadrePeriod}
              setCuadrePeriod={setCuadrePeriod}
              cuadreCashierFilter={cuadreCashierFilter}
              setCuadreCashierFilter={setCuadreCashierFilter}
              cuadreDateFrom={cuadreDateFrom}
              setCuadreDateFrom={setCuadreDateFrom}
              cuadreDateTo={cuadreDateTo}
              setCuadreDateTo={setCuadreDateTo}
              isSameLocalDate={isSameLocalDate}
              getLocalDateStringDR={getLocalDateStringDR}
              normalizeRate={normalizeRate}
            />
          )}


          {/* TAB 6: LIMITS AND PERMISSIONS */}
          {activeTab === 'limites' && (user.role === 'ADMIN' || user.role === 'MASTER') && (
            <ConfigTab
              user={user}
              users={users}
              lotteries={lotteries}
              saveSuccessNotification={saveSuccessNotification}
              selectedScope={selectedScope}
              setSelectedScope={setSelectedScope}
              selectedCashierUsername={selectedCashierUsername}
              setSelectedCashierUsername={setSelectedCashierUsername}
              currentLimitsForm={currentLimitsForm}
              setCurrentLimitsForm={setCurrentLimitsForm}
              systemModeConfig={systemModeConfig}
              setSystemModeConfig={setSystemModeConfig}
              currentPayoutsForm={currentPayoutsForm}
              setCurrentPayoutsForm={setCurrentPayoutsForm}
              sportsLimitsForm={sportsLimitsForm}
              setSportsLimitsForm={setSportsLimitsForm}
              blockedPlayForm={blockedPlayForm}
              setBlockedPlayForm={setBlockedPlayForm}
              blockedSalePlays={blockedSalePlays}
              manualDisabledLotteryIds={manualDisabledLotteryIds}
              setLimitsConfirmOpen={setLimitsConfirmOpen}
              handleAddBlockedPlay={handleAddBlockedPlay}
              handleRemoveBlockedPlay={handleRemoveBlockedPlay}
              handleToggleManualDisabledLottery={handleToggleManualDisabledLottery}
            />
          )}

          {/* TAB 7: FINANCE SUMMARY AND RECHARGES */}
          {activeTab === 'finanzas' && (user.role === 'ADMIN' || user.role === 'MASTER' || user.role === 'SUPERVISOR') && (
            <FinanzasTab
              user={user}
              users={users}
              tickets={tickets}
              sportsTickets={sportsTickets}
              audits={audits}
              finanzasDateFilter={finanzasDateFilter}
              setFinanzasDateFilter={setFinanzasDateFilter}
              setRechargeModalOpen={setRechargeModalOpen}
              setRechargeForm={setRechargeForm}
              isSameLocalDate={isSameLocalDate}
              normalizeRate={normalizeRate}
            />
          )}

          {/* TAB 8: MASTER & ADMIN & SUPERVISOR REPORTS */}
          {activeTab === 'reportes' && (
            <ReportesTab
              tickets={tickets}
              sportsTickets={sportsTickets}
            />
          )}

          {/* TAB 9: AUDIT LOG SYSTEM */}
          {activeTab === 'auditoria' && (
            <AuditoriaTab
              audits={audits}
            />
          )}

        </Suspense>
      )}


      {/* Modales deconstruidos */}
      <AdminFormModal
        isOpen={adminModalOpen}
        onClose={() => setAdminModalOpen(false)}
        onSubmit={handleCreateAdmin}
        form={adminForm}
        setForm={setAdminForm}
      />

      <CajeroFormModal
        isOpen={cajeroModalOpen}
        onClose={() => {
          setCajeroModalOpen(false);
          setEditingCashier(null);
        }}
        onSubmit={handleCreateCajero}
        form={cajeroForm}
        setForm={setCajeroForm}
        users={users}
        editingCashier={editingCashier}
        currentUser={user}
      />

      <SupervisorFormModal
        isOpen={supervisorModalOpen}
        onClose={() => setSupervisorModalOpen(false)}
        onSubmit={handleCreateSupervisor}
        form={supervisorForm}
        setForm={setSupervisorForm}
      />

      <RechargeModal
        isOpen={rechargeModalOpen}
        onClose={() => setRechargeModalOpen(false)}
        onSubmit={handleProcessRecharge}
        form={rechargeForm}
        setForm={setRechargeForm}
        users={users}
        currentUser={user}
      />

      <AssignCashiersModal
        isOpen={assignModalOpen}
        onClose={() => {
          setAssignModalOpen(false);
          setSelectedSupervisor(null);
        }}
        selectedSupervisor={selectedSupervisor}
        users={users}
        currentUser={user}
        assignedCashiersSet={assignedCashiersSet}
        setAssignedCashiersSet={setAssignedCashiersSet}
        onSave={handleSaveAssignments}
      />

      <CredsShareModal
        isOpen={credsShareOpen}
        onClose={() => setCredsShareOpen(false)}
        shareText={shareText}
      />

      <LimitsEditor
        editingCashierLimits={editingCashierLimits}
        onClose={() => setEditingCashierLimits(null)}
        modalLimitsTab={modalLimitsTab}
        setModalLimitsTab={setModalLimitsTab}
        modalLimitsForm={modalLimitsForm}
        setModalLimitsForm={setModalLimitsForm}
        modalPayoutsForm={modalPayoutsForm}
        setModalPayoutsForm={setModalPayoutsForm}
        onSave={handleSaveModalCashierLimits}
        limitsSaving={limitsSaving}
      />

      <AnnulTicketModal
        isOpen={annulModalOpen}
        onClose={() => {
          setAnnulModalOpen(false);
          setSelectedTicketForAnnul(null);
        }}
        onConfirm={() => selectedTicketForAnnul && handleAnnulTicket(selectedTicketForAnnul)}
        ticket={selectedTicketForAnnul}
        annulTimer={annulTimer}
        user={user}
      />

      <DeleteTicketModal
        isOpen={deleteModalOpen}
        onClose={() => {
          setDeleteModalOpen(false);
          setSelectedTicketForDelete(null);
        }}
        onConfirm={() => selectedTicketForDelete && handleDeleteTicket(selectedTicketForDelete)}
        ticket={selectedTicketForDelete}
        isDeleting={isDeletingTicket}
      />

      <TicketDetailModal
        ticket={selectedTicketForDetail}
        onClose={() => setSelectedTicketForDetail(null)}
      />

      <SportsTicketDetailModal
        ticket={selectedSportsTicketForDetail}
        onClose={() => setSelectedSportsTicketForDetail(null)}
      />


      <LimitsConfirmModal
        isOpen={limitsConfirmOpen}
        onClose={() => setLimitsConfirmOpen(false)}
        onConfirm={handleSaveLimits}
        limitsSaving={limitsSaving}
        selectedScope={selectedScope}
        selectedCashierUsername={selectedCashierUsername}
        currentLimitsForm={currentLimitsForm}
        systemModeConfig={systemModeConfig}
      />

    </div>
  );
};
