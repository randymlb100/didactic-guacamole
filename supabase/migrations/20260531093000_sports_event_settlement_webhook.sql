-- Webhook trigger to automatically settle sports tickets when a sports event status updates to 'final'.
-- This utilizes Supabase's net extension to invoke the settle-sports-tickets Deno Edge Function asynchronously.

create or replace function public.tr_sports_event_settlement()
returns trigger as $$
declare
    func_url text;
    service_role_key text;
begin
    -- Only trigger when status updates to 'final'
    if (new.status = 'final' and (old.status is null or old.status IS DISTINCT FROM 'final')) then
        -- Construct the function URL using Supabase settings or fallback to localhost during local dev
        func_url := coalesce(
            current_setting('vault.supabase_url', true),
            'http://localhost:54321'
        ) || '/functions/v1/settle-sports-tickets';
        
        -- Get the service role key from the vault or settings if available
        service_role_key := coalesce(
            current_setting('vault.service_role_key', true),
            ''
        );
        
        -- Execute asynchronous POST HTTP request using the native pg_net extension
        perform net.http_post(
            url := func_url,
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || service_role_key
            ),
            body := jsonb_build_object('eventId', new.id)
        );
    end if;
    return new;
end;
$$ language plpgsql security definer;

-- Create the trigger
create or replace trigger sports_event_final_settle_trigger
after update on public.sports_events
for each row
execute function public.tr_sports_event_settlement();
