use crate::classfile::opcode::*;

// Rewrite bytecode in-place to replace known patterns with super-instructions.
// Only the first byte of each pattern is replaced; middle bytes are left intact
// so that jump targets landing in the middle still execute correct original instructions.
pub fn rewrite_super_instructions(code: &mut Vec<u8>) {
    let len = code.len();
    let mut pc: usize = 0;

    while pc < len {
        if code[pc] == ALOAD_0 {
            // 7 bytes: aload_0 + getfield + bipush + aaload
            if pc + 6 < len
                && code[pc + 1] == GETFIELD
                && code[pc + 4] == BIPUSH
                && code[pc + 6] == AALOAD
            {
                code[pc] = SUPER_ALOAD0_GETFIELD_BIPUSH_AALOAD;
                pc += 7;
                continue;
            }
            // 7 bytes: aload_0 + getfield + getfield
            if pc + 6 < len && code[pc + 1] == GETFIELD && code[pc + 4] == GETFIELD {
                code[pc] = SUPER_ALOAD0_GETFIELD_GETFIELD;
                pc += 7;
                continue;
            }
            // 6 bytes: aload_0 + getfield + iload_1 + aaload
            if pc + 5 < len
                && code[pc + 1] == GETFIELD
                && code[pc + 4] == ILOAD_1
                && code[pc + 5] == AALOAD
            {
                code[pc] = SUPER_ALOAD0_GETFIELD_ILOAD1_AALOAD;
                pc += 6;
                continue;
            }
            // 5 bytes: aload_0 + iconst_0 + putfield
            if pc + 4 < len && code[pc + 1] == ICONST_0 && code[pc + 2] == PUTFIELD {
                code[pc] = SUPER_ALOAD0_ICONST0_PUTFIELD;
                pc += 5;
                continue;
            }
            // 4 bytes: aload_0 + getfield
            if pc + 3 < len && code[pc + 1] == GETFIELD {
                code[pc] = SUPER_ALOAD0_GETFIELD;
                pc += 4;
                continue;
            }
        }
        pc += instruction_length(code, pc);
    }
}

/// Returns the byte length of a JVM instruction at the given offset.
fn instruction_length(code: &[u8], pc: usize) -> usize {
    if pc >= code.len() {
        return 1;
    }
    match code[pc] {
        // 1-byte instructions
        NOP | ACONST_NULL | ICONST_M1 | ICONST_0 | ICONST_1 | ICONST_2 | ICONST_3 | ICONST_4
        | ICONST_5 | LCONST_0 | LCONST_1 | FCONST_0 | FCONST_1 | FCONST_2 | DCONST_0 | DCONST_1
        | ILOAD_0 | ILOAD_1 | ILOAD_2 | ILOAD_3 | LLOAD_0 | LLOAD_1 | LLOAD_2 | LLOAD_3
        | FLOAD_0 | FLOAD_1 | FLOAD_2 | FLOAD_3 | DLOAD_0 | DLOAD_1 | DLOAD_2 | DLOAD_3
        | ALOAD_0 | ALOAD_1 | ALOAD_2 | ALOAD_3 | IALOAD | LALOAD | FALOAD | DALOAD | AALOAD
        | BALOAD | CALOAD | SALOAD | ISTORE_0 | ISTORE_1 | ISTORE_2 | ISTORE_3 | LSTORE_0
        | LSTORE_1 | LSTORE_2 | LSTORE_3 | FSTORE_0 | FSTORE_1 | FSTORE_2 | FSTORE_3 | DSTORE_0
        | DSTORE_1 | DSTORE_2 | DSTORE_3 | ASTORE_0 | ASTORE_1 | ASTORE_2 | ASTORE_3 | IASTORE
        | LASTORE | FASTORE | DASTORE | AASTORE | BASTORE | CASTORE | SASTORE | POP | POP2
        | DUP | DUP_X1 | DUP_X2 | DUP2 | DUP2_X1 | DUP2_X2 | SWAP | IADD | LADD | FADD | DADD
        | ISUB | LSUB | FSUB | DSUB | IMUL | LMUL | FMUL | DMUL | IDIV | LDIV | FDIV | DDIV
        | IREM | LREM | FREM | DREM | INEG | LNEG | FNEG | DNEG | ISHL | LSHL | ISHR | LSHR
        | IUSHR | LUSHR | IAND | LAND | IOR | LOR | IXOR | LXOR | I2L | I2F | I2D | L2I | L2F
        | L2D | F2I | F2L | F2D | D2I | D2L | D2F | I2B | I2C | I2S | LCMP | FCMPL | FCMPG
        | DCMPL | DCMPG | IRETURN | LRETURN | FRETURN | DRETURN | ARETURN | RETURN
        | ARRAYLENGTH | ATHROW | MONITORENTER | MONITOREXIT => 1,

        // 2-byte instructions
        BIPUSH | LDC | ILOAD | LLOAD | FLOAD | DLOAD | ALOAD | ISTORE | LSTORE | FSTORE
        | DSTORE | ASTORE | RET | NEWARRAY => 2,

        // 3-byte instructions
        SIPUSH | LDC_W | LDC2_W | IINC | IFEQ | IFNE | IFLT | IFGE | IFGT | IFLE | IF_ICMPEQ
        | IF_ICMPNE | IF_ICMPLT | IF_ICMPGE | IF_ICMPGT | IF_ICMPLE | IF_ACMPEQ | IF_ACMPNE
        | GOTO | JSR | GETSTATIC | PUTSTATIC | GETFIELD | PUTFIELD | INVOKEVIRTUAL
        | INVOKESPECIAL | INVOKESTATIC | NEW | ANEWARRAY | CHECKCAST | INSTANCEOF | IFNULL
        | IFNONNULL => 3,

        // 4-byte instructions
        MULTIANEWARRAY => 4,

        // 5-byte instructions
        INVOKEINTERFACE | GOTO_W | JSR_W => 5,

        // Variable-length: tableswitch
        TABLESWITCH => {
            let base = pc + 1;
            let padding = (4 - (base % 4)) % 4;
            let table_start = base + padding;
            if table_start + 12 > code.len() {
                return 1;
            }
            let low = i32::from_be_bytes([
                code[table_start + 4],
                code[table_start + 5],
                code[table_start + 6],
                code[table_start + 7],
            ]);
            let high = i32::from_be_bytes([
                code[table_start + 8],
                code[table_start + 9],
                code[table_start + 10],
                code[table_start + 11],
            ]);
            let n = (high - low + 1) as usize;
            (table_start + 12 + n * 4) - pc
        }

        // Variable-length: lookupswitch
        LOOKUPSWITCH => {
            let base = pc + 1;
            let padding = (4 - (base % 4)) % 4;
            let table_start = base + padding;
            if table_start + 8 > code.len() {
                return 1;
            }
            let npairs = i32::from_be_bytes([
                code[table_start + 4],
                code[table_start + 5],
                code[table_start + 6],
                code[table_start + 7],
            ]) as usize;
            (table_start + 8 + npairs * 8) - pc
        }

        // WIDE prefix
        WIDE => {
            if pc + 1 < code.len() && code[pc + 1] == IINC {
                6
            } else {
                4
            }
        }

        _ => 1,
    }
}
