import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const read = (path) => readFile(new URL(`../../${path}`, import.meta.url), 'utf8');

test('only a confirmed unpaid winner with a positive prize is locally notifiable', async () => {
  const source = await read('app/src/main/java/com/lotterynet/pro/core/notification/WinningTicketNotifier.kt');

  assert.match(source, /CONFIRMED_WINNER_STATUSES/);
  assert.match(source, /ticket\.totalPrize\s*>\s*0\.0/);
  assert.doesNotMatch(source, /status\.equals\("winner", true\) \|\| ticket\.totalPrize > 0\.0/);
});

test('current FCM token is registered after a valid session exists', async () => {
  const registrar = await read('app/src/main/java/com/lotterynet/pro/core/push/PushTokenRegistrar.kt');
  const login = await read('app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt');

  assert.match(registrar, /fun registerCurrentToken\(\)/);
  assert.match(registrar, /FirebaseMessaging\.getInstance\(\)\.token/);
  assert.match(login, /PushTokenRegistrar\(this\)\.registerCurrentToken\(\)/);
});

test('push catch-up is expedited and remote merges evaluate winner transitions', async () => {
  const scheduler = await read('app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpScheduler.kt');
  const service = await read('app/src/main/java/com/lotterynet/pro/core/push/LotteryNetFirebaseMessagingService.kt');
  const repository = await read('app/src/main/java/com/lotterynet/pro/core/storage/LocalSalesRepository.kt');

  assert.match(scheduler, /OutOfQuotaPolicy\.RUN_AS_NON_EXPEDITED_WORK_REQUEST/);
  assert.match(scheduler, /fun enqueuePushTriggered/);
  assert.match(service, /enqueuePushTriggered/);
  assert.match(repository, /notifyNewWinningTransitions/);
});
