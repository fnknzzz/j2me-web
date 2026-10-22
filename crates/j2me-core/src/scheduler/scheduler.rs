use serde::{Deserialize, Serialize};
use std::cmp::Reverse;
use std::collections::{BinaryHeap, HashMap, VecDeque};

use super::monitor::CoopMonitor;
use super::virtual_thread::{
    JavaThreadState, ResumeAction, StepResult, ThreadId, VirtualThread, YieldReason,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Scheduler {
    pub threads: HashMap<ThreadId, VirtualThread>,
    pub run_queue: VecDeque<ThreadId>,
    pub monitors: HashMap<u32, CoopMonitor>,
    pub wait_sets: HashMap<u32, VecDeque<ThreadId>>,
    pub sleep_queue: BinaryHeap<Reverse<(u64, ThreadId)>>,
    pub join_waiters: HashMap<ThreadId, Vec<ThreadId>>,
    pub virtual_time_ms: u64,
    pub next_thread_id: u32,
    pub min_virtual_runtime: f64,
    pub speed_multiplier: f64,
    /// Host monotonic time corresponding to `effective_time_base_ms`.
    pub host_time_base_ms: u64,
    /// Effective time when `host_time_base_ms` was recorded.
    pub effective_time_base_ms: u64,
    /// Accumulated time offset from step(time_ms) calls.
    /// Added to host_mono_time_millis() to allow callers to fast-forward time.
    pub time_offset_ms: u64,
}

impl Scheduler {
    pub fn new() -> Self {
        let now = crate::call_host_mono_time_millis();
        Self {
            threads: HashMap::new(),
            run_queue: VecDeque::new(),
            monitors: HashMap::new(),
            wait_sets: HashMap::new(),
            sleep_queue: BinaryHeap::new(),
            join_waiters: HashMap::new(),
            virtual_time_ms: 0,
            next_thread_id: 1,
            min_virtual_runtime: 0.0,
            speed_multiplier: 1.0,
            host_time_base_ms: now,
            effective_time_base_ms: now,
            time_offset_ms: 0,
        }
    }

    pub fn create_thread(&mut self, name: String, priority: u8, daemon: bool) -> ThreadId {
        let id = self.next_thread_id;
        self.next_thread_id += 1;

        let mut thread = VirtualThread::new(id, name, priority, daemon);
        thread.state = JavaThreadState::Runnable;
        thread.virtual_runtime = self.min_virtual_runtime;

        self.threads.insert(id, thread);
        self.run_queue.push_back(id);
        id
    }

    pub fn step(&mut self, time_advance_ms: u64) -> bool {
        self.time_offset_ms += time_advance_ms;

        self.wake_expired_sleepers();

        if let Some(tid) = self.pick_next_thread() {
            let thread = self.threads.get_mut(&tid).unwrap();
            thread.state = JavaThreadState::Runnable;

            let _result = StepResult::Yielded(YieldReason::TimeSlice);

            self.run_queue.push_back(tid);
        }

        self.has_alive_threads()
    }

    /// Returns the effective "now" timestamp: real host time + accumulated offset.
    pub fn effective_now(&self) -> u64 {
        let host_now = crate::call_host_mono_time_millis();
        let elapsed = host_now.saturating_sub(self.host_time_base_ms);
        self.effective_time_base_ms
            .saturating_add(elapsed)
            .saturating_add(self.time_offset_ms)
    }

    pub fn rebase_effective_now_to_current_host(&mut self, effective_now: u64) {
        self.host_time_base_ms = crate::call_host_mono_time_millis();
        self.effective_time_base_ms = effective_now.saturating_sub(self.time_offset_ms);
    }

    pub fn pick_next_thread(&mut self) -> Option<ThreadId> {
        // Remove any non-runnable threads from the run queue
        self.run_queue.retain(|&tid| {
            self.threads
                .get(&tid)
                .map_or(false, |t| t.state == JavaThreadState::Runnable)
        });

        if self.run_queue.is_empty() {
            return None;
        }

        let mut best_idx = 0;
        let mut best_vrt = f64::MAX;
        for (i, &tid) in self.run_queue.iter().enumerate() {
            if let Some(t) = self.threads.get(&tid) {
                if t.virtual_runtime < best_vrt {
                    best_vrt = t.virtual_runtime;
                    best_idx = i;
                }
            }
        }

        let tid = self.run_queue.remove(best_idx);

        // Update min_virtual_runtime so new threads start at a fair vruntime
        if best_vrt > self.min_virtual_runtime {
            self.min_virtual_runtime = best_vrt;
        }

        tid
    }

    /// Advance the time offset so that the next sleep deadline expires immediately.
    pub fn advance_time_to_next_deadline(&mut self) {
        if let Some(&Reverse((deadline, _))) = self.sleep_queue.peek() {
            let now = self.effective_now();
            if deadline > now {
                self.time_offset_ms += deadline - now;
            }
        }
    }

    pub fn wake_expired_sleepers(&mut self) {
        let now = self.effective_now();
        while let Some(&Reverse((deadline, tid))) = self.sleep_queue.peek() {
            if deadline > now {
                break;
            }
            self.sleep_queue.pop();
            if let Some(thread) = self.threads.get_mut(&tid) {
                if thread.state != JavaThreadState::TimedWaiting {
                    continue;
                }
                // Remove from wait_sets (timeout expired, no longer waiting on the object)
                for ws in self.wait_sets.values_mut() {
                    ws.retain(|&t| t != tid);
                }
                // If thread has a ReenterAfterWait action, set it to Blocked
                // so the step() loop handles monitor re-entry properly.
                // Do NOT add to run_queue — the blocked handler in step() will do that.
                if thread.resume_action.is_some() {
                    thread.state = JavaThreadState::Blocked;
                } else {
                    thread.state = JavaThreadState::Runnable;
                    self.run_queue.push_back(tid);
                }
            }
        }
    }

    fn set_sleep_deadline(&mut self, tid: ThreadId, deadline: u64) {
        self.sleep_queue
            .retain(|&Reverse((_, queued_tid))| queued_tid != tid);
        self.sleep_queue.push(Reverse((deadline, tid)));
    }

    pub fn cancel_sleep_deadline(&mut self, tid: ThreadId) {
        self.sleep_queue
            .retain(|&Reverse((_, queued_tid))| queued_tid != tid);
    }

    pub fn dispatch_result(&mut self, tid: ThreadId, result: StepResult) {
        match result {
            StepResult::Yielded(YieldReason::TimeSlice) => {
                if !self.run_queue.contains(&tid) {
                    self.run_queue.push_back(tid);
                }
            }
            StepResult::Yielded(YieldReason::Sleep { deadline_ms }) => {
                if let Some(thread) = self.threads.get_mut(&tid) {
                    thread.state = JavaThreadState::TimedWaiting;
                }
                self.set_sleep_deadline(tid, deadline_ms);
            }
            StepResult::Yielded(YieldReason::NativeEventWait) => {
                // Thread waits indefinitely until push_native_event wakes it.
                if let Some(thread) = self.threads.get_mut(&tid) {
                    thread.state = JavaThreadState::Waiting;
                }
            }
            StepResult::Yielded(YieldReason::MonitorBlocked { obj_id }) => {
                if let Some(thread) = self.threads.get_mut(&tid) {
                    thread.state = JavaThreadState::Blocked;
                    thread.resume_action = Some(ResumeAction::RetryMonitorEnter { obj_id });
                }
                // Don't add to scheduler.monitors — vm.monitors is the source of truth.
                // The blocked handler in step() checks vm.monitors.
            }
            StepResult::Yielded(YieldReason::Wait {
                obj_id,
                timeout,
                saved_recursion,
            }) => {
                // deadline=0 means consumed_pending: the thread consumed a pending
                // notification and must immediately re-enter the monitor. Skip the
                // sleep_queue (wake_expired_sleepers only runs at step-start) and go
                // straight to Blocked+ReenterAfterWait so process_blocked_threads()
                // can handle it in the same step.
                if timeout == Some(0) {
                    if let Some(thread) = self.threads.get_mut(&tid) {
                        thread.state = JavaThreadState::Blocked;
                        thread.resume_action = Some(ResumeAction::ReenterAfterWait {
                            obj_id,
                            saved_recursion,
                        });
                    }
                    if !self.run_queue.contains(&tid) {
                        self.run_queue.push_back(tid);
                    }
                } else {
                    if let Some(thread) = self.threads.get_mut(&tid) {
                        thread.state = if timeout.is_some() {
                            JavaThreadState::TimedWaiting
                        } else {
                            JavaThreadState::Waiting
                        };
                        thread.resume_action = Some(ResumeAction::ReenterAfterWait {
                            obj_id,
                            saved_recursion,
                        });
                    }
                    self.wait_sets
                        .entry(obj_id)
                        .or_insert_with(VecDeque::new)
                        .push_back(tid);
                    if let Some(deadline) = timeout {
                        self.set_sleep_deadline(tid, deadline);
                    }
                }
            }
            StepResult::Yielded(YieldReason::Join { target_tid }) => {
                if let Some(thread) = self.threads.get_mut(&tid) {
                    thread.state = JavaThreadState::Waiting;
                }
                self.join_waiters
                    .entry(target_tid)
                    .or_insert_with(Vec::new)
                    .push(tid);
            }
            StepResult::Yielded(YieldReason::Terminated) | StepResult::ThreadFinished => {
                self.on_thread_finished(tid);
            }
            StepResult::Yielded(YieldReason::AsyncWait { .. }) => {
                if let Some(thread) = self.threads.get_mut(&tid) {
                    thread.state = JavaThreadState::Waiting;
                }
                // Thread is parked — not in any queue.
                // JS side will call jvm_resolve_async to wake it.
            }
            StepResult::Yielded(YieldReason::Exit) => {
                // System.exit() — terminate all threads
                let all_tids: Vec<ThreadId> = self.threads.keys().copied().collect();
                for t in all_tids {
                    self.on_thread_finished(t);
                }
            }
        }
    }

    fn on_thread_finished(&mut self, tid: ThreadId) {
        if let Some(thread) = self.threads.get_mut(&tid) {
            thread.state = JavaThreadState::Terminated;
        }

        if let Some(waiters) = self.join_waiters.remove(&tid) {
            for waiter_tid in waiters {
                if let Some(waiter) = self.threads.get_mut(&waiter_tid) {
                    waiter.state = JavaThreadState::Runnable;
                    waiter.resume_action = None;
                }
                self.run_queue.push_back(waiter_tid);
            }
        }
    }

    pub fn has_alive_threads(&self) -> bool {
        self.threads
            .values()
            .any(|t| !t.daemon && !matches!(t.state, JavaThreadState::Terminated))
    }

    pub fn thread_count(&self) -> usize {
        self.threads.len()
    }

    pub fn runnable_count(&self) -> usize {
        self.run_queue.len()
    }
}

impl Default for Scheduler {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_thread() {
        let mut sched = Scheduler::new();
        let tid = sched.create_thread("main".into(), 5, false);
        assert_eq!(tid, 1);
        assert_eq!(sched.thread_count(), 1);
        assert_eq!(sched.runnable_count(), 1);
        assert!(sched.has_alive_threads());
    }

    #[test]
    fn test_daemon_only_exit() {
        let mut sched = Scheduler::new();
        let tid = sched.create_thread("daemon".into(), 5, true);
        assert!(!sched.has_alive_threads());

        sched.threads.get_mut(&tid).unwrap().daemon = false;
        assert!(sched.has_alive_threads());
    }

    #[test]
    fn test_sleep_wake() {
        let mut sched = Scheduler::new();
        let tid = sched.create_thread("worker".into(), 5, false);

        sched.run_queue.pop_front();
        // In non-WASM tests, host_mono_time_millis() returns 0.
        // Use deadline=0 so the sleeper is immediately woken.
        sched.dispatch_result(
            tid,
            StepResult::Yielded(YieldReason::Sleep { deadline_ms: 0 }),
        );
        assert_eq!(sched.runnable_count(), 0);

        sched.step(0);
        assert!(sched.runnable_count() >= 1);
    }

    #[test]
    fn test_replacing_timed_wait_deadline_discards_stale_entry() {
        let mut sched = Scheduler::new();
        let tid = sched.create_thread("worker".into(), 5, false);
        sched.run_queue.clear();

        sched.dispatch_result(
            tid,
            StepResult::Yielded(YieldReason::Wait {
                obj_id: 1,
                timeout: Some(100),
                saved_recursion: 1,
            }),
        );
        sched.dispatch_result(
            tid,
            StepResult::Yielded(YieldReason::Wait {
                obj_id: 1,
                timeout: Some(200),
                saved_recursion: 1,
            }),
        );

        assert_eq!(sched.sleep_queue.len(), 1);
        assert_eq!(sched.sleep_queue.peek(), Some(&Reverse((200, tid))));
    }

    #[test]
    fn test_join_wakeup() {
        let mut sched = Scheduler::new();
        let t1 = sched.create_thread("t1".into(), 5, false);
        let t2 = sched.create_thread("t2".into(), 5, false);

        sched.run_queue.retain(|&id| id != t1);
        sched.dispatch_result(
            t1,
            StepResult::Yielded(YieldReason::Join { target_tid: t2 }),
        );
        assert!(!sched.run_queue.contains(&t1));

        sched.run_queue.retain(|&id| id != t2);
        sched.dispatch_result(t2, StepResult::ThreadFinished);

        assert!(sched.run_queue.contains(&t1));
    }
}
