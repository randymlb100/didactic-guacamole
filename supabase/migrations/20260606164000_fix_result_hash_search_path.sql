begin;

alter function public.lotterynet_result_draw_stable_hash(
  text,
  text,
  text,
  text,
  text,
  text
)
set search_path = public, pg_temp;

commit;

-- Rollback:
-- alter function public.lotterynet_result_draw_stable_hash(text, text, text, text, text, text) reset search_path;
