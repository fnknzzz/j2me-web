use super::thread_stack::ThreadStack;
use serde::{Deserialize, Serialize};

pub type ThreadId = u32;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum JavaThreadState {
    New,
    Runnable,
    Blocked,
    Waiting,
    TimedWaiting,
    Terminated,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ResumeAction {
    RetryMonitorEnter { obj_id: u32 },
    ReenterAfterWait { obj_id: u32, saved_recursion: u32 },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum StepResult {
    Yielded(YieldReason),
    ThreadFinished,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum YieldReason {
    TimeSlice,
    Sleep {
        deadline_ms: u64,
    },
    MonitorBlocked {
        obj_id: u32,
    },
    Wait {
        obj_id: u32,
        timeout: Option<u64>,
        saved_recursion: u32,
    },
    Join {
        target_tid: ThreadId,
    },
    Terminated,
    AsyncWait {
        request_id: u32,
    },
    /// Thread is suspended waiting for a native event to arrive.
    /// Unlike Sleep, this has no deadline — the thread stays in Waiting state
    /// until push_native_event explicitly wakes it (like PluotSorbet's asyncImpl).
    NativeEventWait,
    Exit,
}

pub enum NativeResult {
    Done(Vec<u32>),
    Yield(YieldReason),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VirtualThread {
    pub id: ThreadId,
    pub name: String,
    pub state: JavaThreadState,
    pub daemon: bool,
    pub interrupt_flag: bool,
    pub priority: u8,

    pub stack: ThreadStack,
    pub pc: usize,
    pub current_method: u32,

    pub virtual_runtime: f64,

    pub resume_action: Option<ResumeAction>,

    /// Number of monitors currently held by this thread.
    /// When > 0, the scheduler should not preempt this thread.
    pub held_monitor_count: u32,
}

impl VirtualThread {
    pub fn new(id: ThreadId, name: String, priority: u8, daemon: bool) -> Self {
        Self {
            id,
            name,
            state: JavaThreadState::New,
            daemon,
            interrupt_flag: false,
            priority,
            stack: ThreadStack::new(),
            pc: 0,
            current_method: 0,
            virtual_runtime: 0.0,
            resume_action: None,
            held_monitor_count: 0,
        }
    }

    pub fn is_alive(&self) -> bool {
        !matches!(
            self.state,
            JavaThreadState::New | JavaThreadState::Terminated
        )
    }
}
