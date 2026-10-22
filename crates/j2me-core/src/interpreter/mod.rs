pub mod native;

use crate::classfile::class_repo::ClassRepository;
use crate::classfile::opcode::*;
use crate::classfile::types::*;
use crate::heap::NULL;
use crate::scheduler::virtual_thread::{StepResult, ThreadId, YieldReason};
use crate::scheduler::ThreadStack;
use crate::vm::SharedVm;

/// Recursively allocate a multi-dimensional Java array.
/// `depth` is the current dimension index, `dims` is the total number of dimensions.
/// The innermost dimension uses `inner_bpe` (bytes per element); outer dimensions use 4 (reference size).
fn alloc_multi_array(
    vm: &mut SharedVm,
    sizes: &[u32],
    depth: usize,
    dims: usize,
    inner_bpe: u32,
    descriptor: &str,
) -> u32 {
    let len = sizes[depth];
    // Current level's descriptor: e.g. for "[[B" at depth 0 → "[[B", depth 1 → "[B"
    let level_desc = &descriptor[depth..];
    let class_id = vm.class_repo.get_or_register_array_class(level_desc);
    if depth == dims - 1 {
        // Innermost dimension: allocate a flat array with the correct element size and class_id
        vm.new_array(class_id, len, inner_bpe)
    } else {
        // Outer dimension: allocate a reference array and fill each element recursively
        let outer = vm.new_array(class_id, len, 4);
        for i in 0..len {
            let inner = alloc_multi_array(vm, sizes, depth + 1, dims, inner_bpe, descriptor);
            vm.heap.array_write_u32(outer, i, inner);
        }
        outer
    }
}

#[inline]
fn method_frame_layout(vm: &SharedVm, method_id: u32) -> Option<(usize, usize)> {
    let method = vm.class_repo.get_method(method_id)?;
    Some((method.max_locals() as usize, method.max_stack() as usize))
}

pub fn step(
    stack: &mut ThreadStack,
    pc: &mut usize,
    current_method: &mut u32,
    _thread_id: ThreadId,
    vm: &mut SharedVm,
    max_instructions: u32,
) -> StepResult {
    let mut instruction_count: u32 = 0;

    // Yield at coarse safe points (method entry / invokes and loop back-edges).
    // The caller sets a larger max_instructions when the thread holds monitors
    // (50K vs 1.5K), giving synchronized blocks a better chance to complete
    // without preemption — but NOT infinite, to avoid deadlock when a thread
    // spin-waits inside a synchronized block for another thread.
    let should_yield_timeslice = |_vm: &SharedVm, instruction_count: u32, max_instructions: u32| {
        instruction_count >= max_instructions
    };

    // Cache the current method outside the loop — only refresh on frame switch.
    // This eliminates per-iteration clone() of RuntimeMethod (which contains
    // String fields and Vec<u8> bytecode), matching PluotSorbet's approach of
    // caching mi/code/maxLocals and only updating them on invoke/return.
    let mut cached_method_id: u32 = *current_method;
    let mut method_ptr = match vm.class_repo.get_method_ptr(*current_method) {
        Some(method) => method,
        None => return StepResult::ThreadFinished,
    };
    // SAFETY: the repository's method arena is immutable while threads execute;
    // class linking is completed before the scheduler starts running bytecode.
    let mut method = unsafe { &*method_ptr };
    let mut caller_class_id: u32 = method.class_id;
    let mut poll_safepoint = true;

    loop {
        if !stack.has_frames() {
            return StepResult::ThreadFinished;
        }

        // Global VM state is observable only at safe points: method boundaries,
        // allocations, invokes, and loop back-edges. Ordinary bytecodes avoid
        // paying these branches on every dispatch.
        if poll_safepoint || *current_method != cached_method_id {
            poll_safepoint = false;
            if stack.overflow {
                stack.overflow = false;
                if throw_runtime(
                    stack,
                    pc,
                    current_method,
                    vm,
                    _thread_id,
                    "java.lang.StackOverflowError",
                ) {
                    continue;
                }
                return StepResult::ThreadFinished;
            }

            if vm.heap.oom {
                vm.heap.oom = false;
                let exception_ref = if vm.preallocated_oom != 0 {
                    vm.preallocated_oom
                } else {
                    let obj = vm.new_object("java.lang.OutOfMemoryError");
                    if vm.heap.oom {
                        vm.heap.oom = false;
                        return StepResult::ThreadFinished;
                    }
                    vm.preallocated_oom = obj;
                    obj
                };
                if handle_exception(stack, pc, current_method, vm, _thread_id, exception_ref) {
                    continue;
                }
                return StepResult::ThreadFinished;
            }

            if vm.force_gc || vm.heap.needs_gc() {
                return StepResult::Yielded(YieldReason::TimeSlice);
            }
            if instruction_count > 0
                && should_yield_timeslice(vm, instruction_count, max_instructions)
            {
                return StepResult::Yielded(YieldReason::TimeSlice);
            }
        }

        // Refresh cached method only when current_method has changed (frame switch)
        if *current_method != cached_method_id {
            method_ptr = match vm.class_repo.get_method_ptr(*current_method) {
                Some(method) => method,
                None => return StepResult::ThreadFinished,
            };
            // SAFETY: see the method-arena invariant above.
            method = unsafe { &*method_ptr };
            cached_method_id = *current_method;
            caller_class_id = method.class_id;
        }

        let code = method.bytecode();
        if *pc >= code.len() {
            return StepResult::ThreadFinished;
        }

        let op = code[*pc];
        instruction_count += 1;
        poll_safepoint = matches!(
            op,
            INVOKEVIRTUAL
                | INVOKESPECIAL
                | INVOKESTATIC
                | INVOKEINTERFACE
                | NEW
                | NEWARRAY
                | ANEWARRAY
                | MULTIANEWARRAY
        );

        match op {
            NOP => {
                *pc += 1;
            }

            ACONST_NULL => {
                stack.push(NULL);
                *pc += 1;
            }

            ICONST_M1 => {
                stack.push((-1i32) as u32);
                *pc += 1;
            }
            ICONST_0 => {
                stack.push(0);
                *pc += 1;
            }
            ICONST_1 => {
                stack.push(1);
                *pc += 1;
            }
            ICONST_2 => {
                stack.push(2);
                *pc += 1;
            }
            ICONST_3 => {
                stack.push(3);
                *pc += 1;
            }
            ICONST_4 => {
                stack.push(4);
                *pc += 1;
            }
            ICONST_5 => {
                stack.push(5);
                *pc += 1;
            }

            LCONST_0 => {
                stack.push(0);
                stack.push(0);
                *pc += 1;
            }
            LCONST_1 => {
                stack.push(1);
                stack.push(0);
                *pc += 1;
            }

            FCONST_0 => {
                stack.push(0f32.to_bits());
                *pc += 1;
            }
            FCONST_1 => {
                stack.push(1f32.to_bits());
                *pc += 1;
            }
            FCONST_2 => {
                stack.push(2f32.to_bits());
                *pc += 1;
            }

            DCONST_0 => {
                let bits = 0f64.to_bits();
                stack.push(bits as u32);
                stack.push((bits >> 32) as u32);
                *pc += 1;
            }
            DCONST_1 => {
                let bits = 1f64.to_bits();
                stack.push(bits as u32);
                stack.push((bits >> 32) as u32);
                *pc += 1;
            }

            BIPUSH => {
                let val = code[*pc + 1] as i8 as i32;
                stack.push(val as u32);
                *pc += 2;
            }
            SIPUSH => {
                let val = read_i16(code, *pc + 1) as i32;
                stack.push(val as u32);
                *pc += 3;
            }

            LDC => {
                let index = code[*pc + 1] as u16;
                push_ldc(stack, caller_class_id, index, vm);
                *pc += 2;
            }
            LDC_W => {
                let index = read_u16(code, *pc + 1);
                push_ldc(stack, caller_class_id, index, vm);
                *pc += 3;
            }
            LDC2_W => {
                let index = read_u16(code, *pc + 1);
                push_ldc2(stack, caller_class_id, index, vm);
                *pc += 3;
            }

            ILOAD | FLOAD | ALOAD => {
                let idx = code[*pc + 1] as usize;
                let val = stack.local(idx, method.max_locals() as usize);
                stack.push(val);
                *pc += 2;
            }
            LLOAD | DLOAD => {
                let idx = code[*pc + 1] as usize;
                let lo = stack.local(idx, method.max_locals() as usize);
                let hi = stack.local(idx + 1, method.max_locals() as usize);
                stack.push(lo);
                stack.push(hi);
                *pc += 2;
            }

            ILOAD_0 | FLOAD_0 | ALOAD_0 => {
                let val = stack.local(0, method.max_locals() as usize);
                stack.push(val);
                *pc += 1;
            }
            ILOAD_1 | FLOAD_1 | ALOAD_1 => {
                let val = stack.local(1, method.max_locals() as usize);
                stack.push(val);
                *pc += 1;
            }
            ILOAD_2 | FLOAD_2 | ALOAD_2 => {
                let val = stack.local(2, method.max_locals() as usize);
                stack.push(val);
                *pc += 1;
            }
            ILOAD_3 | FLOAD_3 | ALOAD_3 => {
                let val = stack.local(3, method.max_locals() as usize);
                stack.push(val);
                *pc += 1;
            }
            LLOAD_0 | DLOAD_0 => {
                let lo = stack.local(0, method.max_locals() as usize);
                let hi = stack.local(1, method.max_locals() as usize);
                stack.push(lo);
                stack.push(hi);
                *pc += 1;
            }
            LLOAD_1 | DLOAD_1 => {
                let lo = stack.local(1, method.max_locals() as usize);
                let hi = stack.local(2, method.max_locals() as usize);
                stack.push(lo);
                stack.push(hi);
                *pc += 1;
            }
            LLOAD_2 | DLOAD_2 => {
                let lo = stack.local(2, method.max_locals() as usize);
                let hi = stack.local(3, method.max_locals() as usize);
                stack.push(lo);
                stack.push(hi);
                *pc += 1;
            }
            LLOAD_3 | DLOAD_3 => {
                let lo = stack.local(3, method.max_locals() as usize);
                let hi = stack.local(4, method.max_locals() as usize);
                stack.push(lo);
                stack.push(hi);
                *pc += 1;
            }

            ISTORE | FSTORE | ASTORE => {
                let idx = code[*pc + 1] as usize;
                let val = stack.pop();
                stack.set_local(idx, method.max_locals() as usize, val);
                *pc += 2;
            }
            LSTORE | DSTORE => {
                let idx = code[*pc + 1] as usize;
                let hi = stack.pop();
                let lo = stack.pop();
                stack.set_local(idx, method.max_locals() as usize, lo);
                stack.set_local(idx + 1, method.max_locals() as usize, hi);
                *pc += 2;
            }

            ISTORE_0 | FSTORE_0 | ASTORE_0 => {
                let val = stack.pop();
                stack.set_local(0, method.max_locals() as usize, val);
                *pc += 1;
            }
            ISTORE_1 | FSTORE_1 | ASTORE_1 => {
                let val = stack.pop();
                stack.set_local(1, method.max_locals() as usize, val);
                *pc += 1;
            }
            ISTORE_2 | FSTORE_2 | ASTORE_2 => {
                let val = stack.pop();
                stack.set_local(2, method.max_locals() as usize, val);
                *pc += 1;
            }
            ISTORE_3 | FSTORE_3 | ASTORE_3 => {
                let val = stack.pop();
                stack.set_local(3, method.max_locals() as usize, val);
                *pc += 1;
            }
            LSTORE_0 | DSTORE_0 => {
                let hi = stack.pop();
                let lo = stack.pop();
                stack.set_local(0, method.max_locals() as usize, lo);
                stack.set_local(1, method.max_locals() as usize, hi);
                *pc += 1;
            }
            LSTORE_1 | DSTORE_1 => {
                let hi = stack.pop();
                let lo = stack.pop();
                stack.set_local(1, method.max_locals() as usize, lo);
                stack.set_local(2, method.max_locals() as usize, hi);
                *pc += 1;
            }
            LSTORE_2 | DSTORE_2 => {
                let hi = stack.pop();
                let lo = stack.pop();
                stack.set_local(2, method.max_locals() as usize, lo);
                stack.set_local(3, method.max_locals() as usize, hi);
                *pc += 1;
            }
            LSTORE_3 | DSTORE_3 => {
                let hi = stack.pop();
                let lo = stack.pop();
                stack.set_local(3, method.max_locals() as usize, lo);
                stack.set_local(4, method.max_locals() as usize, hi);
                *pc += 1;
            }

            IALOAD | FALOAD | AALOAD => {
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    let class_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("");
                    let msg = format!(
                        "java.lang.NullPointerException (AALOAD in {}.{})",
                        class_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_read_u32(arrayref, index));
                *pc += 1;
            }
            BALOAD => {
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_read_i8(arrayref, index as u32) as i32 as u32);
                *pc += 1;
            }
            CALOAD => {
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_read_u16(arrayref, index as u32) as u32);
                *pc += 1;
            }
            SALOAD => {
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_read_i16(arrayref, index as u32) as i32 as u32);
                *pc += 1;
            }
            LALOAD => {
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let val = vm.heap.array_read_i64(arrayref, index as u32);
                push_long(stack, val);
                *pc += 1;
            }
            DALOAD => {
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let val = vm.heap.array_read_f64(arrayref, index as u32);
                push_double(stack, val);
                *pc += 1;
            }

            IASTORE | FASTORE | AASTORE => {
                let val = stack.pop();
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                vm.heap.array_write_u32(arrayref, index, val);
                *pc += 1;
            }
            BASTORE => {
                let val = stack.pop();
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                vm.heap.array_write_i8(arrayref, index, val as i8);
                *pc += 1;
            }
            CASTORE | SASTORE => {
                let val = stack.pop();
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                vm.heap.array_write_u16(arrayref, index, val as u16);
                *pc += 1;
            }
            LASTORE => {
                let hi = stack.pop();
                let lo = stack.pop();
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let val = ((hi as i64) << 32) | (lo as u32 as i64);
                vm.heap.array_write_i64(arrayref, index, val);
                *pc += 1;
            }
            DASTORE => {
                let hi = stack.pop();
                let lo = stack.pop();
                let index = stack.pop();
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let len = vm.heap.array_length(arrayref);
                if (index as i32) < 0 || index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let bits = ((hi as u64) << 32) | (lo as u64);
                vm.heap
                    .array_write_f64(arrayref, index, f64::from_bits(bits));
                *pc += 1;
            }

            POP => {
                stack.pop();
                *pc += 1;
            }
            POP2 => {
                stack.pop();
                stack.pop();
                *pc += 1;
            }

            DUP => {
                let v = stack.peek();
                stack.push(v);
                *pc += 1;
            }
            DUP_X1 => {
                let v1 = stack.pop();
                let v2 = stack.pop();
                stack.push(v1);
                stack.push(v2);
                stack.push(v1);
                *pc += 1;
            }
            DUP_X2 => {
                let v1 = stack.pop();
                let v2 = stack.pop();
                let v3 = stack.pop();
                stack.push(v1);
                stack.push(v3);
                stack.push(v2);
                stack.push(v1);
                *pc += 1;
            }
            DUP2 => {
                let v1 = stack.pop();
                let v2 = stack.pop();
                stack.push(v2);
                stack.push(v1);
                stack.push(v2);
                stack.push(v1);
                *pc += 1;
            }
            DUP2_X1 => {
                let v1 = stack.pop();
                let v2 = stack.pop();
                let v3 = stack.pop();
                stack.push(v2);
                stack.push(v1);
                stack.push(v3);
                stack.push(v2);
                stack.push(v1);
                *pc += 1;
            }
            DUP2_X2 => {
                let v1 = stack.pop();
                let v2 = stack.pop();
                let v3 = stack.pop();
                let v4 = stack.pop();
                stack.push(v2);
                stack.push(v1);
                stack.push(v4);
                stack.push(v3);
                stack.push(v2);
                stack.push(v1);
                *pc += 1;
            }
            SWAP => {
                let v1 = stack.pop();
                let v2 = stack.pop();
                stack.push(v1);
                stack.push(v2);
                *pc += 1;
            }

            IADD => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                stack.push(a.wrapping_add(b) as u32);
                *pc += 1;
            }
            ISUB => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                stack.push(a.wrapping_sub(b) as u32);
                *pc += 1;
            }
            IMUL => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                stack.push(a.wrapping_mul(b) as u32);
                *pc += 1;
            }
            IDIV => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                if b == 0 {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.ArithmeticException (/ by zero)",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(a.wrapping_div(b) as u32);
                *pc += 1;
            }
            IREM => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                if b == 0 {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.ArithmeticException (/ by zero)",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(a.wrapping_rem(b) as u32);
                *pc += 1;
            }
            INEG => {
                let v = stack.pop() as i32;
                stack.push(v.wrapping_neg() as u32);
                *pc += 1;
            }

            LADD => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                let r = to_long(al, ah).wrapping_add(to_long(bl, bh));
                push_long(stack, r);
                *pc += 1;
            }
            LSUB => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                let r = to_long(al, ah).wrapping_sub(to_long(bl, bh));
                push_long(stack, r);
                *pc += 1;
            }
            LMUL => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                let r = to_long(al, ah).wrapping_mul(to_long(bl, bh));
                push_long(stack, r);
                *pc += 1;
            }
            LDIV => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                let b = to_long(bl, bh);
                if b == 0 {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.ArithmeticException (/ by zero)",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                push_long(stack, to_long(al, ah).wrapping_div(b));
                *pc += 1;
            }
            LREM => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                let b = to_long(bl, bh);
                if b == 0 {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.ArithmeticException (/ by zero)",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                push_long(stack, to_long(al, ah).wrapping_rem(b));
                *pc += 1;
            }
            LNEG => {
                let hi = stack.pop();
                let lo = stack.pop();
                push_long(stack, to_long(lo, hi).wrapping_neg());
                *pc += 1;
            }

            FADD => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push((a + b).to_bits());
                *pc += 1;
            }
            FSUB => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push((a - b).to_bits());
                *pc += 1;
            }
            FMUL => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push((a * b).to_bits());
                *pc += 1;
            }
            FDIV => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push((a / b).to_bits());
                *pc += 1;
            }
            FREM => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push((a % b).to_bits());
                *pc += 1;
            }
            FNEG => {
                let v = f32::from_bits(stack.pop());
                stack.push((-v).to_bits());
                *pc += 1;
            }

            DADD => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                push_double(stack, a + b);
                *pc += 1;
            }
            DSUB => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                push_double(stack, a - b);
                *pc += 1;
            }
            DMUL => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                push_double(stack, a * b);
                *pc += 1;
            }
            DDIV => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                push_double(stack, a / b);
                *pc += 1;
            }
            DREM => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                push_double(stack, a % b);
                *pc += 1;
            }
            DNEG => {
                let v = pop_double(stack);
                push_double(stack, -v);
                *pc += 1;
            }

            ISHL => {
                let b = (stack.pop() as i32) & 0x1f;
                let a = stack.pop() as i32;
                stack.push((a << b) as u32);
                *pc += 1;
            }
            ISHR => {
                let b = (stack.pop() as i32) & 0x1f;
                let a = stack.pop() as i32;
                stack.push((a >> b) as u32);
                *pc += 1;
            }
            IUSHR => {
                let b = (stack.pop() & 0x1f) as u32;
                let a = stack.pop();
                stack.push(a >> b);
                *pc += 1;
            }
            IAND => {
                let b = stack.pop();
                let a = stack.pop();
                stack.push(a & b);
                *pc += 1;
            }
            IOR => {
                let b = stack.pop();
                let a = stack.pop();
                stack.push(a | b);
                *pc += 1;
            }
            IXOR => {
                let b = stack.pop();
                let a = stack.pop();
                stack.push(a ^ b);
                *pc += 1;
            }

            LSHL => {
                let shift = (stack.pop() & 0x3f) as u32;
                let hi = stack.pop();
                let lo = stack.pop();
                push_long(stack, to_long(lo, hi) << shift);
                *pc += 1;
            }
            LSHR => {
                let shift = (stack.pop() & 0x3f) as u32;
                let hi = stack.pop();
                let lo = stack.pop();
                push_long(stack, to_long(lo, hi) >> shift);
                *pc += 1;
            }
            LUSHR => {
                let shift = (stack.pop() & 0x3f) as u32;
                let hi = stack.pop();
                let lo = stack.pop();
                push_long(stack, (to_long(lo, hi) as u64 >> shift) as i64);
                *pc += 1;
            }
            LAND => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                push_long(stack, to_long(al, ah) & to_long(bl, bh));
                *pc += 1;
            }
            LOR => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                push_long(stack, to_long(al, ah) | to_long(bl, bh));
                *pc += 1;
            }
            LXOR => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                push_long(stack, to_long(al, ah) ^ to_long(bl, bh));
                *pc += 1;
            }

            IINC => {
                let idx = code[*pc + 1] as usize;
                let inc = code[*pc + 2] as i8 as i32;
                let ml = method.max_locals() as usize;
                let val = stack.local(idx, ml) as i32;
                stack.set_local(idx, ml, val.wrapping_add(inc) as u32);
                *pc += 3;
            }

            I2L => {
                let v = stack.pop() as i32 as i64;
                push_long(stack, v);
                *pc += 1;
            }
            I2F => {
                let v = stack.pop() as i32;
                stack.push((v as f32).to_bits());
                *pc += 1;
            }
            I2D => {
                let v = stack.pop() as i32;
                push_double(stack, v as f64);
                *pc += 1;
            }
            L2I => {
                let _hi = stack.pop();
                let lo = stack.pop();
                stack.push(lo);
                *pc += 1;
            }
            L2F => {
                let hi = stack.pop();
                let lo = stack.pop();
                stack.push((to_long(lo, hi) as f32).to_bits());
                *pc += 1;
            }
            L2D => {
                let hi = stack.pop();
                let lo = stack.pop();
                push_double(stack, to_long(lo, hi) as f64);
                *pc += 1;
            }
            F2I => {
                let v = f32::from_bits(stack.pop());
                stack.push(f32_to_i32(v) as u32);
                *pc += 1;
            }
            F2L => {
                let v = f32::from_bits(stack.pop());
                push_long(stack, f32_to_i64(v));
                *pc += 1;
            }
            F2D => {
                let v = f32::from_bits(stack.pop());
                push_double(stack, v as f64);
                *pc += 1;
            }
            D2I => {
                let v = pop_double(stack);
                stack.push(f64_to_i32(v) as u32);
                *pc += 1;
            }
            D2L => {
                let v = pop_double(stack);
                push_long(stack, f64_to_i64(v));
                *pc += 1;
            }
            D2F => {
                let v = pop_double(stack);
                stack.push((v as f32).to_bits());
                *pc += 1;
            }
            I2B => {
                let v = stack.pop() as i32;
                stack.push((v as i8) as i32 as u32);
                *pc += 1;
            }
            I2C => {
                let v = stack.pop();
                stack.push(v & 0xFFFF);
                *pc += 1;
            }
            I2S => {
                let v = stack.pop() as i32;
                stack.push((v as i16) as i32 as u32);
                *pc += 1;
            }

            LCMP => {
                let (bh, bl, ah, al) = pop_long_pair(stack);
                let a = to_long(al, ah);
                let b = to_long(bl, bh);
                stack.push(if a > b {
                    1u32
                } else if a == b {
                    0u32
                } else {
                    (-1i32) as u32
                });
                *pc += 1;
            }
            FCMPL => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push(if a.is_nan() || b.is_nan() {
                    (-1i32) as u32
                } else if a > b {
                    1u32
                } else if a == b {
                    0
                } else {
                    (-1i32) as u32
                });
                *pc += 1;
            }
            FCMPG => {
                let b = f32::from_bits(stack.pop());
                let a = f32::from_bits(stack.pop());
                stack.push(if a.is_nan() || b.is_nan() {
                    1u32
                } else if a > b {
                    1u32
                } else if a == b {
                    0
                } else {
                    (-1i32) as u32
                });
                *pc += 1;
            }
            DCMPL => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                stack.push(if a.is_nan() || b.is_nan() {
                    (-1i32) as u32
                } else if a > b {
                    1u32
                } else if a == b {
                    0
                } else {
                    (-1i32) as u32
                });
                *pc += 1;
            }
            DCMPG => {
                let b = pop_double(stack);
                let a = pop_double(stack);
                stack.push(if a.is_nan() || b.is_nan() {
                    1u32
                } else if a > b {
                    1u32
                } else if a == b {
                    0
                } else {
                    (-1i32) as u32
                });
                *pc += 1;
            }

            IFEQ => {
                let v = stack.pop() as i32;
                branch_if(v == 0, code, pc, &mut poll_safepoint);
            }
            IFNE => {
                let v = stack.pop() as i32;
                branch_if(v != 0, code, pc, &mut poll_safepoint);
            }
            IFLT => {
                let v = stack.pop() as i32;
                branch_if(v < 0, code, pc, &mut poll_safepoint);
            }
            IFGE => {
                let v = stack.pop() as i32;
                branch_if(v >= 0, code, pc, &mut poll_safepoint);
            }
            IFGT => {
                let v = stack.pop() as i32;
                branch_if(v > 0, code, pc, &mut poll_safepoint);
            }
            IFLE => {
                let v = stack.pop() as i32;
                branch_if(v <= 0, code, pc, &mut poll_safepoint);
            }

            IF_ICMPEQ => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                branch_if(a == b, code, pc, &mut poll_safepoint);
            }
            IF_ICMPNE => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                branch_if(a != b, code, pc, &mut poll_safepoint);
            }
            IF_ICMPLT => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                branch_if(a < b, code, pc, &mut poll_safepoint);
            }
            IF_ICMPGE => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                branch_if(a >= b, code, pc, &mut poll_safepoint);
            }
            IF_ICMPGT => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                branch_if(a > b, code, pc, &mut poll_safepoint);
            }
            IF_ICMPLE => {
                let b = stack.pop() as i32;
                let a = stack.pop() as i32;
                branch_if(a <= b, code, pc, &mut poll_safepoint);
            }

            IF_ACMPEQ => {
                let b = stack.pop();
                let a = stack.pop();
                branch_if(a == b, code, pc, &mut poll_safepoint);
            }
            IF_ACMPNE => {
                let b = stack.pop();
                let a = stack.pop();
                branch_if(a != b, code, pc, &mut poll_safepoint);
            }

            GOTO => {
                let offset = read_i16(code, *pc + 1) as i32;
                *pc = (*pc as i32 + offset) as usize;
                if offset < 0 {
                    if vm.force_gc
                        || vm.heap.needs_gc()
                        || should_yield_timeslice(vm, instruction_count, max_instructions)
                    {
                        return StepResult::Yielded(YieldReason::TimeSlice);
                    }
                }
            }
            GOTO_W => {
                let offset = read_i32(code, *pc + 1);
                *pc = (*pc as i32 + offset) as usize;
                if offset < 0 {
                    if vm.force_gc
                        || vm.heap.needs_gc()
                        || should_yield_timeslice(vm, instruction_count, max_instructions)
                    {
                        return StepResult::Yielded(YieldReason::TimeSlice);
                    }
                }
            }

            IFNULL => {
                let v = stack.pop();
                branch_if(v == NULL, code, pc, &mut poll_safepoint);
            }
            IFNONNULL => {
                let v = stack.pop();
                branch_if(v != NULL, code, pc, &mut poll_safepoint);
            }

            TABLESWITCH => {
                let index = stack.pop() as i32;
                let base = *pc;
                let pad = (4 - ((base + 1) % 4)) % 4;
                let off = base + 1 + pad;
                let default_offset = read_i32(code, off);
                let low = read_i32(code, off + 4);
                let high = read_i32(code, off + 8);
                if index >= low && index <= high {
                    let entry = off + 12 + ((index - low) as usize) * 4;
                    *pc = (base as i32 + read_i32(code, entry)) as usize;
                } else {
                    *pc = (base as i32 + default_offset) as usize;
                }
                poll_safepoint = *pc < base;
            }
            LOOKUPSWITCH => {
                let key = stack.pop() as i32;
                let base = *pc;
                let pad = (4 - ((base + 1) % 4)) % 4;
                let off = base + 1 + pad;
                let default_offset = read_i32(code, off);
                let npairs = read_i32(code, off + 4) as usize;
                let mut found = false;
                for i in 0..npairs {
                    let entry = off + 8 + i * 8;
                    if key == read_i32(code, entry) {
                        *pc = (base as i32 + read_i32(code, entry + 4)) as usize;
                        found = true;
                        break;
                    }
                }
                if !found {
                    *pc = (base as i32 + default_offset) as usize;
                }
                poll_safepoint = *pc < base;
            }

            IRETURN | FRETURN | ARETURN => {
                let ret_val = stack.pop();
                release_monitor_if_synchronized(stack, vm, _thread_id);
                let (_caller_fp, encoded_caller_pc) =
                    stack.pop_frame(vm.class_repo.max_locals_table());
                if !stack.has_frames() {
                    vm.last_thread_return_value = ret_val;
                    return StepResult::ThreadFinished;
                }
                stack.push(ret_val);
                *current_method = stack.method_id_at(stack.fp);
                let caller_pc = ThreadStack::decode_caller_pc(encoded_caller_pc);
                let invoke_size = ThreadStack::decode_invoke_size(encoded_caller_pc);
                *pc = caller_pc + invoke_size;
            }
            LRETURN | DRETURN => {
                let hi = stack.pop();
                let lo = stack.pop();
                release_monitor_if_synchronized(stack, vm, _thread_id);
                let (_caller_fp, encoded_caller_pc) =
                    stack.pop_frame(vm.class_repo.max_locals_table());
                if !stack.has_frames() {
                    return StepResult::ThreadFinished;
                }
                stack.push(lo);
                stack.push(hi);
                *current_method = stack.method_id_at(stack.fp);
                let caller_pc = ThreadStack::decode_caller_pc(encoded_caller_pc);
                let invoke_size = ThreadStack::decode_invoke_size(encoded_caller_pc);
                *pc = caller_pc + invoke_size;
            }
            RETURN => {
                let returning_method_id = *current_method;
                release_monitor_if_synchronized(stack, vm, _thread_id);
                let (_caller_fp, encoded_caller_pc) =
                    stack.pop_frame(vm.class_repo.max_locals_table());
                if !stack.has_frames() {
                    return StepResult::ThreadFinished;
                }
                let caller_pc = ThreadStack::decode_caller_pc(encoded_caller_pc);
                if ThreadStack::decode_is_clinit(encoded_caller_pc) {
                    // This frame was a <clinit> — mark the class as initialized
                    if let Some(class_id) = vm
                        .class_repo
                        .get_method_ref(returning_method_id)
                        .map(|m| m.class_id)
                    {
                        vm.mark_initialized_by_id(class_id);
                    }
                    *current_method = stack.method_id_at(stack.fp);
                    *pc = caller_pc;
                } else {
                    *current_method = stack.method_id_at(stack.fp);
                    let invoke_size = ThreadStack::decode_invoke_size(encoded_caller_pc);
                    *pc = caller_pc + invoke_size;
                }
            }

            GETSTATIC => {
                let index = read_u16(code, *pc + 1);
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index) {
                    if let CachedCpEntry::Field {
                        owner_class_id,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if let Some(clinit_id) = vm.ensure_initialized_by_id(owner_class_id) {
                            let (max_locals, max_stack) =
                                method_frame_layout(vm, clinit_id).unwrap_or((0, 0));
                            stack.push_frame_clinit(max_locals, max_stack, clinit_id, *pc);
                            *current_method = clinit_id;
                            *pc = 0;
                            continue;
                        }
                        let base = vm.static_fields.get_addr_by_id(owner_class_id);
                        if is_wide {
                            push_long(stack, vm.heap.read_i64(base + byte_offset));
                        } else {
                            stack.push(vm.heap.read_u32(base + byte_offset));
                        }
                    }
                }
                *pc += 3;
            }
            PUTSTATIC => {
                let index = read_u16(code, *pc + 1);
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index) {
                    if let CachedCpEntry::Field {
                        owner_class_id,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if let Some(clinit_id) = vm.ensure_initialized_by_id(owner_class_id) {
                            let (max_locals, max_stack) =
                                method_frame_layout(vm, clinit_id).unwrap_or((0, 0));
                            stack.push_frame_clinit(max_locals, max_stack, clinit_id, *pc);
                            *current_method = clinit_id;
                            *pc = 0;
                            continue;
                        }
                        let base = vm.static_fields.get_addr_by_id(owner_class_id);
                        if is_wide {
                            let hi = stack.pop();
                            let lo = stack.pop();
                            let val = ((hi as i64) << 32) | (lo as u32 as i64);
                            vm.heap.write_i64(base + byte_offset, val);
                        } else {
                            let val = stack.pop();
                            vm.heap.write_u32(base + byte_offset, val);
                        }
                    }
                }
                *pc += 3;
            }
            GETFIELD => {
                let index = read_u16(code, *pc + 1);
                let objectref = stack.pop();
                if objectref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let field_info = vm
                        .resolve_fieldref_cached_by_id(&caller_name, index)
                        .map(|r| format!("{}.{}", r.class_name, r.name))
                        .unwrap_or_default();
                    let msg = format!(
                        "java.lang.NullPointerException (GETFIELD {} in {}.{})",
                        field_info,
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index) {
                    if let CachedCpEntry::Field {
                        owner_class_id: _,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if is_wide {
                            push_long(stack, vm.heap.read_i64(objectref + byte_offset));
                        } else {
                            stack.push(vm.heap.read_u32(objectref + byte_offset));
                        }
                    }
                }
                *pc += 3;
            }
            PUTFIELD => {
                let index = read_u16(code, *pc + 1);
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index) {
                    if let CachedCpEntry::Field {
                        owner_class_id: _,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if is_wide {
                            let hi = stack.pop();
                            let lo = stack.pop();
                            let objectref = stack.pop();
                            if objectref == NULL {
                                let class_name = vm
                                    .class_repo
                                    .get_class_name_by_id(method.class_id)
                                    .unwrap_or("");
                                let msg = format!(
                                    "java.lang.NullPointerException (PUTFIELD in {}.{})",
                                    class_name,
                                    vm.class_repo.get_string(method.name_id)
                                );
                                if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                                    continue;
                                }
                                return StepResult::ThreadFinished;
                            }
                            let val = ((hi as i64) << 32) | (lo as u32 as i64);
                            vm.heap.write_i64(objectref + byte_offset, val);
                        } else {
                            let val = stack.pop();
                            let objectref = stack.pop();
                            if objectref == NULL {
                                let class_name = vm
                                    .class_repo
                                    .get_class_name_by_id(method.class_id)
                                    .unwrap_or("");
                                let msg = format!(
                                    "java.lang.NullPointerException (PUTFIELD in {}.{})",
                                    class_name,
                                    vm.class_repo.get_string(method.name_id)
                                );
                                if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                                    continue;
                                }
                                return StepResult::ThreadFinished;
                            }
                            vm.heap.write_u32(objectref + byte_offset, val);
                        }
                    }
                }
                *pc += 3;
            }

            INVOKEVIRTUAL | INVOKESPECIAL | INVOKESTATIC | INVOKEINTERFACE => {
                let index = read_u16(code, *pc + 1);
                let invoke_pc = *pc;
                let invoke_size: usize = if op == INVOKEINTERFACE { 5 } else { 3 };

                if let Some(CachedCpEntry::Method {
                    method_id: base_mid,
                    vtable_index: base_vti,
                    imethod_id: base_imid,
                }) = vm.resolve_method_inline(caller_class_id, index)
                {
                    // Get the base (declaring) method from cache — O(1) Vec index
                    let base_method_ptr = vm.class_repo.get_method_ptr(base_mid);
                    if base_method_ptr.is_none() {
                        *pc = invoke_pc + invoke_size;
                    }
                    let base_method = match base_method_ptr {
                        Some(method) => unsafe { &*method },
                        None => {
                            continue;
                        }
                    };

                    if op == INVOKESTATIC {
                        let owner_class_id = base_method.class_id;
                        if let Some(clinit_id) = vm.ensure_initialized_by_id(owner_class_id) {
                            let (max_locals, max_stack) =
                                method_frame_layout(vm, clinit_id).unwrap_or((0, 0));
                            stack.push_frame_clinit(max_locals, max_stack, clinit_id, *pc);
                            *current_method = clinit_id;
                            *pc = 0;
                            continue;
                        }
                    }

                    let resolved: Option<u32> = if op == INVOKEVIRTUAL || op == INVOKEINTERFACE {
                        let param_slots = base_method.param_slot_count();
                        let obj_ref = stack.peek_at(param_slots - 1);
                        if obj_ref == NULL {
                            for _ in 0..param_slots {
                                stack.pop();
                            }
                            let base_class_name = vm
                                .class_repo
                                .get_class_name_by_id(base_method.class_id)
                                .unwrap_or("");
                            let caller_class_name = vm
                                .class_repo
                                .get_class_name_by_id(method.class_id)
                                .unwrap_or("");
                            let msg = format!(
                                "java.lang.NullPointerException (INVOKE {}.{} in {}.{})",
                                base_class_name,
                                vm.class_repo.get_string(base_method.name_id),
                                caller_class_name,
                                vm.class_repo.get_string(method.name_id)
                            );
                            if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                                continue;
                            }
                            return StepResult::ThreadFinished;
                        }
                        let actual_class_id = vm.get_class_id_of_object(obj_ref);
                        let result = if op == INVOKEINTERFACE {
                            // itable lookup using cached imethod_id — zero string hashing O(1)
                            if base_imid != u32::MAX {
                                vm.class_repo
                                    .itable_lookup_by_imethod_id(actual_class_id, base_imid)
                            } else {
                                None
                            }
                        } else {
                            // vtable lookup using cached vtable_index — zero allocation O(1)
                            if base_vti != u32::MAX {
                                vm.class_repo.vtable_lookup_by_id(actual_class_id, base_vti)
                            } else {
                                None
                            }
                        };
                        // Fallback to hierarchy search if vtable/itable miss
                        result.or_else(|| {
                            vm.class_repo.find_method_in_hierarchy_by_ids(
                                actual_class_id,
                                base_method.name_id,
                                base_method.descriptor_id,
                            )
                        })
                    } else {
                        // INVOKESTATIC / INVOKESPECIAL: base method IS the target
                        Some(base_mid)
                    };

                    if let Some(target_method_id) = resolved {
                        let Some(target_ptr) = vm.class_repo.get_method_ptr(target_method_id)
                        else {
                            *pc = invoke_pc + invoke_size;
                            continue;
                        };
                        // SAFETY: method metadata remains stable for the duration of execution.
                        let target = unsafe { &*target_ptr };
                        // --- Interpreter-level intercept for Class.forName / Class.newInstance ---
                        if target.is_native()
                            && vm
                                .class_repo
                                .get_class_name_by_id(target.class_id)
                                .unwrap_or("")
                                == "java.lang.Class"
                            && vm.class_repo.get_string(target.name_id) == "forName"
                            && vm.class_repo.get_string(target.descriptor_id)
                                == "(Ljava/lang/String;)Ljava/lang/Class;"
                        {
                            // Peek at the name argument (1 slot: the String ref)
                            let name_ref = stack.peek_at(0);
                            let target_class = vm
                                .get_string_value(name_ref)
                                .unwrap_or_default()
                                .replace('/', ".");
                            let class_id = vm.class_repo.get_class_id(&target_class).unwrap_or(0);
                            if class_id != 0 {
                                // JVM spec: Class.forName must trigger <clinit> before returning.
                                // If clinit is needed, push clinit frame with pc unchanged so we
                                // re-execute this invoke after clinit completes.
                                if let Some(clinit_id) = vm.ensure_initialized(&target_class) {
                                    let (max_locals, max_stack) =
                                        method_frame_layout(vm, clinit_id).unwrap_or((0, 0));
                                    stack.push_frame_clinit(
                                        max_locals, max_stack, clinit_id, invoke_pc,
                                    );
                                    *current_method = clinit_id;
                                    *pc = 0;
                                    continue;
                                }
                            }
                            // clinit done (or not needed) — run the native forName normally
                            native::dispatch(stack, vm, &target, _thread_id);
                            if let Some(exc_desc) = vm.pending_exception_str.take() {
                                if throw_runtime(
                                    stack,
                                    pc,
                                    current_method,
                                    vm,
                                    _thread_id,
                                    &exc_desc,
                                ) {
                                    continue;
                                }
                                return StepResult::ThreadFinished;
                            }
                            *pc = invoke_pc + invoke_size;
                        } else if target.is_native()
                            && vm
                                .class_repo
                                .get_class_name_by_id(target.class_id)
                                .unwrap_or("")
                                == "java.lang.Class"
                            && vm.class_repo.get_string(target.name_id) == "newInstance"
                            && vm.class_repo.get_string(target.descriptor_id)
                                == "()Ljava/lang/Object;"
                        {
                            // JVM spec: Class.newInstance must invoke the no-arg <init>.
                            let this_ref = stack.pop(); // pop 'this' (Class object)
                            let next_pc = invoke_pc + invoke_size;
                            if let Some(class_name) = vm.class_objects.get(&this_ref).cloned() {
                                // Check if class is an interface or abstract — throw InstantiationException
                                let is_interface_or_abstract = vm
                                    .class_repo
                                    .get_class(&class_name)
                                    .map_or(false, |c| c.is_interface() || c.is_abstract());
                                if is_interface_or_abstract {
                                    stack.push(NULL);
                                    if throw_runtime(
                                        stack,
                                        pc,
                                        current_method,
                                        vm,
                                        _thread_id,
                                        "java.lang.InstantiationException",
                                    ) {
                                        continue;
                                    }
                                    return StepResult::ThreadFinished;
                                }
                                let obj = vm.new_object(&class_name);
                                stack.push(obj); // return value for caller
                                                 // If <init>()V exists, push a frame to execute it
                                if let Some(init_method_id) = vm
                                    .class_repo
                                    .find_method_in_hierarchy(&class_name, "<init>", "()V")
                                {
                                    let init_method_ptr = vm
                                        .class_repo
                                        .get_method_ptr(init_method_id)
                                        .expect("resolved init method must remain in arena");
                                    let init_method = unsafe { &*init_method_ptr };
                                    if init_method.is_native() {
                                        // native <init> (e.g. Object.<init>) — dispatch directly
                                        stack.push(obj);
                                        native::dispatch(stack, vm, init_method, _thread_id);
                                    } else {
                                        // push 'this' as arg, then zero-copy invoke
                                        // Store opPC (invoke_pc) so exception handler sees the correct PC
                                        stack.push(obj);
                                        stack.push_frame_for_invoke(
                                            init_method.max_locals() as usize,
                                            init_method.max_stack() as usize,
                                            1,
                                            init_method.method_id,
                                            ThreadStack::encode_invoke_pc(
                                                invoke_pc,
                                                invoke_size as u8,
                                            ),
                                        );
                                        *current_method = init_method.method_id;
                                        *pc = 0;
                                        continue;
                                    }
                                }
                            } else {
                                stack.push(NULL);
                            }
                            *pc = next_pc;
                        } else if target.is_native() {
                            if let Some(yr) = native::dispatch(stack, vm, &target, _thread_id) {
                                // Most native yields (wait, sleep) have already consumed their args
                                // and performed side effects, so we advance PC past the invoke.
                                // Exception: AsyncWait and NativeEventWait re-execute the invoke
                                // on resume, so their PC must NOT advance.
                                if !matches!(
                                    yr,
                                    YieldReason::AsyncWait { .. }
                                        | YieldReason::NativeEventWait { .. }
                                ) {
                                    *pc = invoke_pc + invoke_size;
                                }
                                return StepResult::Yielded(yr);
                            }
                            // Check if native method set a pending exception
                            if let Some(exc_desc) = vm.pending_exception_str.take() {
                                if throw_runtime(
                                    stack,
                                    pc,
                                    current_method,
                                    vm,
                                    _thread_id,
                                    &exc_desc,
                                ) {
                                    continue;
                                }
                                return StepResult::ThreadFinished;
                            }
                            *pc = invoke_pc + invoke_size;
                        } else {
                            let param_slots = target.param_slot_count();
                            let ml = target.max_locals() as usize;

                            // For synchronized methods, check monitor BEFORE pushing frame.
                            // Zero-copy invoke means pop_frame can't cleanly undo push_frame_for_invoke.
                            if target.is_synchronized() {
                                let monitor_obj = if target.is_static() {
                                    vm.static_fields.get_addr_by_id(target.class_id)
                                } else {
                                    // 'this' is on the stack at depth = param_slots - 1 from top
                                    stack.peek_at(param_slots - 1)
                                };
                                if monitor_obj != NULL {
                                    let entered = vm
                                        .monitors
                                        .entry(monitor_obj)
                                        .or_default()
                                        .try_enter(_thread_id);
                                    if !entered {
                                        // Monitor contended: don't push frame, re-execute invoke on resume
                                        *pc = invoke_pc;
                                        return StepResult::Yielded(YieldReason::MonitorBlocked {
                                            obj_id: monitor_obj,
                                        });
                                    }
                                }
                            }

                            // Store opPC (invoke instruction start) as caller_pc,
                            // so exception handler sees the correct PC for table matching.
                            stack.push_frame_for_invoke(
                                ml,
                                target.max_stack() as usize,
                                param_slots,
                                target.method_id,
                                ThreadStack::encode_invoke_pc(invoke_pc, invoke_size as u8),
                            );

                            if target.is_synchronized() {
                                let monitor_obj = if target.is_static() {
                                    vm.static_fields.get_addr_by_id(target.class_id)
                                } else {
                                    stack.local(0, ml) // this ref
                                };
                                if monitor_obj != NULL {
                                    stack.set_monitor(stack.fp, monitor_obj);
                                }
                            }

                            *pc = 0;
                            *current_method = target.method_id;
                        }
                    } else {
                        let param_slots = base_method.param_slot_count();
                        if vm.debug {
                            let warning = format!(
                                "[WARN] Method not found: {}.{}{}",
                                vm.class_repo
                                    .get_class_name_by_id(base_method.class_id)
                                    .unwrap_or(""),
                                vm.class_repo.get_string(base_method.name_id),
                                vm.class_repo.get_string(base_method.descriptor_id)
                            );
                            vm.println(&warning);
                        }
                        // Pop params and push default return value
                        for _ in 0..param_slots {
                            stack.pop();
                        }
                        let base_desc = vm.class_repo.get_string(base_method.descriptor_id);
                        if !base_desc.ends_with(")V") {
                            stack.push(0);
                            if base_desc.ends_with(")J") || base_desc.ends_with(")D") {
                                stack.push(0);
                            }
                        }
                        *pc = invoke_pc + invoke_size;
                    }
                } else {
                    // The target class is not loaded yet. Resolution remains ID-only;
                    // retrying after class initialization/loading will populate the CP cache.
                    *pc = invoke_pc + invoke_size;
                }

                if should_yield_timeslice(vm, instruction_count, max_instructions) {
                    return StepResult::Yielded(YieldReason::TimeSlice);
                }
            }

            NEW => {
                let index = read_u16(code, *pc + 1);
                if let Some(class_id) = vm.resolve_class_inline(caller_class_id, index) {
                    if let Some(clinit_id) = vm.ensure_initialized_by_id(class_id) {
                        let (max_locals, max_stack) =
                            method_frame_layout(vm, clinit_id).unwrap_or((0, 0));
                        stack.push_frame_clinit(max_locals, max_stack, clinit_id, *pc);
                        *current_method = clinit_id;
                        *pc = 0;
                        continue;
                    }
                    let addr = vm.new_object_by_id(class_id);
                    stack.push(addr);
                } else {
                    let class_name = vm
                        .resolve_class_name_cached_by_id(caller_class_id, index)
                        .unwrap_or_else(|| format!("cp#{}", index));
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    vm.println(&format!(
                        "[WARN] Class not loaded: {} (referenced in {}.{})",
                        class_name,
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    ));
                    stack.push(NULL);
                }
                *pc += 3;
            }

            NEWARRAY => {
                let count = stack.pop();
                if (count as i32) < 0 {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NegativeArraySizeException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let atype = code[*pc + 1];
                let array_class = match atype {
                    4 => "[Z",
                    5 => "[C",
                    6 => "[F",
                    7 => "[D",
                    8 => "[B",
                    9 => "[S",
                    10 => "[I",
                    11 => "[J",
                    _ => "[I",
                };
                let class_id = vm.class_repo.get_class_id(array_class).unwrap_or(0);
                let bpe = vm.class_repo.get_array_bpe(class_id);
                let bpe = if bpe == 0 { 4 } else { bpe as u32 };
                stack.push(vm.new_array(class_id, count, bpe));
                *pc += 2;
            }

            ANEWARRAY => {
                let count = stack.pop();
                if (count as i32) < 0 {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NegativeArraySizeException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let component_name = vm
                    .resolve_class_name_cached_by_id(method.class_id, read_u16(code, *pc + 1))
                    .unwrap_or_else(|| "java.lang.Object".to_string());
                let array_name = if component_name.starts_with('[') {
                    format!("[{}", component_name)
                } else {
                    format!("[L{};", component_name)
                };
                let class_id = vm.class_repo.get_or_register_array_class(&array_name);
                stack.push(vm.new_array(class_id, count, 4));
                *pc += 3;
            }

            ARRAYLENGTH => {
                let arrayref = stack.pop();
                if arrayref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_length(arrayref));
                *pc += 1;
            }

            CHECKCAST => {
                let index = read_u16(code, *pc + 1);
                let objectref = stack.peek_at(0);
                if objectref != NULL {
                    if let Some(target_class_id) = vm.resolve_class_inline(caller_class_id, index) {
                        let obj_class_id = vm.get_class_id_of_object(objectref);
                        if !vm
                            .class_repo
                            .is_assignable_by_id(obj_class_id, target_class_id)
                        {
                            let obj_class = vm
                                .class_repo
                                .get_class_name_by_id(obj_class_id)
                                .unwrap_or_default();
                            let target_class = vm
                                .class_repo
                                .get_class_name_by_id(target_class_id)
                                .unwrap_or_default();
                            let msg = format!(
                                "java.lang.ClassCastException ({} cannot be cast to {})",
                                obj_class, target_class
                            );
                            stack.pop(); // pop the objectref before throwing
                            if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                                continue;
                            }
                            return StepResult::ThreadFinished;
                        }
                    }
                }
                *pc += 3;
            }
            INSTANCEOF => {
                let index = read_u16(code, *pc + 1);
                let objectref = stack.pop();
                if objectref == NULL {
                    stack.push(0);
                } else {
                    if let Some(target_class_id) = vm.resolve_class_inline(caller_class_id, index) {
                        let obj_class_id = vm.get_class_id_of_object(objectref);
                        let result = vm
                            .class_repo
                            .is_assignable_by_id(obj_class_id, target_class_id);
                        stack.push(if result { 1 } else { 0 });
                    } else {
                        stack.push(0);
                    }
                }
                *pc += 3;
            }

            MONITORENTER => {
                let obj = stack.pop();
                if obj == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let monitor = vm.monitors.entry(obj).or_default();
                if !monitor.try_enter(_thread_id) {
                    stack.push(obj);
                    return StepResult::Yielded(YieldReason::MonitorBlocked { obj_id: obj });
                }
                vm.current_monitor_depth += 1;
                *pc += 1;
            }
            MONITOREXIT => {
                let obj = stack.pop();
                if obj == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                if let Some(monitor) = vm.monitors.get_mut(&obj) {
                    if let Some(next_tid) = monitor.exit(_thread_id) {
                        vm.monitor_transfer_wakeups.push(next_tid);
                    }
                    // Decrement depth when fully releasing (recursion went to 0 means we exited)
                    if monitor.owner != Some(_thread_id) {
                        if vm.current_monitor_depth > 0 {
                            vm.current_monitor_depth -= 1;
                        }
                    }
                }
                *pc += 1;
            }

            ATHROW => {
                let exception_ref = stack.pop();
                if exception_ref == NULL {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NullPointerException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                if handle_exception(stack, pc, current_method, vm, _thread_id, exception_ref) {
                    continue;
                }
                return StepResult::ThreadFinished;
            }

            WIDE => {
                let wide_op = code[*pc + 1];
                let ml = method.max_locals() as usize;
                match wide_op {
                    ILOAD | FLOAD | ALOAD => {
                        let idx = read_u16(code, *pc + 2) as usize;
                        stack.push(stack.local(idx, ml));
                        *pc += 4;
                    }
                    LLOAD | DLOAD => {
                        let idx = read_u16(code, *pc + 2) as usize;
                        let lo = stack.local(idx, ml);
                        let hi = stack.local(idx + 1, ml);
                        stack.push(lo);
                        stack.push(hi);
                        *pc += 4;
                    }
                    ISTORE | FSTORE | ASTORE => {
                        let idx = read_u16(code, *pc + 2) as usize;
                        let val = stack.pop();
                        stack.set_local(idx, ml, val);
                        *pc += 4;
                    }
                    LSTORE | DSTORE => {
                        let idx = read_u16(code, *pc + 2) as usize;
                        let hi = stack.pop();
                        let lo = stack.pop();
                        stack.set_local(idx, ml, lo);
                        stack.set_local(idx + 1, ml, hi);
                        *pc += 4;
                    }
                    IINC => {
                        let idx = read_u16(code, *pc + 2) as usize;
                        let inc = read_i16(code, *pc + 4) as i32;
                        let val = stack.local(idx, ml) as i32;
                        stack.set_local(idx, ml, val.wrapping_add(inc) as u32);
                        *pc += 6;
                    }
                    RET => {
                        let idx = read_u16(code, *pc + 2) as usize;
                        *pc = stack.local(idx, ml) as usize;
                    }
                    _ => {
                        *pc += 2;
                    }
                }
            }

            JSR => {
                let offset = read_i16(code, *pc + 1) as i32;
                stack.push((*pc + 3) as u32);
                *pc = (*pc as i32 + offset) as usize;
            }
            JSR_W => {
                let offset = read_i32(code, *pc + 1);
                stack.push((*pc + 5) as u32);
                *pc = (*pc as i32 + offset) as usize;
            }
            RET => {
                let idx = code[*pc + 1] as usize;
                *pc = stack.local(idx, method.max_locals() as usize) as usize;
            }

            MULTIANEWARRAY => {
                let cp_index = read_u16(code, *pc + 1) as usize;
                let dims = code[*pc + 3] as usize;
                // Pop dimension sizes (first popped = last dimension)
                let mut sizes = vec![0u32; dims];
                for i in (0..dims).rev() {
                    sizes[i] = stack.pop();
                }
                // Check for negative sizes
                let neg = sizes.iter().any(|&s| (s as i32) < 0);
                if neg {
                    if throw_runtime(
                        stack,
                        pc,
                        current_method,
                        vm,
                        _thread_id,
                        "java.lang.NegativeArraySizeException",
                    ) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                // Resolve array type descriptor from constant pool
                let descriptor = vm
                    .resolve_class_name_cached_by_id(method.class_id, cp_index as u16)
                    .map(|s| s.replace('.', "/"))
                    .unwrap_or_default();
                // Determine inner element size from the descriptor
                // e.g. "[[I" -> innermost is 'I' (4 bytes), "[[B" -> 'B' (1 byte)
                let innermost = descriptor.chars().last().unwrap_or('I');
                let inner_bpe: u32 = match innermost {
                    'B' | 'Z' => 1,
                    'S' | 'C' => 2,
                    'J' | 'D' => 8,
                    _ => 4, // I, F, L, [
                };
                // Recursively allocate the multi-dimensional array
                let arr = alloc_multi_array(vm, &sizes, 0, dims, inner_bpe, &descriptor);
                stack.push(arr);
                *pc += 4;
            }

            // ─── Super-instructions ───────────────────────────────────────────
            SUPER_ALOAD0_GETFIELD => {
                let objectref = stack.local(0, method.max_locals() as usize);
                if objectref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (GETFIELD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let index = read_u16(code, *pc + 2);
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index) {
                    if let CachedCpEntry::Field {
                        owner_class_id: _,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if is_wide {
                            push_long(stack, vm.heap.read_i64(objectref + byte_offset));
                        } else {
                            stack.push(vm.heap.read_u32(objectref + byte_offset));
                        }
                    }
                }
                *pc += 4;
            }
            SUPER_ALOAD0_GETFIELD_GETFIELD => {
                let objectref = stack.local(0, method.max_locals() as usize);
                if objectref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (GETFIELD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let index1 = read_u16(code, *pc + 2);
                let intermediate =
                    if let Some(entry) = vm.resolve_field_inline(caller_class_id, index1) {
                        if let CachedCpEntry::Field {
                            owner_class_id: _,
                            byte_offset,
                            is_wide: false,
                            is_static: _,
                        } = entry
                        {
                            vm.heap.read_u32(objectref + byte_offset)
                        } else {
                            0
                        }
                    } else {
                        0
                    };
                if intermediate == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (GETFIELD chain in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let index2 = read_u16(code, *pc + 5);
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index2) {
                    if let CachedCpEntry::Field {
                        owner_class_id: _,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if is_wide {
                            push_long(stack, vm.heap.read_i64(intermediate + byte_offset));
                        } else {
                            stack.push(vm.heap.read_u32(intermediate + byte_offset));
                        }
                    }
                }
                *pc += 7;
            }
            SUPER_ALOAD0_ICONST0_PUTFIELD => {
                let objectref = stack.local(0, method.max_locals() as usize);
                if objectref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (PUTFIELD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let index = read_u16(code, *pc + 3);
                if let Some(entry) = vm.resolve_field_inline(caller_class_id, index) {
                    if let CachedCpEntry::Field {
                        owner_class_id: _,
                        byte_offset,
                        is_wide,
                        is_static: _,
                    } = entry
                    {
                        if is_wide {
                            vm.heap.write_i64(objectref + byte_offset, 0i64);
                        } else {
                            vm.heap.write_u32(objectref + byte_offset, 0);
                        }
                    }
                }
                *pc += 5;
            }
            SUPER_ALOAD0_GETFIELD_BIPUSH_AALOAD => {
                let objectref = stack.local(0, method.max_locals() as usize);
                if objectref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (GETFIELD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let field_index = read_u16(code, *pc + 2);
                let arrayref =
                    if let Some(entry) = vm.resolve_field_inline(caller_class_id, field_index) {
                        if let CachedCpEntry::Field {
                            owner_class_id: _,
                            byte_offset,
                            is_wide: false,
                            is_static: _,
                        } = entry
                        {
                            vm.heap.read_u32(objectref + byte_offset)
                        } else {
                            0
                        }
                    } else {
                        0
                    };
                if arrayref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (AALOAD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let arr_index = code[*pc + 5] as i8 as i32 as u32;
                let len = vm.heap.array_length(arrayref);
                if (arr_index as i32) < 0 || arr_index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        arr_index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_read_u32(arrayref, arr_index));
                *pc += 7;
            }
            SUPER_ALOAD0_GETFIELD_ILOAD1_AALOAD => {
                let objectref = stack.local(0, method.max_locals() as usize);
                if objectref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (GETFIELD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let field_index = read_u16(code, *pc + 2);
                let arrayref =
                    if let Some(entry) = vm.resolve_field_inline(caller_class_id, field_index) {
                        if let CachedCpEntry::Field {
                            owner_class_id: _,
                            byte_offset,
                            is_wide: false,
                            is_static: _,
                        } = entry
                        {
                            vm.heap.read_u32(objectref + byte_offset)
                        } else {
                            0
                        }
                    } else {
                        0
                    };
                if arrayref == NULL {
                    let caller_name = vm
                        .class_repo
                        .get_class_name_by_id(method.class_id)
                        .unwrap_or("")
                        .to_string();
                    let msg = format!(
                        "java.lang.NullPointerException (AALOAD in {}.{})",
                        caller_name,
                        vm.class_repo.get_string(method.name_id)
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                let arr_index = stack.local(1, method.max_locals() as usize);
                let len = vm.heap.array_length(arrayref);
                if (arr_index as i32) < 0 || arr_index >= len {
                    let msg = format!(
                        "java.lang.ArrayIndexOutOfBoundsException ({})",
                        arr_index as i32
                    );
                    if throw_runtime(stack, pc, current_method, vm, _thread_id, &msg) {
                        continue;
                    }
                    return StepResult::ThreadFinished;
                }
                stack.push(vm.heap.array_read_u32(arrayref, arr_index));
                *pc += 6;
            }

            _ => {
                if vm.debug {
                    eprintln!("Unknown opcode: 0x{:02X} at pc={}", op, *pc);
                }
                *pc += 1;
            }
        }
    }
}

fn release_monitor_if_synchronized(stack: &mut ThreadStack, vm: &mut SharedVm, tid: ThreadId) {
    let monitor_obj = stack.monitor_at(stack.fp);
    if monitor_obj != NULL {
        if let Some(monitor) = vm.monitors.get_mut(&monitor_obj) {
            if let Some(next_tid) = monitor.exit(tid) {
                vm.monitor_transfer_wakeups.push(next_tid);
            }
        }
    }
}

fn handle_exception(
    stack: &mut ThreadStack,
    pc: &mut usize,
    current_method: &mut u32,
    vm: &mut SharedVm,
    tid: ThreadId,
    exception_ref: u32,
) -> bool {
    let exception_class = vm
        .get_class_name_of_object(exception_ref)
        .unwrap_or("java.lang.Throwable")
        .to_string();

    let mut trace_frames: Vec<String> = Vec::new();

    loop {
        if !stack.has_frames() {
            return false;
        }

        let method = match vm.class_repo.get_method(*current_method) {
            Some(m) => m.clone(),
            None => {
                return false;
            }
        };

        let current_pc = *pc as u16;

        for entry in method.exception_table() {
            if current_pc >= entry.start_pc && current_pc < entry.end_pc {
                let matches = if entry.catch_type == 0 {
                    true
                } else {
                    let cp = vm.class_repo.get_constant_pool_by_id(method.class_id);
                    resolve_class_name(&vm.class_repo, cp, entry.catch_type)
                        .map_or(false, |catch_name| {
                            vm.class_repo.is_assignable(&exception_class, &catch_name)
                        })
                };
                if matches {
                    stack.reset_operand_stack();
                    stack.push(exception_ref);
                    *pc = entry.handler_pc as usize;
                    return true;
                }
            }
        }

        release_monitor_if_synchronized(stack, vm, tid);
        let method_class_name = vm
            .class_repo
            .get_class_name_by_id(method.class_id)
            .unwrap_or("")
            .to_string();
        trace_frames.push(format!(
            "  at {}.{}(pc={})",
            method_class_name,
            vm.class_repo.get_string(method.name_id),
            current_pc
        ));
        let (_caller_fp, caller_pc) = stack.pop_frame(vm.class_repo.max_locals_table());

        if !stack.has_frames() {
            {
                let detail = vm
                    .class_repo
                    .find_field_offset("java.lang.Throwable", "detailMessage")
                    .map(|off| vm.heap.read_u32(exception_ref + off))
                    .filter(|&r| r != NULL)
                    .map(|r| vm.get_string_value(r).unwrap_or_default())
                    .unwrap_or_default();
                let summary = if detail.is_empty() {
                    format!(
                        "Unhandled exception: {} at {}.{}",
                        exception_class,
                        method_class_name,
                        vm.class_repo.get_string(method.name_id)
                    )
                } else {
                    format!(
                        "Unhandled exception: {} at {}.{}: {}",
                        exception_class,
                        method_class_name,
                        vm.class_repo.get_string(method.name_id),
                        detail
                    )
                };
                let mut report = summary.clone();
                for frame in &trace_frames {
                    report.push('\n');
                    report.push_str(frame);
                }
                #[cfg(not(target_arch = "wasm32"))]
                crate::host_native::emit_error(&report);
                vm.println(&format!("[WARN] {}", summary));
                for frame in &trace_frames {
                    vm.println(frame);
                }
            }
            return false;
        }

        *current_method = stack.method_id_at(stack.fp);
        // caller_pc is now the opPC (invoke instruction start), use it directly
        // for exception table matching — no need for the old `caller_pc - 1` hack.
        *pc = caller_pc;
    }
}

fn resolve_class_name(class_repo: &ClassRepository, cp: &[CpEntry], index: u16) -> Option<String> {
    match cp.get(index as usize) {
        Some(CpEntry::Class { name_index }) => class_repo
            .resolve_utf8(cp, *name_index)
            .map(|s| s.replace('/', ".")),
        _ => None,
    }
}

fn throw_runtime(
    stack: &mut ThreadStack,
    pc: &mut usize,
    current_method: &mut u32,
    vm: &mut SharedVm,
    tid: ThreadId,
    class_name: &str,
) -> bool {
    let (actual_class, message) = if let Some(idx) = class_name.find(" (") {
        let msg = &class_name[idx + 2..];
        // Strip trailing ')' if present
        let msg = msg.strip_suffix(')').unwrap_or(msg);
        (&class_name[..idx], Some(msg))
    } else {
        (class_name, None)
    };
    let exception_ref = vm.new_object(actual_class);
    if let Some(msg) = message {
        if let Some(off) = vm
            .class_repo
            .find_field_offset("java.lang.Throwable", "detailMessage")
        {
            let msg_ref = vm.create_string(msg);
            vm.heap.write_u32(exception_ref + off, msg_ref);
        }
    }
    handle_exception(stack, pc, current_method, vm, tid, exception_ref)
}

fn read_u16(code: &[u8], offset: usize) -> u16 {
    ((code[offset] as u16) << 8) | (code[offset + 1] as u16)
}

fn read_i16(code: &[u8], offset: usize) -> i16 {
    read_u16(code, offset) as i16
}

fn read_i32(code: &[u8], offset: usize) -> i32 {
    ((code[offset] as i32) << 24)
        | ((code[offset + 1] as i32) << 16)
        | ((code[offset + 2] as i32) << 8)
        | (code[offset + 3] as i32)
}

fn to_long(lo: u32, hi: u32) -> i64 {
    ((hi as i64) << 32) | (lo as u32 as i64)
}

fn push_long(stack: &mut ThreadStack, val: i64) {
    stack.push(val as u32);
    stack.push((val >> 32) as u32);
}

fn pop_double(stack: &mut ThreadStack) -> f64 {
    let hi = stack.pop();
    let lo = stack.pop();
    f64::from_bits(((hi as u64) << 32) | (lo as u64))
}

fn push_double(stack: &mut ThreadStack, val: f64) {
    let bits = val.to_bits();
    stack.push(bits as u32);
    stack.push((bits >> 32) as u32);
}

fn pop_long_pair(stack: &mut ThreadStack) -> (u32, u32, u32, u32) {
    let bh = stack.pop();
    let bl = stack.pop();
    let ah = stack.pop();
    let al = stack.pop();
    (bh, bl, ah, al)
}

fn branch_if(cond: bool, code: &[u8], pc: &mut usize, poll_safepoint: &mut bool) {
    if cond {
        let offset = read_i16(code, *pc + 1) as i32;
        *pc = (*pc as i32 + offset) as usize;
        *poll_safepoint = offset < 0;
    } else {
        *pc += 3;
    }
}

fn push_ldc(stack: &mut ThreadStack, caller_class_id: u32, index: u16, vm: &mut SharedVm) {
    // Fast path: inline cache hit
    let cached = vm.class_repo.get_inline_cache(caller_class_id, index);
    match cached {
        CachedCpEntry::LdcInt { value } => {
            stack.push(value);
            return;
        }
        CachedCpEntry::LdcString { heap_addr } => {
            stack.push(heap_addr);
            return;
        }
        _ => {}
    }

    // Slow path: resolve from CP
    enum LdcVal {
        Int(u32),
        Float(u32),
        Str(u32),
        Zero,
    } // Str(string_id)
    let val = {
        let cp = vm.class_repo.get_constant_pool_by_id(caller_class_id);
        match cp.get(index as usize) {
            Some(CpEntry::Integer(v)) => LdcVal::Int(*v as u32),
            Some(CpEntry::Float(v)) => LdcVal::Float(v.to_bits()),
            Some(CpEntry::String { string_index }) => match cp.get(*string_index as usize) {
                Some(CpEntry::Utf8 { string_id }) => LdcVal::Str(*string_id),
                _ => LdcVal::Zero,
            },
            _ => LdcVal::Zero,
        }
    };
    match val {
        LdcVal::Int(v) => {
            vm.class_repo.set_inline_cache(
                caller_class_id,
                index,
                CachedCpEntry::LdcInt { value: v },
            );
            stack.push(v);
        }
        LdcVal::Float(bits) => {
            vm.class_repo.set_inline_cache(
                caller_class_id,
                index,
                CachedCpEntry::LdcInt { value: bits },
            );
            stack.push(bits);
        }
        LdcVal::Str(string_id) => {
            // 快速路径：by_string_id 已缓存
            let cached_addr = vm.heap.get_interned_by_string_id(string_id);
            let handle = if cached_addr != 0 {
                cached_addr
            } else {
                let s = vm.class_repo.get_string(string_id).to_string();
                let addr = vm.create_string(&s);
                vm.heap.set_interned_by_string_id(string_id, addr);
                // 同时注册到 dynamic intern pool，确保 String.intern() 能找到常量池字符串
                vm.heap.intern_string_dynamic(&s, addr);
                addr
            };
            vm.class_repo.set_inline_cache(
                caller_class_id,
                index,
                CachedCpEntry::LdcString { heap_addr: handle },
            );
            stack.push(handle);
        }
        LdcVal::Zero => stack.push(0),
    }
}

fn push_ldc2(stack: &mut ThreadStack, caller_class_id: u32, index: u16, vm: &mut SharedVm) {
    // Fast path: inline cache hit
    let cached = vm.class_repo.get_inline_cache(caller_class_id, index);
    if let CachedCpEntry::Ldc2Wide { lo, hi } = cached {
        stack.push(lo);
        stack.push(hi);
        return;
    }

    // Slow path: resolve from CP
    let cp = vm.class_repo.get_constant_pool_by_id(caller_class_id);
    match cp.get(index as usize) {
        Some(CpEntry::Long(v)) => {
            let lo = *v as u32;
            let hi = (*v >> 32) as u32;
            vm.class_repo.set_inline_cache(
                caller_class_id,
                index,
                CachedCpEntry::Ldc2Wide { lo, hi },
            );
            stack.push(lo);
            stack.push(hi);
        }
        Some(CpEntry::Double(v)) => {
            let bits = v.to_bits();
            let lo = bits as u32;
            let hi = (bits >> 32) as u32;
            vm.class_repo.set_inline_cache(
                caller_class_id,
                index,
                CachedCpEntry::Ldc2Wide { lo, hi },
            );
            stack.push(lo);
            stack.push(hi);
        }
        _ => {
            stack.push(0);
            stack.push(0);
        }
    }
}

fn f32_to_i32(v: f32) -> i32 {
    if v.is_nan() {
        0
    } else if v >= i32::MAX as f32 {
        i32::MAX
    } else if v <= i32::MIN as f32 {
        i32::MIN
    } else {
        v as i32
    }
}

fn f32_to_i64(v: f32) -> i64 {
    if v.is_nan() {
        0
    } else if v >= i64::MAX as f32 {
        i64::MAX
    } else if v <= i64::MIN as f32 {
        i64::MIN
    } else {
        v as i64
    }
}

fn f64_to_i32(v: f64) -> i32 {
    if v.is_nan() {
        0
    } else if v >= i32::MAX as f64 {
        i32::MAX
    } else if v <= i32::MIN as f64 {
        i32::MIN
    } else {
        v as i32
    }
}

fn f64_to_i64(v: f64) -> i64 {
    if v.is_nan() {
        0
    } else if v >= i64::MAX as f64 {
        i64::MAX
    } else if v <= i64::MIN as f64 {
        i64::MIN
    } else {
        v as i64
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::heap::OBJ_HDR_SIZE;
    use std::cell::RefCell;
    use std::rc::Rc;

    fn new_test_vm() -> SharedVm {
        SharedVm::new(Rc::new(RefCell::new(1)), false)
    }

    fn add_simple_static_method(
        vm: &mut SharedVm,
        class_name: &str,
        method_name: &str,
        code: Vec<u8>,
    ) -> u32 {
        let method_id = vm.class_repo.alloc_method_id();
        let class_id = 1;
        let method = RuntimeMethod {
            name_id: vm.class_repo.intern_string(method_name),
            descriptor_id: vm.class_repo.intern_string("()V"),
            access_flags: ACC_STATIC,
            code: Some(CodeAttribute {
                max_stack: 2,
                max_locals: 0,
                code,
                exception_table: vec![],
                attributes: vec![],
            }),
            class_id,
            method_id,
            vtable_index: u32::MAX,
            imethod_id: u32::MAX,
            native_method_id: u32::MAX,
            param_slots: 0,
        };

        let rc = RuntimeClass {
            name: class_name.to_string(),
            super_class_id: 0,
            interface_ids: vec![],
            all_interface_ids: vec![],
            access_flags: ACC_PUBLIC,
            constant_pool: vec![CpEntry::Empty],
            methods: vec![method_id],
            fields: vec![],
            class_id,
            size_of_fields: 0,
            size_of_static_fields: 0,
            initialized: true,
            depth: 0,
            display: vec![class_id],
            vtable: Vec::new(),
            itable: Vec::new(),
            ref_bitmap: Vec::new(),
            static_ref_bitmap: Vec::new(),
            inline_cache: Vec::new(),
        };

        vm.class_repo.add_class(rc, vec![method]);
        method_id
    }

    /// Intern test CP strings and return a valid CP.
    fn make_cp_interned(
        vm: &mut SharedVm,
        class_name: &str,
        field_name: &str,
        descriptor: &str,
    ) -> Vec<CpEntry> {
        vec![
            CpEntry::Empty,
            CpEntry::Fieldref {
                class_index: 2,
                name_and_type_index: 3,
            },
            CpEntry::Class { name_index: 4 },
            CpEntry::NameAndType {
                name_index: 5,
                descriptor_index: 6,
            },
            CpEntry::Utf8 {
                string_id: vm.class_repo.intern_string(&class_name.replace('.', "/")),
            },
            CpEntry::Utf8 {
                string_id: vm.class_repo.intern_string(field_name),
            },
            CpEntry::Utf8 {
                string_id: vm.class_repo.intern_string(descriptor),
            },
        ]
    }

    #[test]
    fn test_clinit_triggered_by_getstatic() {
        let mut vm = new_test_vm();

        let clinit_id = vm.class_repo.alloc_method_id();
        let main_id = vm.class_repo.alloc_method_id();

        let cp = make_cp_interned(&mut vm, "TestClass", "x", "I");

        let clinit_code = vec![BIPUSH, 42, PUTSTATIC, 0, 1, RETURN];

        let main_code = vec![GETSTATIC, 0, 1, IRETURN];

        let clinit = RuntimeMethod {
            name_id: vm.class_repo.intern_string("<clinit>"),
            descriptor_id: vm.class_repo.intern_string("()V"),
            access_flags: ACC_STATIC,
            code: Some(CodeAttribute {
                max_stack: 2,
                max_locals: 0,
                code: clinit_code,
                exception_table: vec![],
                attributes: vec![],
            }),
            class_id: 1,
            method_id: clinit_id,
            vtable_index: u32::MAX,
            imethod_id: u32::MAX,
            native_method_id: u32::MAX,
            param_slots: 0,
        };

        let main_method = RuntimeMethod {
            name_id: vm.class_repo.intern_string("main"),
            descriptor_id: vm.class_repo.intern_string("()V"),
            access_flags: ACC_STATIC,
            code: Some(CodeAttribute {
                max_stack: 2,
                max_locals: 1,
                code: main_code,
                exception_table: vec![],
                attributes: vec![],
            }),
            class_id: 1,
            method_id: main_id,
            vtable_index: u32::MAX,
            imethod_id: u32::MAX,
            native_method_id: u32::MAX,
            param_slots: 0,
        };

        let rc = RuntimeClass {
            name: "TestClass".to_string(),
            super_class_id: 0,
            interface_ids: vec![],
            all_interface_ids: vec![],
            access_flags: ACC_PUBLIC,
            constant_pool: cp,
            methods: vec![clinit_id, main_id],
            fields: vec![RuntimeField {
                name_id: vm.class_repo.intern_string("x"),
                descriptor_id: vm.class_repo.intern_string("I"),
                access_flags: ACC_STATIC,
                class_id: 0,
                byte_offset: OBJ_HDR_SIZE,
                wide: false,
                reference: false,
            }],
            class_id: 0,
            size_of_fields: 0,
            size_of_static_fields: 4,
            initialized: false,
            depth: 0,
            display: vec![0],
            vtable: Vec::new(),
            itable: Vec::new(),
            ref_bitmap: Vec::new(),
            static_ref_bitmap: Vec::new(),
            inline_cache: Vec::new(),
        };

        vm.class_repo
            .add_class(rc, vec![clinit.clone(), main_method.clone()]);

        let mut stack = ThreadStack::new();
        stack.push_frame(
            main_method.max_locals() as usize,
            main_method.max_stack() as usize,
            main_id,
            0,
        );
        let mut pc: usize = 0;
        let mut current_method = main_id;

        let mut steps = 0;
        loop {
            let result = step(&mut stack, &mut pc, &mut current_method, 0, &mut vm, 100);
            steps += 1;
            match result {
                StepResult::ThreadFinished => break,
                StepResult::Yielded(_) => {}
            }
            if steps > 20 {
                panic!("too many steps");
            }
        }

        let static_addr = vm.static_fields.get_addr("TestClass");
        let x_val = vm.heap.read_u32(static_addr + OBJ_HDR_SIZE);
        assert_eq!(x_val, 42);
    }

    #[test]
    fn test_does_not_timeslice_mid_basic_block_while_holding_monitor() {
        let mut vm = new_test_vm();
        let method_id =
            add_simple_static_method(&mut vm, "SafePointTest", "run", vec![NOP, NOP, RETURN]);
        vm.current_monitor_depth = 1;

        let mut stack = ThreadStack::new();
        stack.push_frame(0, 0, method_id, 0);
        let mut pc = 0usize;
        let mut current_method = method_id;

        let result = step(&mut stack, &mut pc, &mut current_method, 1, &mut vm, 1);
        assert!(matches!(result, StepResult::ThreadFinished));
    }

    #[test]
    fn test_timeslice_only_on_loop_backedge() {
        let mut vm = new_test_vm();
        let method_id = add_simple_static_method(
            &mut vm,
            "LoopSafePointTest",
            "run",
            vec![NOP, GOTO, 0xFF, 0xFF],
        );

        let mut stack = ThreadStack::new();
        stack.push_frame(0, 0, method_id, 0);
        let mut pc = 0usize;
        let mut current_method = method_id;

        let result = step(&mut stack, &mut pc, &mut current_method, 1, &mut vm, 1);
        assert!(matches!(
            result,
            StepResult::Yielded(YieldReason::TimeSlice)
        ));
    }

    #[test]
    fn test_conditional_loop_backedge_is_a_safepoint() {
        let mut vm = new_test_vm();
        let method_id = add_simple_static_method(
            &mut vm,
            "ConditionalLoopSafePointTest",
            "run",
            vec![ICONST_0, IFEQ, 0xFF, 0xFF],
        );

        let mut stack = ThreadStack::new();
        stack.push_frame(0, 1, method_id, 0);
        let mut pc = 0usize;
        let mut current_method = method_id;

        let result = step(&mut stack, &mut pc, &mut current_method, 1, &mut vm, 1);
        assert!(matches!(
            result,
            StepResult::Yielded(YieldReason::TimeSlice)
        ));
    }
}
