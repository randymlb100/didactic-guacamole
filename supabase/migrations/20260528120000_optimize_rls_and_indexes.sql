begin;

-- 1. Optimize Auditoria RLS (Replacing subquery (select auth.uid()) with direct auth.uid())
drop policy if exists audit_select_scope on public.auditoria;
create policy audit_select_scope on public.auditoria
for select to public
using (public.ln_is_master() or actor_id = auth.uid());

-- 2. Optimize Balances RLS (Replacing subquery (select auth.uid()) with direct auth.uid())
drop policy if exists balances_select_scope on public.balances;
create policy balances_select_scope on public.balances
for select to public
using (
  public.ln_is_master()
  or owner_id = auth.uid()
  or exists (
    select 1
    from public.profiles p
    where p.id = balances.owner_id
      and public.ln_same_admin_network(coalesce(p.admin_owner_id, p.id))
  )
);

-- 3. Add Descending Date Indexes for Queries Sorting by Date/Time
create index if not exists idx_tickets_created_at_desc on public.tickets (created_at desc);
create index if not exists idx_auditoria_created_at_desc on public.auditoria (created_at desc);

commit;
