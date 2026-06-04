# Supabase Cron Backup - results refresh

Date: 2026-05-31
Project: unhoulkujbtsypccpirc

This backup was created before pausing the noisy internal Supabase cron that was invoking `results-server-refresh` every minute.

## Cron job before change

- jobid: 3
- jobname: lotterynet-results-server-refresh
- schedule: `* * * * *`
- active: true
- database: postgres
- username: postgres
- node: localhost:5432

## Why it was paused

The job was running once per minute and the Edge Function logs showed repeated `403` responses. Supabase counts Edge Function invocations even when the response is an error, so this job alone can generate about 43,200 invocations per month.

Render cron remains the active results worker.

## Re-enable command if needed

```sql
select cron.alter_job(
  job_id := 3,
  active := true
);
```

## Pause command used

```sql
select cron.alter_job(
  job_id := 3,
  active := false
);
```
