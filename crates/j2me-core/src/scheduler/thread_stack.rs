use serde::{Deserialize, Serialize};

pub type Slot = u32;

/// Frame type encoded in the high 4 bits of the METHOD_ID slot (FP+2),
/// matching PluotSorbet's FrameType encoding.
#[repr(u32)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FrameType {
    Interpreter = 0x0000_0000,
    ExitInterpreter = 0x1000_0000,
    PushPendingFrames = 0x2000_0000,
    Interrupt = 0x3000_0000,
    Native = 0x4000_0000,
}

pub const METHOD_ID_MASK: u32 = 0x0FFF_FFFF;
pub const FRAME_TYPE_MASK: u32 = 0xF000_0000;

const CALLER_PC: usize = 0;
const CALLER_FP: usize = 1;
const METHOD_ID: usize = 2;
const MONITOR_OBJ: usize = 3;
pub const FRAME_HEADER_SIZE: usize = 4;

// CALLER_PC high-bit flags — J2ME bytecode is always < 16MB, so 4 bits are safe.
// bit 31: is_clinit (this frame was pushed for a <clinit> method)
// bit 30-28: invoke_size (0=not invoke, 3=normal invoke, 5=invokeinterface)
const PC_CLINIT_BIT: u32 = 0x8000_0000;
const PC_INVOKE_SIZE_SHIFT: u32 = 28;
const PC_INVOKE_SIZE_MASK: u32 = 0x7000_0000;
const PC_RAW_MASK: u32 = 0x0FFF_FFFF;

const INITIAL_STACK_CAPACITY: usize = 1024;
const MAX_FRAME_DEPTH: usize = 512;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ThreadStack {
    pub data: Vec<Slot>,
    pub fp: usize,
    pub sp: usize,
    pub frame_depth: usize,
    pub overflow: bool,
}

impl ThreadStack {
    pub fn new() -> Self {
        Self {
            data: vec![0; INITIAL_STACK_CAPACITY],
            fp: 0,
            sp: 0,
            frame_depth: 0,
            overflow: false,
        }
    }

    fn ensure_capacity(&mut self, needed: usize) {
        if needed > self.data.len() {
            self.data.resize(needed + 256, 0);
        }
    }

    pub fn push_frame(
        &mut self,
        max_locals: usize,
        max_stack: usize,
        method_id: u32,
        caller_pc: usize,
    ) {
        self.push_frame_typed(
            max_locals,
            max_stack,
            method_id,
            caller_pc,
            FrameType::Interpreter,
        );
    }

    /// Push a clinit frame, encoding the is_clinit flag in caller_pc high bit.
    pub fn push_frame_clinit(
        &mut self,
        max_locals: usize,
        max_stack: usize,
        method_id: u32,
        caller_pc: usize,
    ) {
        let encoded_pc = (caller_pc as u32 & PC_RAW_MASK) | PC_CLINIT_BIT;
        self.push_frame_typed(
            max_locals,
            max_stack,
            method_id,
            encoded_pc as usize,
            FrameType::Interpreter,
        );
    }

    pub fn push_frame_typed(
        &mut self,
        max_locals: usize,
        max_stack: usize,
        method_id: u32,
        caller_pc: usize,
        frame_type: FrameType,
    ) {
        self.frame_depth += 1;
        if self.frame_depth > MAX_FRAME_DEPTH {
            self.overflow = true;
            return;
        }
        let new_fp = self.sp + max_locals;
        let needed = new_fp + FRAME_HEADER_SIZE + max_stack;
        self.ensure_capacity(needed);

        self.data[new_fp + CALLER_PC] = caller_pc as Slot;
        self.data[new_fp + CALLER_FP] = self.fp as Slot;
        self.data[new_fp + METHOD_ID] = (frame_type as u32) | (method_id & METHOD_ID_MASK);
        self.data[new_fp + MONITOR_OBJ] = 0;

        self.fp = new_fp;
        self.sp = new_fp + FRAME_HEADER_SIZE;
    }

    /// Push a marker frame (no method info, like PluotSorbet's pushMarkerFrame).
    pub fn push_marker_frame(&mut self, frame_type: FrameType) {
        self.push_frame_typed(0, 0, 0, 0, frame_type);
    }

    /// Zero-copy frame push for method invocations (matching PluotSorbet).
    ///
    /// The top `param_slots` values on the caller's operand stack are **not**
    /// popped and re-written; instead they are adopted in-place as the callee's
    /// first `param_slots` locals.
    ///
    /// Layout before:
    /// ```text
    ///   ... [arg0] [arg1] ... [arg_{n-1}]
    ///                                     ^ sp
    /// ```
    /// Layout after:
    /// ```text
    ///   ... [arg0] [arg1] ... [arg_{n-1}] [0..pad] [HEADER]
    ///        ^ lp = old_sp - param_slots            ^ fp = lp + max_locals
    ///                                                       ^ sp = fp + HEADER_SIZE
    /// ```
    pub fn push_frame_for_invoke(
        &mut self,
        max_locals: usize,
        max_stack: usize,
        param_slots: usize,
        method_id: u32,
        caller_pc: usize,
    ) {
        self.push_frame_for_invoke_typed(
            max_locals,
            max_stack,
            param_slots,
            method_id,
            caller_pc,
            FrameType::Interpreter,
        );
    }

    pub fn push_frame_for_invoke_typed(
        &mut self,
        max_locals: usize,
        max_stack: usize,
        param_slots: usize,
        method_id: u32,
        caller_pc: usize,
        frame_type: FrameType,
    ) {
        self.frame_depth += 1;
        if self.frame_depth > MAX_FRAME_DEPTH {
            self.overflow = true;
            return;
        }
        // lp points to where arg0 already lives
        let lp = self.sp - param_slots;
        let new_fp = lp + max_locals;
        let needed = new_fp + FRAME_HEADER_SIZE + max_stack;
        self.ensure_capacity(needed);

        // Zero out the pad region (locals beyond the parameters)
        for i in (lp + param_slots)..new_fp {
            self.data[i] = 0;
        }

        // Encode invoke_size into caller_pc high bits.
        // The caller should have already encoded the invoke_size via encode_invoke_pc().
        self.data[new_fp + CALLER_PC] = caller_pc as Slot;
        self.data[new_fp + CALLER_FP] = self.fp as Slot;
        self.data[new_fp + METHOD_ID] = (frame_type as u32) | (method_id & METHOD_ID_MASK);
        self.data[new_fp + MONITOR_OBJ] = 0;

        self.fp = new_fp;
        self.sp = new_fp + FRAME_HEADER_SIZE;
    }

    /// Pop the current frame. Reads method_id from the frame header,
    /// looks up max_locals from the provided table (matching PluotSorbet's
    /// methodIdToMethodInfoMap pattern). No external max_locals param needed.
    pub fn pop_frame(&mut self, max_locals_table: &[u16]) -> (usize, usize) {
        if self.frame_depth > 0 {
            self.frame_depth -= 1;
        }
        let method_id = (self.data[self.fp + METHOD_ID] & METHOD_ID_MASK) as usize;
        let max_locals = max_locals_table.get(method_id).copied().unwrap_or(0) as usize;

        let caller_pc = self.data[self.fp + CALLER_PC] as usize;
        let caller_fp = self.data[self.fp + CALLER_FP] as usize;

        self.sp = self.fp - max_locals;
        self.fp = caller_fp;

        (caller_fp, caller_pc)
    }

    /// Push a value onto the operand stack.
    /// Frame creation reserves `max_stack`, so the interpreter hot path does
    /// not need to repeat a Vec capacity check for every Java operand push.
    #[inline(always)]
    pub fn push(&mut self, val: Slot) {
        debug_assert!(self.sp < self.data.len(), "operand stack exceeds max_stack");
        unsafe {
            *self.data.get_unchecked_mut(self.sp) = val;
        }
        self.sp += 1;
    }

    /// Pop a value from the operand stack.
    /// Uses unsafe get_unchecked to eliminate bounds checking on hot path.
    #[inline(always)]
    pub fn pop(&mut self) -> Slot {
        self.sp -= 1;
        unsafe { *self.data.get_unchecked(self.sp) }
    }

    /// Peek at the top of the operand stack without popping.
    #[inline(always)]
    pub fn peek(&self) -> Slot {
        unsafe { *self.data.get_unchecked(self.sp - 1) }
    }

    /// Peek at a value `depth` slots below the top.
    #[inline(always)]
    pub fn peek_at(&self, depth: usize) -> Slot {
        unsafe { *self.data.get_unchecked(self.sp - 1 - depth) }
    }

    /// Read a local variable. Uses unsafe get_unchecked for hot path.
    #[inline(always)]
    pub fn local(&self, idx: usize, max_locals: usize) -> Slot {
        debug_assert!(idx < max_locals, "local index out of bounds");
        unsafe { *self.data.get_unchecked(self.fp - max_locals + idx) }
    }

    /// Write a local variable. Uses unsafe get_unchecked for hot path.
    #[inline(always)]
    pub fn set_local(&mut self, idx: usize, max_locals: usize, val: Slot) {
        debug_assert!(idx < max_locals, "local index out of bounds");
        unsafe {
            *self.data.get_unchecked_mut(self.fp - max_locals + idx) = val;
        }
    }

    pub fn method_id_at(&self, fp: usize) -> u32 {
        self.data[fp + METHOD_ID] & METHOD_ID_MASK
    }

    pub fn frame_type_at(&self, fp: usize) -> u32 {
        self.data[fp + METHOD_ID] & FRAME_TYPE_MASK
    }

    pub fn monitor_at(&self, fp: usize) -> u32 {
        self.data[fp + MONITOR_OBJ]
    }

    pub fn set_monitor(&mut self, fp: usize, obj: u32) {
        self.data[fp + MONITOR_OBJ] = obj;
    }

    /// Encode invoke_size (3 or 5) into a caller_pc value for push_frame_for_invoke.
    #[inline(always)]
    pub fn encode_invoke_pc(caller_pc: usize, invoke_size: u8) -> usize {
        ((caller_pc as u32 & PC_RAW_MASK) | ((invoke_size as u32) << PC_INVOKE_SIZE_SHIFT)) as usize
    }

    /// Decode the raw caller_pc: extract actual PC value (low 28 bits).
    #[inline(always)]
    pub fn decode_caller_pc(encoded: usize) -> usize {
        (encoded as u32 & PC_RAW_MASK) as usize
    }

    /// Decode invoke_size from encoded caller_pc (bits 30-28).
    #[inline(always)]
    pub fn decode_invoke_size(encoded: usize) -> usize {
        ((encoded as u32 & PC_INVOKE_SIZE_MASK) >> PC_INVOKE_SIZE_SHIFT) as usize
    }

    /// Decode is_clinit flag from encoded caller_pc (bit 31).
    #[inline(always)]
    pub fn decode_is_clinit(encoded: usize) -> bool {
        (encoded as u32 & PC_CLINIT_BIT) != 0
    }

    pub fn reset_operand_stack(&mut self) {
        self.sp = self.fp + FRAME_HEADER_SIZE;
    }

    pub fn frame_count(&self) -> usize {
        let mut count = 0;
        let mut fp = self.fp;
        while fp != 0 || count == 0 {
            count += 1;
            let caller_fp = self.data[fp + CALLER_FP] as usize;
            if caller_fp == fp {
                break;
            }
            fp = caller_fp;
            if fp == 0 {
                break;
            }
        }
        count
    }

    pub fn has_frames(&self) -> bool {
        self.sp > 0
    }
}

impl Default for ThreadStack {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Build a max_locals lookup table for tests.
    /// Entries: index = method_id, value = max_locals.
    fn test_ml_table() -> Vec<u16> {
        let mut t = vec![0u16; 256];
        t[1] = 1;
        t[2] = 2;
        t[3] = 0;
        t[42] = 5;
        t[100] = 2;
        t[200] = 1;
        t
    }

    #[test]
    fn test_push_pop_frame() {
        let mut stack = ThreadStack::new();
        let ml = test_ml_table();

        stack.push_frame(2, 4, 100, 0);
        assert_eq!(stack.method_id_at(stack.fp), 100);

        stack.set_local(0, 2, 42);
        stack.set_local(1, 2, 99);
        assert_eq!(stack.local(0, 2), 42);
        assert_eq!(stack.local(1, 2), 99);

        stack.push(10);
        stack.push(20);
        assert_eq!(stack.pop(), 20);
        assert_eq!(stack.pop(), 10);

        let outer_fp = stack.fp;
        stack.push_frame(1, 4, 200, 5);
        assert_eq!(stack.method_id_at(stack.fp), 200);

        stack.push(77);
        assert_eq!(stack.pop(), 77);

        let (caller_fp, caller_pc) = stack.pop_frame(&ml);
        assert_eq!(caller_fp, outer_fp);
        assert_eq!(caller_pc, 5);
    }

    #[test]
    fn test_nested_frames() {
        let mut stack = ThreadStack::new();
        let ml = test_ml_table();

        stack.push_frame(1, 4, 1, 0);
        stack.set_local(0, 1, 100);

        stack.push_frame(2, 4, 2, 10);
        stack.set_local(0, 2, 200);
        stack.set_local(1, 2, 201);

        stack.push_frame(0, 4, 3, 20);

        assert_eq!(stack.method_id_at(stack.fp), 3);
        let (_, pc) = stack.pop_frame(&ml);
        assert_eq!(pc, 20);

        assert_eq!(stack.method_id_at(stack.fp), 2);
        assert_eq!(stack.local(0, 2), 200);
        assert_eq!(stack.local(1, 2), 201);
        let (_, pc) = stack.pop_frame(&ml);
        assert_eq!(pc, 10);

        assert_eq!(stack.method_id_at(stack.fp), 1);
        assert_eq!(stack.local(0, 1), 100);
    }

    #[test]
    fn test_operand_stack() {
        let mut stack = ThreadStack::new();
        stack.push_frame(0, 4, 1, 0);

        stack.push(1);
        stack.push(2);
        stack.push(3);
        assert_eq!(stack.peek(), 3);
        assert_eq!(stack.pop(), 3);
        assert_eq!(stack.pop(), 2);
        assert_eq!(stack.pop(), 1);
    }

    #[test]
    fn test_zero_copy_invoke() {
        let mut stack = ThreadStack::new();
        let ml = test_ml_table();

        // Caller frame with 1 local
        stack.push_frame(1, 4, 1, 0);
        stack.set_local(0, 1, 999);

        // Caller pushes 3 args onto operand stack
        stack.push(10); // arg0 (this)
        stack.push(20); // arg1
        stack.push(30); // arg2

        // Zero-copy invoke: callee has max_locals=5, param_slots=3
        stack.push_frame_for_invoke(5, 4, 3, 42, 7);

        // The 3 args should now be callee's locals[0..3]
        assert_eq!(stack.local(0, 5), 10);
        assert_eq!(stack.local(1, 5), 20);
        assert_eq!(stack.local(2, 5), 30);

        // Extra locals[3..5] should be zero
        assert_eq!(stack.local(3, 5), 0);
        assert_eq!(stack.local(4, 5), 0);

        assert_eq!(stack.method_id_at(stack.fp), 42);

        // Pop callee frame
        let (_, caller_pc) = stack.pop_frame(&ml);
        assert_eq!(caller_pc, 7);

        // Caller's locals should be intact
        assert_eq!(stack.local(0, 1), 999);
    }

    #[test]
    fn test_frame_reserves_declared_operand_stack() {
        let mut stack = ThreadStack {
            data: Vec::new(),
            fp: 0,
            sp: 0,
            frame_depth: 0,
            overflow: false,
        };
        stack.push_frame(3, 7, 1, 0);
        let reserved_len = stack.data.len();
        assert!(reserved_len >= 3 + FRAME_HEADER_SIZE + 7);

        for value in 0..7 {
            stack.push(value);
        }
        assert_eq!(stack.data.len(), reserved_len);
    }
}
