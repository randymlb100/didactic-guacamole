create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'lotterynet_kv'
    ) then
        alter publication supabase_realtime add table public.lotterynet_kv;
    end if;
end;
$$;

create or replace function public.install_results_server_refresh_cron()
returns void
language plpgsql
security definer
set search_path = public, extensions, vault
as $$
declare
    has_project_url boolean;
    has_publishable_key boolean;
    has_results_secret boolean;
begin
    select exists(select 1 from vault.decrypted_secrets where name = 'project_url') into has_project_url;
    select exists(select 1 from vault.decrypted_secrets where name = 'publishable_key') into has_publishable_key;
    select exists(select 1 from vault.decrypted_secrets where name = 'lotterynet_results_cron_secret') into has_results_secret;

    if not (has_project_url and has_publishable_key and has_results_secret) then
        raise exception 'Missing Vault secrets. Required: project_url, publishable_key, lotterynet_results_cron_secret';
    end if;

    perform cron.unschedule(jobid)
    from cron.job
    where jobname = 'lotterynet-results-server-refresh';

    perform cron.schedule(
        'lotterynet-results-server-refresh',
        '* * * * *',
        $job$
        select
            net.http_post(
                url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url') || '/functions/v1/results-server-refresh',
                headers := jsonb_build_object(
                    'Content-Type', 'application/json',
                    'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'publishable_key'),
                    'x-lotterynet-results-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'lotterynet_results_cron_secret')
                ),
                body := jsonb_build_object('date', to_char((now() at time zone 'America/Santo_Domingo')::date, 'DD-MM-YYYY'))
            ) as request_id;
        $job$
    );
end;
$$;

revoke all on function public.install_results_server_refresh_cron() from anon, authenticated;
