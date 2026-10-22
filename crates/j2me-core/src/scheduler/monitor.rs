use super::virtual_thread::ThreadId;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, VecDeque};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CoopMonitor {
    pub owner: Option<ThreadId>,
    pub recursion: u32,
    pub wait_set: VecDeque<ThreadId>,
    pub blocked_queue: VecDeque<ThreadId>,
    saved_recursions: HashMap<ThreadId, u32>,
    /// Pending notifications not yet consumed by a wait().
    /// In cooperative scheduling, notify/notifyAll can fire before the target
    /// thread enters wait(). This counter lets wait() consume the signal immediately.
    pub pending_notifies: u32,
}

impl CoopMonitor {
    pub fn new() -> Self {
        Self {
            owner: None,
            recursion: 0,
            wait_set: VecDeque::new(),
            blocked_queue: VecDeque::new(),
            saved_recursions: HashMap::new(),
            pending_notifies: 0,
        }
    }

    pub fn is_active(&self) -> bool {
        self.owner.is_some()
            || self.recursion != 0
            || !self.wait_set.is_empty()
            || !self.blocked_queue.is_empty()
            || !self.saved_recursions.is_empty()
            || self.pending_notifies != 0
    }

    pub fn try_enter(&mut self, tid: ThreadId) -> bool {
        match self.owner {
            None => {
                self.owner = Some(tid);
                self.recursion = 1;
                true
            }
            Some(o) if o == tid => {
                self.recursion += 1;
                true
            }
            _ => false,
        }
    }

    pub fn exit(&mut self, tid: ThreadId) -> Option<ThreadId> {
        if self.owner != Some(tid) {
            // Not the owner — can happen when wait() transferred ownership
            return None;
        }
        if self.recursion == 0 {
            // Prevent underflow — should not happen in correct code
            return None;
        }
        self.recursion -= 1;
        if self.recursion == 0 {
            // Transfer ownership to next blocked thread, if any
            if let Some(next) = self.blocked_queue.pop_front() {
                self.owner = Some(next);
                // Set recursion to 0 here; the actual recursion count will be
                // restored by the scheduler:
                //   - RetryMonitorEnter: interpreter retries MONITORENTER which
                //     calls try_enter() → rec becomes 1
                //   - ReenterAfterWait: process_blocked_threads restores
                //     saved_recursion
                self.recursion = 0;
                Some(next)
            } else {
                self.owner = None;
                None
            }
        } else {
            None
        }
    }

    /// Returns (saved_recursion, consumed_pending).
    /// If consumed_pending is true, a pending notification was consumed and the
    /// thread should not actually enter the wait set — it should immediately
    /// re-enter the monitor (go to blocked_queue) as if notify was called.
    pub fn wait(&mut self, tid: ThreadId) -> (u32, bool) {
        let saved_recursion = if self.owner == Some(tid) {
            let r = self.recursion;
            self.owner = None;
            self.recursion = 0;
            r
        } else {
            1
        };

        let consumed_pending = if self.pending_notifies > 0 {
            self.pending_notifies -= 1;
            // Don't enter wait_set — thread will be treated as "just notified"
            // by the caller, which adds it to pending_notify_wakeups.
            true
        } else {
            self.saved_recursions.insert(tid, saved_recursion);
            self.wait_set.push_back(tid);
            false
        };

        // Transfer ownership to next blocked thread if any
        if self.owner.is_none() {
            if let Some(next) = self.blocked_queue.pop_front() {
                self.owner = Some(next);
                self.recursion = 1;
            }
        }

        (saved_recursion, consumed_pending)
    }

    pub fn notify(&mut self) -> Option<(ThreadId, u32)> {
        if let Some(tid) = self.wait_set.pop_front() {
            let saved = self.saved_recursions.remove(&tid).unwrap_or(1);
            self.blocked_queue.push_back(tid);
            Some((tid, saved))
        } else {
            // No thread waiting — store as pending for the next wait() call.
            // Use = 1 (not += 1) because multiple notify calls before a single
            // wait() should only produce one pending signal.  Over-counting
            // causes a livelock where wait() repeatedly consumes stale pendings
            // and spins without blocking.
            self.pending_notifies = 1;
            None
        }
    }

    pub fn notify_all(&mut self) -> Vec<(ThreadId, u32)> {
        let tids: Vec<_> = self.wait_set.drain(..).collect();
        if tids.is_empty() {
            // Same as notify(): cap at 1 to prevent livelock from stale pendings.
            self.pending_notifies = 1;
        }
        let mut result = Vec::with_capacity(tids.len());
        for tid in tids {
            let saved = self.saved_recursions.remove(&tid).unwrap_or(1);
            self.blocked_queue.push_back(tid);
            result.push((tid, saved));
        }
        result
    }
}

impl Default for CoopMonitor {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_enter_exit() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));
        assert_eq!(m.owner, Some(1));
        assert_eq!(m.recursion, 1);

        assert!(!m.try_enter(2));

        assert!(m.try_enter(1));
        assert_eq!(m.recursion, 2);

        m.exit(1);
        assert_eq!(m.recursion, 1);
        assert_eq!(m.owner, Some(1));

        let woken = m.exit(1);
        assert_eq!(m.owner, None);
        assert!(woken.is_none());
    }

    #[test]
    fn test_blocked_wakeup() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));

        assert!(!m.try_enter(2));
        m.blocked_queue.push_back(2);

        assert!(!m.try_enter(3));
        m.blocked_queue.push_back(3);

        let woken = m.exit(1);
        assert_eq!(woken, Some(2));
        assert_eq!(m.owner, Some(2)); // exit() transfers ownership to next blocked
        assert_eq!(m.recursion, 0); // exit() sets rec=0, caller restores as needed
    }

    #[test]
    fn test_wait_notify() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));

        let (saved, consumed) = m.wait(1);
        assert_eq!(saved, 1);
        assert!(!consumed);
        assert_eq!(m.owner, None);
        assert_eq!(m.wait_set.len(), 1);

        let notified = m.notify();
        assert_eq!(notified, Some((1, 1)));
        assert_eq!(m.wait_set.len(), 0);
        assert_eq!(m.blocked_queue.len(), 1);
    }

    #[test]
    fn test_notify_all() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));

        m.wait_set.push_back(2);
        m.wait_set.push_back(3);
        m.wait_set.push_back(4);

        let notified = m.notify_all();
        assert_eq!(notified, vec![(2, 1), (3, 1), (4, 1)]);
        assert_eq!(m.wait_set.len(), 0);
        assert_eq!(m.blocked_queue.len(), 3);
    }

    #[test]
    fn test_wait_with_blocked_transfer() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));

        assert!(!m.try_enter(2));
        m.blocked_queue.push_back(2);

        let (saved, consumed) = m.wait(1);
        assert_eq!(saved, 1);
        assert!(!consumed);
        assert_eq!(m.owner, Some(2));
        assert_eq!(m.recursion, 1);
    }

    #[test]
    fn test_pending_notify_consumed_by_wait() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));

        // notify with empty wait_set → pending
        let woke = m.notify();
        assert!(woke.is_none());
        assert_eq!(m.pending_notifies, 1);

        // wait should consume pending and NOT enter wait_set
        let (saved, consumed) = m.wait(1);
        assert_eq!(saved, 1);
        assert!(consumed);
        assert_eq!(m.pending_notifies, 0);
        assert_eq!(m.wait_set.len(), 0);
    }

    #[test]
    fn test_notify_all_pending() {
        let mut m = CoopMonitor::new();
        assert!(m.try_enter(1));

        // notifyAll with empty wait_set → pending
        let wakeups = m.notify_all();
        assert!(wakeups.is_empty());
        assert_eq!(m.pending_notifies, 1);

        // wait should consume
        let (_, consumed) = m.wait(1);
        assert!(consumed);
        assert_eq!(m.pending_notifies, 0);
        assert_eq!(m.wait_set.len(), 0);
    }
}
