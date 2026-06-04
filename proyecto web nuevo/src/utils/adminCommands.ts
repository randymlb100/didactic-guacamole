import type { UserAccount } from '../types';
import { getValidAccessToken } from './authSession';
import { supabase } from './supabaseClient';

export type AdminUserCommandAction =
  | 'toggle_bank'
  | 'delete_bank'
  | 'regenerate_bank_credentials'
  | 'reset_user_password'
  | 'update_user_commission'
  | 'assign_supervisor_cashiers';

export interface AdminUserCommandResponse<TData = any> {
  ok: boolean;
  message: string;
  data?: TData;
  auditId?: string;
}

export async function runAdminUserCommand<TData = any>(
  actor: Pick<UserAccount, 'id' | 'user' | 'role'>,
  action: AdminUserCommandAction,
  targetId: string,
  payload: Record<string, unknown> = {}
): Promise<AdminUserCommandResponse<TData>> {
  if (!supabase) {
    throw new Error('Supabase no esta configurado para ejecutar la accion administrativa.');
  }

  const accessToken = getValidAccessToken();
  if (!accessToken) {
    throw new Error('Sesion Supabase requerida. Vuelve a iniciar sesion para sincronizar acciones administrativas.');
  }

  const { data, error } = await supabase.functions.invoke('admin-user-command', {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    body: {
      action,
      actorId: actor.id || actor.user,
      actorRole: actor.role,
      targetId,
      payload,
    },
  });

  if (error) {
    throw new Error(error.message || 'No se pudo ejecutar la accion administrativa.');
  }

  if (!data?.ok) {
    throw new Error(data?.message || 'No se pudo ejecutar la accion administrativa.');
  }

  return data as AdminUserCommandResponse<TData>;
}
