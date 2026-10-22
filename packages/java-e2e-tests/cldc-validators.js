/**
 * CLDC 测试用例的自定义断言策略
 *
 * 对于多线程测试，输出顺序可能因线程调度而不同，
 * 因此使用逻辑断言而非精确快照比对。
 *
 * 策略模式：key 为用例名，value 为断言函数
 * 断言函数接收实际输出，不匹配时抛出 Error
 */

function parseLines(output) {
  return output
    .trim()
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l);
}

function assertContains(lines, required, context) {
  const missing = required.filter((r) => !lines.includes(r));
  if (missing.length > 0) {
    throw new Error(
      `${context}: Missing required lines: ${missing.join(', ')}\nActual output: ${lines.join(', ')}`,
    );
  }
}

function assertOrder(lines, before, after, context) {
  const beforeIdx = lines.indexOf(before);
  const afterIdx = lines.indexOf(after);
  if (beforeIdx === -1) {
    throw new Error(`${context}: "${before}" not found`);
  }
  if (afterIdx === -1) {
    throw new Error(`${context}: "${after}" not found`);
  }
  if (beforeIdx >= afterIdx) {
    throw new Error(
      `${context}: "${before}" (index ${beforeIdx}) should come before "${after}" (index ${afterIdx})`,
    );
  }
}

function assertSequence(lines, sequence, context) {
  for (let i = 0; i < sequence.length - 1; i++) {
    assertOrder(lines, sequence[i], sequence[i + 1], context);
  }
}

/**
 * WaitNotifySuite:
 * - T1 和 T2 并发启动
 * - T1 调用 wait()，T2 调用 notifyAll()
 *
 * 逻辑规则：
 * 1. 所有必需行必须存在
 * 2. T1-start 必须在 T1-after-wait 之前（T1 自身的顺序）
 * 3. T2-start 必须在 T2-end 之前（T2 自身的顺序）
 */
function validateWaitNotifySuite(output) {
  const lines = parseLines(output);
  const required = ['T1-start', 'T2-start', 'T2-end', 'T1-after-wait'];

  assertContains(lines, required, 'WaitNotifySuite');
  assertOrder(
    lines,
    'T1-start',
    'T1-after-wait',
    'WaitNotifySuite: T1 sequence',
  );
  assertOrder(lines, 'T2-start', 'T2-end', 'WaitNotifySuite: T2 sequence');
}

/**
 * WaitNotifyOneSuite:
 * - 两个 W 线程 (T1, T2) 调用 wait(5) 带超时
 * - 一个 N 线程调用 notify()（只唤醒一个）
 *
 * 逻辑规则：
 * 1. 所有必需行必须存在
 * 2. 每个线程的 start 必须在其 after-wait 之前
 * 3. N-start 必须在 N-end 之前
 */
function validateWaitNotifyOneSuite(output) {
  const lines = parseLines(output);
  const required = [
    'T1-start',
    'T2-start',
    'N-start',
    'N-end',
    'T1-after-wait',
    'T2-after-wait',
  ];

  assertContains(lines, required, 'WaitNotifyOneSuite');
  assertOrder(
    lines,
    'T1-start',
    'T1-after-wait',
    'WaitNotifyOneSuite: T1 sequence',
  );
  assertOrder(
    lines,
    'T2-start',
    'T2-after-wait',
    'WaitNotifyOneSuite: T2 sequence',
  );
  assertOrder(lines, 'N-start', 'N-end', 'WaitNotifyOneSuite: N sequence');
}

/**
 * WaitNotifyNanosSuite:
 * - T1 调用 wait(1, 100000) 带纳秒精度超时
 * - T2 调用 notifyAll()
 *
 * 逻辑规则：
 * 1. 所有必需行必须存在
 * 2. T1-start 必须在 T1-after-wait 之前
 * 3. T2-start 必须在 T2-end 之前
 */
function validateWaitNotifyNanosSuite(output) {
  const lines = parseLines(output);
  const required = ['T1-start', 'T2-start', 'T2-end', 'T1-after-wait'];

  assertContains(lines, required, 'WaitNotifyNanosSuite');
  assertOrder(
    lines,
    'T1-start',
    'T1-after-wait',
    'WaitNotifyNanosSuite: T1 sequence',
  );
  assertOrder(lines, 'T2-start', 'T2-end', 'WaitNotifyNanosSuite: T2 sequence');
}

/**
 * WaitNotifyMinimalSuite:
 * - Waiter 线程获取锁后 wait
 * - Notifier 线程 sleep 10ms 后 notify
 * - 由于 sleep，顺序应该是确定的
 *
 * 逻辑规则：
 * 1. Main 序列：starting -> finished
 * 2. Waiter 序列：acquired lock -> calling wait -> woke up -> done
 * 3. Notifier 序列：acquired lock -> calling notify -> called notify -> released lock
 * 4. Waiter calling wait 应在 Notifier acquired lock 之前（Notifier sleep 10ms）
 */
function validateWaitNotifyMinimalSuite(output) {
  const lines = parseLines(output);
  const required = [
    'Main: starting threads',
    'Waiter: acquired lock',
    'Waiter: calling wait',
    'Notifier: acquired lock',
    'Notifier: calling notify',
    'Notifier: called notify',
    'Notifier: released lock',
    'Waiter: woke up from wait',
    'Waiter: done',
    'Main: all threads finished',
  ];

  assertContains(lines, required, 'WaitNotifyMinimalSuite');

  if (lines[0] !== 'Main: starting threads') {
    throw new Error(
      `WaitNotifyMinimalSuite: First line should be "Main: starting threads", got "${lines[0]}"`,
    );
  }

  if (lines[lines.length - 1] !== 'Main: all threads finished') {
    throw new Error(
      `WaitNotifyMinimalSuite: Last line should be "Main: all threads finished", got "${lines[lines.length - 1]}"`,
    );
  }

  assertSequence(
    lines,
    [
      'Waiter: acquired lock',
      'Waiter: calling wait',
      'Waiter: woke up from wait',
      'Waiter: done',
    ],
    'WaitNotifyMinimalSuite: Waiter sequence',
  );

  assertSequence(
    lines,
    [
      'Notifier: acquired lock',
      'Notifier: calling notify',
      'Notifier: called notify',
      'Notifier: released lock',
    ],
    'WaitNotifyMinimalSuite: Notifier sequence',
  );

  assertOrder(
    lines,
    'Waiter: calling wait',
    'Notifier: acquired lock',
    'WaitNotifyMinimalSuite: Waiter should call wait before Notifier acquires lock',
  );
  assertOrder(
    lines,
    'Notifier: released lock',
    'Waiter: woke up from wait',
    'WaitNotifyMinimalSuite: Waiter should wake up after Notifier releases lock',
  );
}

/**
 * TimeSliceFairnessSuite:
 * - 3 个 Worker 线程并发启动
 * - 每个 Worker 执行 100 次循环，累加各自的计数器
 * - main 等待所有 Worker 完成后打印计数器
 *
 * 逻辑规则：
 * 1. 所有 "Worker X done" 必须存在（3 个线程都完成）
 * 2. Counter1/2/3 都必须等于 100（每个 Worker 执行了 100 次）
 * 3. "Worker X done" 可以是任意顺序（线程调度不确定）
 * 4. Counter 输出必须在所有 Worker done 之后（main 等待 join）
 */
function validateTimeSliceFairnessSuite(output) {
  const lines = parseLines(output);

  const workerDoneLines = ['Worker 1 done', 'Worker 2 done', 'Worker 3 done'];
  assertContains(
    lines,
    workerDoneLines,
    'TimeSliceFairnessSuite: Worker completion',
  );

  const counterLines = ['Counter1: 100', 'Counter2: 100', 'Counter3: 100'];
  assertContains(lines, counterLines, 'TimeSliceFairnessSuite: Counter values');

  const lastWorkerDoneIdx = Math.max(
    lines.indexOf('Worker 1 done'),
    lines.indexOf('Worker 2 done'),
    lines.indexOf('Worker 3 done'),
  );
  const firstCounterIdx = Math.min(
    lines.indexOf('Counter1: 100'),
    lines.indexOf('Counter2: 100'),
    lines.indexOf('Counter3: 100'),
  );

  if (lastWorkerDoneIdx >= firstCounterIdx) {
    throw new Error(
      'TimeSliceFairnessSuite: All "Worker X done" should come before Counter outputs (main waits for join). ' +
        `Got: lastWorkerDone@${lastWorkerDoneIdx}, firstCounter@${firstCounterIdx}`,
    );
  }
}

function validateAccessControllerSystemClassSuite(output) {
  const lines = parseLines(output);
  const expected = [
    '=== checkPermission_basic ===',
    'basic/p1_exception = java.security.AccessControlException',
    'basic/p2_exception = java.security.AccessControlException',
    '=== checkPermission_null_and_multiple_calls ===',
    'nullArg/exception = java.lang.NullPointerException',
    'loop/first_exception = java.security.AccessControlException',
    'loop/successCount = 0',
    'loop/failCount = 20',
  ];

  if (lines.join('\n') !== expected.join('\n')) {
    throw new Error(
      'AccessControllerSystemClassSuite: output mismatch.\n' +
        'Expected:\n' +
        expected.join('\n') +
        '\n\nActual:\n' +
        lines.join('\n'),
    );
  }
}

/**
 * CLDC 自定义断言策略 Map
 * key: 用例名 (不含 .jar 后缀)
 * value: 断言函数，接收实际输出，不匹配时抛出 Error
 */
export const cldcValidators = {
  WaitNotifySuite: validateWaitNotifySuite,
  WaitNotifyOneSuite: validateWaitNotifyOneSuite,
  WaitNotifyNanosSuite: validateWaitNotifyNanosSuite,
  WaitNotifyMinimalSuite: validateWaitNotifyMinimalSuite,
  TimeSliceFairnessSuite: validateTimeSliceFairnessSuite,
  AccessControllerSystemClassSuite: validateAccessControllerSystemClassSuite,
};
