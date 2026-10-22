pub mod gc;

use serde::{Deserialize, Deserializer, Serialize, Serializer};
use std::collections::HashMap;
use std::ops::Range;

// ============ StringInternPool: 方案 A ============

/// LDC String 快速路径走 Vec 数组索引 O(1)，String.intern() 走 HashMap fallback。
#[derive(Debug, Clone, Serialize, Deserialize)]
struct StringInternPool {
    /// string_id → heap_addr. 0 = 尚未创建。
    by_string_id: Vec<u32>,
    /// 运行时动态 intern 的字符串：content → heap_addr。
    dynamic: HashMap<String, u32>,
}

impl StringInternPool {
    fn new() -> Self {
        Self {
            by_string_id: Vec::new(),
            dynamic: HashMap::new(),
        }
    }

    /// LDC 快速路径：通过 string_id 获取已驻留的 heap_addr。
    #[inline]
    fn get_by_string_id(&self, string_id: u32) -> u32 {
        self.by_string_id
            .get(string_id as usize)
            .copied()
            .unwrap_or(0)
    }

    /// LDC 快速路径：通过 string_id 设置 heap_addr。
    #[inline]
    fn set_by_string_id(&mut self, string_id: u32, heap_addr: u32) {
        let idx = string_id as usize;
        if idx >= self.by_string_id.len() {
            self.by_string_id.resize(idx + 1, 0);
        }
        self.by_string_id[idx] = heap_addr;
    }

    /// String.intern() 慢速路径：按内容查找。
    fn get_dynamic(&self, content: &str) -> Option<u32> {
        self.dynamic.get(content).copied()
    }

    /// String.intern() 慢速路径：按内容注册。
    fn insert_dynamic(&mut self, content: String, heap_addr: u32) {
        self.dynamic.insert(content, heap_addr);
    }

    /// GC root 遍历：返回所有已驻留的 heap_addr。
    fn all_values(&self) -> impl Iterator<Item = u32> + '_ {
        self.by_string_id
            .iter()
            .copied()
            .filter(|&a| a != 0)
            .chain(self.dynamic.values().copied())
    }
}

pub const NULL: u32 = 0;

/// Ordinary objects only carry their class id inline. Identity hashes and GC
/// metadata live in side tables owned by the heap.
pub const OBJ_HDR_SIZE: u32 = 4;
pub const OBJ_CLASS_ID_OFFSET: u32 = 0;

pub const ARRAY_HDR_SIZE: u32 = 8;
pub const ARRAY_CLASS_ID_OFFSET: u32 = 0;
pub const ARRAY_LENGTH_OFFSET: u32 = 4;
pub const ARRAY_DATA_OFFSET: u32 = 8;

pub const MAX_HEAP_SIZE: u32 = 64 * 1024 * 1024; // 64 MB

const INITIAL_GC_THRESHOLD: u32 = 512 * 1024;
const GC_GROWTH_FACTOR: u32 = 2;
const PAGE_SIZE: u32 = 16 * 1024;
const SIZE_CLASSES: [u32; 12] = [8, 12, 16, 24, 32, 48, 64, 96, 128, 256, 1024, 4096];
const META_ARRAY: u8 = 1;
const META_ATOMIC: u8 = 2;

#[derive(Debug, Clone, Serialize, Deserialize)]
struct HeapPage {
    start: u32,
    span: u32,
    slot_size: u32,
    slot_count: u16,
    allocated: Vec<u64>,
    marked: Vec<u64>,
    arrays: Vec<u64>,
    atomics: Vec<u64>,
    free_slots: Vec<u16>,
}

impl HeapPage {
    fn new(start: u32, span: u32, slot_size: u32) -> Self {
        let slot_count = if SIZE_CLASSES.contains(&slot_size) {
            (span / slot_size) as u16
        } else {
            // Allocations above the largest size class own their entire page
            // span. Treating unused tail bytes as another slot lets the page be
            // reindexed as reusable while its first object is still live.
            1
        };
        let words = (slot_count as usize + 63) / 64;
        Self {
            start,
            span,
            slot_size,
            slot_count,
            allocated: vec![0; words],
            marked: vec![0; words],
            arrays: vec![0; words],
            atomics: vec![0; words],
            free_slots: (0..slot_count).rev().collect(),
        }
    }

    #[inline]
    fn bit(bits: &[u64], slot: usize) -> bool {
        bits.get(slot / 64)
            .is_some_and(|word| word & (1 << (slot & 63)) != 0)
    }

    #[inline]
    fn set_bit(bits: &mut [u64], slot: usize, value: bool) {
        let mask = 1u64 << (slot & 63);
        if value {
            bits[slot / 64] |= mask;
        } else {
            bits[slot / 64] &= !mask;
        }
    }

    fn slot_of(&self, addr: u32) -> Option<usize> {
        if addr < self.start || addr >= self.start + self.span {
            return None;
        }
        let offset = addr - self.start;
        if offset % self.slot_size != 0 {
            return None;
        }
        let slot = (offset / self.slot_size) as usize;
        (slot < self.slot_count as usize).then_some(slot)
    }
}

#[derive(Serialize, Deserialize)]
struct HeapBlockSnapshot {
    start: u32,
    bytes: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct HeapSnapshotSerde {
    bump: u32,
    next_hash: u32,
    string_pool: StringInternPool,
    oom: bool,
    all_objects: Vec<u32>,
    total_allocated: u32,
    gc_threshold: u32,
    pages: Vec<HeapPage>,
    identity_hashes: HashMap<u32, u32>,
    blocks: Vec<HeapBlockSnapshot>,
}

/// Heap with TypedArray-style indexed access (matching PluotSorbet).
///
/// The underlying storage is `Vec<u8>`, but provides `i32[]`, `f32[]`, `u16[]`,
/// `i8[]` views via index-based accessors. For 4-byte aligned accesses,
/// use `i32_get(addr >> 2)` / `i32_set(addr >> 2, val)`, matching
/// PluotSorbet's `i32[address >> 2]` pattern.
#[derive(Debug, Clone)]
pub struct Heap {
    data: Vec<u8>,
    bump: u32,
    next_hash: u32,
    string_pool: StringInternPool,
    pub oom: bool,
    all_objects: Vec<u32>,
    /// Page metadata owns allocation/mark/type bitmaps. This replaces the
    /// per-object HashSet and the in-object GC header.
    pages: Vec<HeapPage>,
    page_map: Vec<i32>,
    available_pages: Vec<Vec<usize>>,
    large_pages: HashMap<u32, Vec<usize>>,
    identity_hashes: HashMap<u32, u32>,
    total_allocated: u32,
    gc_threshold: u32,
}

impl Serialize for Heap {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let mut blocks = Vec::with_capacity(self.all_objects.len());
        for &obj_addr in &self.all_objects {
            let block_addr = obj_addr;
            let total_size = self.obj_total_size(obj_addr);
            if total_size == 0 {
                continue;
            }
            let block_end = block_addr.saturating_add(total_size);
            if block_end > self.bump || block_end as usize > self.data.len() {
                continue;
            }
            blocks.push(HeapBlockSnapshot {
                start: block_addr,
                bytes: self.data[block_addr as usize..block_end as usize].to_vec(),
            });
        }

        HeapSnapshotSerde {
            bump: self.bump,
            next_hash: self.next_hash,
            string_pool: self.string_pool.clone(),
            oom: self.oom,
            all_objects: self.all_objects.clone(),
            total_allocated: self.total_allocated,
            gc_threshold: self.gc_threshold,
            pages: self.pages.clone(),
            identity_hashes: self.identity_hashes.clone(),
            blocks,
        }
        .serialize(serializer)
    }
}

impl<'de> Deserialize<'de> for Heap {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let snapshot = HeapSnapshotSerde::deserialize(deserializer)?;
        let mut data = vec![0; snapshot.bump as usize];
        for block in snapshot.blocks {
            let end = block.start.saturating_add(block.bytes.len() as u32);
            if end > snapshot.bump {
                return Err(serde::de::Error::custom("heap block exceeds bump"));
            }
            data[block.start as usize..end as usize].copy_from_slice(&block.bytes);
        }
        let mut heap = Self {
            data,
            bump: snapshot.bump,
            next_hash: snapshot.next_hash,
            string_pool: snapshot.string_pool,
            oom: snapshot.oom,
            all_objects: snapshot.all_objects,
            pages: snapshot.pages,
            page_map: Vec::new(),
            available_pages: vec![Vec::new(); SIZE_CLASSES.len()],
            large_pages: HashMap::new(),
            identity_hashes: snapshot.identity_hashes,
            total_allocated: snapshot.total_allocated,
            gc_threshold: snapshot.gc_threshold,
        };
        heap.rebuild_page_indexes();
        Ok(heap)
    }
}

impl Heap {
    pub fn new() -> Self {
        Self::with_capacity(4 * 1024 * 1024)
    }

    pub fn with_capacity(bytes: usize) -> Self {
        let mut data = Vec::with_capacity(bytes);
        data.resize(PAGE_SIZE as usize, 0);
        Self {
            data,
            bump: PAGE_SIZE,
            next_hash: 1,
            string_pool: StringInternPool::new(),
            oom: false,
            all_objects: Vec::new(),
            pages: Vec::new(),
            page_map: vec![-1],
            available_pages: vec![Vec::new(); SIZE_CLASSES.len()],
            large_pages: HashMap::new(),
            identity_hashes: HashMap::new(),
            total_allocated: 0,
            gc_threshold: INITIAL_GC_THRESHOLD,
        }
    }

    fn allocate_page(&mut self, slot_size: u32) -> Option<usize> {
        let span = if slot_size <= PAGE_SIZE {
            PAGE_SIZE
        } else {
            (slot_size + PAGE_SIZE - 1) & !(PAGE_SIZE - 1)
        };
        let addr = (self.bump + PAGE_SIZE - 1) & !(PAGE_SIZE - 1);
        let end = addr.checked_add(span)?;
        if end > MAX_HEAP_SIZE {
            self.oom = true;
            return None;
        }
        if end as usize > self.data.len() {
            self.data.resize(end as usize, 0);
        }
        self.bump = end;
        let page_idx = self.pages.len();
        self.pages.push(HeapPage::new(addr, span, slot_size));
        let first = (addr / PAGE_SIZE) as usize;
        let count = (span / PAGE_SIZE) as usize;
        if self.page_map.len() < first + count {
            self.page_map.resize(first + count, -1);
        }
        for entry in &mut self.page_map[first..first + count] {
            *entry = page_idx as i32;
        }
        Some(page_idx)
    }

    fn class_for_size(size: u32) -> Option<(usize, u32)> {
        SIZE_CLASSES
            .iter()
            .position(|&class| size <= class)
            .map(|i| (i, SIZE_CLASSES[i]))
    }

    fn alloc_slot(&mut self, requested: u32, flags: u8) -> (u32, u32) {
        let requested = (requested + 3) & !3;
        let (class_idx, slot_size) =
            Self::class_for_size(requested).unwrap_or((SIZE_CLASSES.len(), requested));
        let page_idx = if class_idx < SIZE_CLASSES.len() {
            loop {
                if let Some(idx) = self.available_pages[class_idx].last().copied() {
                    if !self.pages[idx].free_slots.is_empty() {
                        break idx;
                    }
                    self.available_pages[class_idx].pop();
                } else {
                    let Some(idx) = self.allocate_page(slot_size) else {
                        return (self.bump, requested);
                    };
                    self.available_pages[class_idx].push(idx);
                }
            }
        } else {
            let reusable_span = self
                .large_pages
                .iter()
                .filter(|(_, pages)| !pages.is_empty())
                .map(|(&span, _)| span)
                .filter(|&span| span >= requested)
                .min();
            match reusable_span.and_then(|span| self.large_pages.get_mut(&span)?.pop()) {
                Some(idx) => {
                    self.pages[idx].slot_size = requested;
                    idx
                }
                None => {
                    let Some(idx) = self.allocate_page(slot_size) else {
                        return (self.bump, requested);
                    };
                    idx
                }
            }
        };
        let page = &mut self.pages[page_idx];
        let slot = page
            .free_slots
            .pop()
            .expect("new or available page has a slot") as usize;
        HeapPage::set_bit(&mut page.allocated, slot, true);
        HeapPage::set_bit(&mut page.arrays, slot, flags & META_ARRAY != 0);
        HeapPage::set_bit(&mut page.atomics, slot, flags & META_ATOMIC != 0);
        let addr = page.start + slot as u32 * page.slot_size;
        self.data[addr as usize..(addr + page.slot_size) as usize].fill(0);
        if class_idx < SIZE_CLASSES.len() && page.free_slots.is_empty() {
            self.available_pages[class_idx].pop();
        }
        (addr, page.slot_size)
    }

    fn next_hash_code(&mut self) -> u32 {
        let h = self.next_hash;
        self.next_hash += 1;
        h
    }

    pub fn alloc_object(&mut self, class_id: u32, size_of_fields: u32) -> u32 {
        let payload = OBJ_HDR_SIZE + size_of_fields;
        let (addr, actual_total) = self.alloc_slot(payload, 0);
        if self.oom {
            // Return a valid writable address but do NOT track the object.
            // The bump was not advanced, so this area is scratch space that
            // will be overwritten by the next successful allocation.
            return addr;
        }
        let obj_addr = addr;
        self.write_u32(obj_addr + OBJ_CLASS_ID_OFFSET, class_id);
        self.all_objects.push(obj_addr);
        self.total_allocated += actual_total;
        obj_addr
    }

    pub fn alloc_array(&mut self, class_id: u32, length: u32, bytes_per_element: u32) -> u32 {
        let payload = ARRAY_DATA_OFFSET + length * bytes_per_element;
        let (addr, actual_total) = self.alloc_slot(payload, META_ARRAY);
        if self.oom {
            return addr;
        }
        let obj_addr = addr;
        self.write_u32(obj_addr + ARRAY_CLASS_ID_OFFSET, class_id);
        self.write_u32(obj_addr + ARRAY_LENGTH_OFFSET, length);
        self.all_objects.push(obj_addr);
        self.total_allocated += actual_total;
        obj_addr
    }

    /// Allocate an array with explicit atomic flag (for GC: atomic arrays are not scanned)
    pub fn alloc_array_typed(
        &mut self,
        class_id: u32,
        length: u32,
        bytes_per_element: u32,
        is_atomic: bool,
    ) -> u32 {
        let payload = ARRAY_DATA_OFFSET + length * bytes_per_element;
        let flags = META_ARRAY | if is_atomic { META_ATOMIC } else { 0 };
        let (addr, actual_total) = self.alloc_slot(payload, flags);
        if self.oom {
            return addr;
        }
        let obj_addr = addr;
        self.write_u32(obj_addr + ARRAY_CLASS_ID_OFFSET, class_id);
        self.write_u32(obj_addr + ARRAY_LENGTH_OFFSET, length);
        self.all_objects.push(obj_addr);
        self.total_allocated += actual_total;
        obj_addr
    }

    pub fn get_class_id(&self, addr: u32) -> u32 {
        self.read_u32(addr + OBJ_CLASS_ID_OFFSET)
    }

    pub fn array_length(&self, addr: u32) -> u32 {
        self.read_u32(addr + ARRAY_LENGTH_OFFSET)
    }

    /// Resolve an element range in a Java array to its byte range in heap storage.
    ///
    /// Keeping the checked address calculation here allows native bulk operations
    /// to validate once and then use Rust slice copies instead of per-element heap
    /// accessors.
    pub fn array_byte_range(
        &self,
        addr: u32,
        element_offset: u32,
        element_count: u32,
        bytes_per_element: u32,
    ) -> Option<Range<usize>> {
        if addr == NULL || bytes_per_element == 0 || !self.is_array_obj(addr) {
            return None;
        }
        let element_end = element_offset.checked_add(element_count)?;
        if element_end > self.array_length(addr) {
            return None;
        }
        let byte_offset = element_offset.checked_mul(bytes_per_element)?;
        let byte_len = element_count.checked_mul(bytes_per_element)?;
        let start = addr
            .checked_add(ARRAY_DATA_OFFSET)?
            .checked_add(byte_offset)? as usize;
        let end = start.checked_add(byte_len as usize)?;
        let object_end = addr.checked_add(self.obj_total_size(addr))? as usize;
        (end <= object_end && end <= self.data.len()).then_some(start..end)
    }

    pub fn array_bytes(
        &self,
        addr: u32,
        element_offset: u32,
        element_count: u32,
        bytes_per_element: u32,
    ) -> Option<&[u8]> {
        let range =
            self.array_byte_range(addr, element_offset, element_count, bytes_per_element)?;
        Some(&self.data[range])
    }

    pub fn array_bytes_mut(
        &mut self,
        addr: u32,
        element_offset: u32,
        element_count: u32,
        bytes_per_element: u32,
    ) -> Option<&mut [u8]> {
        let range =
            self.array_byte_range(addr, element_offset, element_count, bytes_per_element)?;
        Some(&mut self.data[range])
    }

    /// Copy Java array storage with memmove semantics.
    pub fn copy_array_elements(
        &mut self,
        src: u32,
        src_pos: u32,
        dest: u32,
        dest_pos: u32,
        length: u32,
        bytes_per_element: u32,
    ) -> bool {
        let Some(source) = self.array_byte_range(src, src_pos, length, bytes_per_element) else {
            return false;
        };
        let Some(destination) = self.array_byte_range(dest, dest_pos, length, bytes_per_element)
        else {
            return false;
        };
        self.data.copy_within(source, destination.start);
        true
    }

    /// Lazy hashCode: assigned on first access via global incrementing counter.
    /// Matches PluotSorbet's strategy and is safe for future GC (objects may move).
    pub fn get_hash_code(&mut self, addr: u32) -> u32 {
        if let Some(&h) = self.identity_hashes.get(&addr) {
            return h;
        }
        let new_h = self.next_hash_code();
        self.identity_hashes.insert(addr, new_h);
        new_h
    }

    pub fn read_i8(&self, addr: u32) -> i8 {
        self.data.get(addr as usize).copied().unwrap_or(0) as i8
    }

    pub fn write_i8(&mut self, addr: u32, val: i8) {
        if let Some(slot) = self.data.get_mut(addr as usize) {
            *slot = val as u8;
        }
    }

    pub fn read_u16(&self, addr: u32) -> u16 {
        let off = addr as usize;
        let b = &self.data;
        if off + 1 < b.len() {
            u16::from_le_bytes([b[off], b[off + 1]])
        } else {
            0
        }
    }

    pub fn write_u16(&mut self, addr: u32, val: u16) {
        let off = addr as usize;
        let bytes = val.to_le_bytes();
        if off + 1 < self.data.len() {
            self.data[off] = bytes[0];
            self.data[off + 1] = bytes[1];
        }
    }

    pub fn read_i16(&self, addr: u32) -> i16 {
        self.read_u16(addr) as i16
    }

    pub fn write_i16(&mut self, addr: u32, val: i16) {
        self.write_u16(addr, val as u16);
    }

    pub fn read_u32(&self, addr: u32) -> u32 {
        let off = addr as usize;
        let b = &self.data;
        if off + 3 < b.len() {
            u32::from_le_bytes([b[off], b[off + 1], b[off + 2], b[off + 3]])
        } else {
            0
        }
    }

    pub fn write_u32(&mut self, addr: u32, val: u32) {
        let off = addr as usize;
        let bytes = val.to_le_bytes();
        if off + 3 < self.data.len() {
            self.data[off] = bytes[0];
            self.data[off + 1] = bytes[1];
            self.data[off + 2] = bytes[2];
            self.data[off + 3] = bytes[3];
        }
    }

    pub fn read_i32(&self, addr: u32) -> i32 {
        self.read_u32(addr) as i32
    }

    pub fn write_i32(&mut self, addr: u32, val: i32) {
        self.write_u32(addr, val as u32);
    }

    pub fn read_i64(&self, addr: u32) -> i64 {
        let lo = self.read_u32(addr) as u64;
        let hi = self.read_u32(addr + 4) as u64;
        (hi << 32 | lo) as i64
    }

    pub fn write_i64(&mut self, addr: u32, val: i64) {
        self.write_u32(addr, val as u32);
        self.write_u32(addr + 4, (val >> 32) as u32);
    }

    pub fn read_f32(&self, addr: u32) -> f32 {
        f32::from_bits(self.read_u32(addr))
    }

    pub fn write_f32(&mut self, addr: u32, val: f32) {
        self.write_u32(addr, val.to_bits());
    }

    pub fn read_f64(&self, addr: u32) -> f64 {
        let lo = self.read_u32(addr) as u64;
        let hi = self.read_u32(addr + 4) as u64;
        f64::from_bits(hi << 32 | lo)
    }

    pub fn write_f64(&mut self, addr: u32, val: f64) {
        let bits = val.to_bits();
        self.write_u32(addr, bits as u32);
        self.write_u32(addr + 4, (bits >> 32) as u32);
    }

    pub fn array_read_i32(&self, addr: u32, index: u32) -> i32 {
        self.read_i32(ARRAY_DATA_OFFSET + addr + index * 4)
    }

    pub fn array_write_i32(&mut self, addr: u32, index: u32, val: i32) {
        self.write_i32(ARRAY_DATA_OFFSET + addr + index * 4, val);
    }

    pub fn array_read_u32(&self, addr: u32, index: u32) -> u32 {
        self.read_u32(ARRAY_DATA_OFFSET + addr + index * 4)
    }

    pub fn array_write_u32(&mut self, addr: u32, index: u32, val: u32) {
        self.write_u32(ARRAY_DATA_OFFSET + addr + index * 4, val);
    }

    pub fn array_read_i8(&self, addr: u32, index: u32) -> i8 {
        self.read_i8(ARRAY_DATA_OFFSET + addr + index)
    }

    pub fn array_write_i8(&mut self, addr: u32, index: u32, val: i8) {
        self.write_i8(ARRAY_DATA_OFFSET + addr + index, val);
    }

    pub fn array_read_u16(&self, addr: u32, index: u32) -> u16 {
        self.read_u16(
            ARRAY_DATA_OFFSET
                .wrapping_add(addr)
                .wrapping_add(index.wrapping_mul(2)),
        )
    }

    pub fn array_write_u16(&mut self, addr: u32, index: u32, val: u16) {
        self.write_u16(
            ARRAY_DATA_OFFSET
                .wrapping_add(addr)
                .wrapping_add(index.wrapping_mul(2)),
            val,
        );
    }

    pub fn array_read_i16(&self, addr: u32, index: u32) -> i16 {
        self.read_i16(
            ARRAY_DATA_OFFSET
                .wrapping_add(addr)
                .wrapping_add(index.wrapping_mul(2)),
        )
    }

    pub fn array_write_i16(&mut self, addr: u32, index: u32, val: i16) {
        self.write_i16(
            ARRAY_DATA_OFFSET
                .wrapping_add(addr)
                .wrapping_add(index.wrapping_mul(2)),
            val,
        );
    }

    pub fn array_read_i64(&self, addr: u32, index: u32) -> i64 {
        self.read_i64(ARRAY_DATA_OFFSET + addr + index * 8)
    }

    pub fn array_write_i64(&mut self, addr: u32, index: u32, val: i64) {
        self.write_i64(ARRAY_DATA_OFFSET + addr + index * 8, val);
    }

    pub fn array_read_f32(&self, addr: u32, index: u32) -> f32 {
        self.read_f32(ARRAY_DATA_OFFSET + addr + index * 4)
    }

    pub fn array_write_f32(&mut self, addr: u32, index: u32, val: f32) {
        self.write_f32(ARRAY_DATA_OFFSET + addr + index * 4, val);
    }

    pub fn array_read_f64(&self, addr: u32, index: u32) -> f64 {
        self.read_f64(ARRAY_DATA_OFFSET + addr + index * 8)
    }

    pub fn array_write_f64(&mut self, addr: u32, index: u32, val: f64) {
        self.write_f64(ARRAY_DATA_OFFSET + addr + index * 8, val);
    }

    /// LDC 快速路径：通过 string_id 获取已驻留的 heap_addr。0 = 尚未创建。
    #[inline]
    pub fn get_interned_by_string_id(&self, string_id: u32) -> u32 {
        self.string_pool.get_by_string_id(string_id)
    }

    /// LDC 快速路径：通过 string_id 注册驻留。
    #[inline]
    pub fn set_interned_by_string_id(&mut self, string_id: u32, heap_addr: u32) {
        self.string_pool.set_by_string_id(string_id, heap_addr);
    }

    /// String.intern() 慢速路径：按内容查找或注册。
    pub fn intern_string_dynamic(&mut self, value: &str, addr: u32) -> u32 {
        if let Some(existing) = self.string_pool.get_dynamic(value) {
            return existing;
        }
        self.string_pool.insert_dynamic(value.to_string(), addr);
        addr
    }

    /// String.intern() 慢速路径：按内容查找。
    pub fn get_interned_string_dynamic(&self, value: &str) -> Option<u32> {
        self.string_pool.get_dynamic(value)
    }

    pub fn used_bytes(&self) -> u32 {
        self.bump
    }

    // ---- TypedArray-style indexed accessors (PluotSorbet alignment) ----

    /// `i32[index]` — read an i32 at the given i32-index (byte offset = index * 4).
    #[inline]
    pub fn i32_get(&self, index: u32) -> i32 {
        self.read_i32(index << 2)
    }

    /// `i32[index] = val` — write an i32 at the given i32-index.
    #[inline]
    pub fn i32_set(&mut self, index: u32, val: i32) {
        self.write_i32(index << 2, val);
    }

    /// `u32[index]` — read a u32 at the given u32-index.
    #[inline]
    pub fn u32_get(&self, index: u32) -> u32 {
        self.read_u32(index << 2)
    }

    /// `u32[index] = val` — write a u32 at the given u32-index.
    #[inline]
    pub fn u32_set(&mut self, index: u32, val: u32) {
        self.write_u32(index << 2, val);
    }

    /// `f32[index]` — read a f32 at the given f32-index (byte offset = index * 4).
    #[inline]
    pub fn f32_get(&self, index: u32) -> f32 {
        self.read_f32(index << 2)
    }

    /// `f32[index] = val` — write a f32 at the given f32-index.
    #[inline]
    pub fn f32_set(&mut self, index: u32, val: f32) {
        self.write_f32(index << 2, val);
    }

    /// `u16[index]` — read a u16 at the given u16-index (byte offset = index * 2).
    #[inline]
    pub fn u16_get(&self, index: u32) -> u16 {
        self.read_u16(index << 1)
    }

    /// `u16[index] = val` — write a u16 at the given u16-index.
    #[inline]
    pub fn u16_set(&mut self, index: u32, val: u16) {
        self.write_u16(index << 1, val);
    }

    /// `i8[index]` — read an i8 at the given byte index.
    #[inline]
    pub fn i8_get(&self, index: u32) -> i8 {
        self.read_i8(index)
    }

    /// `i8[index] = val` — write an i8 at the given byte index.
    #[inline]
    pub fn i8_set(&mut self, index: u32, val: i8) {
        self.write_i8(index, val);
    }

    /// Raw data slice for direct access (e.g., bulk copy).
    pub fn data(&self) -> &[u8] {
        &self.data
    }

    /// Mutable raw data slice.
    pub fn data_mut(&mut self) -> &mut [u8] {
        &mut self.data
    }

    // ---- GC support ----

    fn location(&self, obj_addr: u32) -> Option<(usize, usize)> {
        let map_idx = (obj_addr / PAGE_SIZE) as usize;
        let page_idx = *self.page_map.get(map_idx)?;
        if page_idx < 0 {
            return None;
        }
        let page_idx = page_idx as usize;
        let slot = self.pages.get(page_idx)?.slot_of(obj_addr)?;
        Some((page_idx, slot))
    }

    fn rebuild_page_indexes(&mut self) {
        self.page_map.clear();
        self.available_pages = vec![Vec::new(); SIZE_CLASSES.len()];
        self.large_pages.clear();
        for (idx, page) in self.pages.iter().enumerate() {
            let first = (page.start / PAGE_SIZE) as usize;
            let count = (page.span / PAGE_SIZE) as usize;
            if self.page_map.len() < first + count {
                self.page_map.resize(first + count, -1);
            }
            for entry in &mut self.page_map[first..first + count] {
                *entry = idx as i32;
            }
            if let Some(class_idx) = SIZE_CLASSES.iter().position(|&size| size == page.slot_size) {
                if !page.free_slots.is_empty() {
                    self.available_pages[class_idx].push(idx);
                }
            } else if !page.free_slots.is_empty() {
                self.large_pages.entry(page.span).or_default().push(idx);
            }
        }
    }

    pub fn is_marked(&self, obj_addr: u32) -> bool {
        self.location(obj_addr)
            .is_some_and(|(p, s)| HeapPage::bit(&self.pages[p].marked, s))
    }

    pub fn set_mark(&mut self, obj_addr: u32) {
        if let Some((p, s)) = self.location(obj_addr) {
            HeapPage::set_bit(&mut self.pages[p].marked, s, true);
        }
    }

    pub fn clear_mark(&mut self, obj_addr: u32) {
        if let Some((p, s)) = self.location(obj_addr) {
            HeapPage::set_bit(&mut self.pages[p].marked, s, false);
        }
    }

    pub fn is_array_obj(&self, obj_addr: u32) -> bool {
        self.location(obj_addr)
            .is_some_and(|(p, s)| HeapPage::bit(&self.pages[p].arrays, s))
    }

    pub fn is_atomic_obj(&self, obj_addr: u32) -> bool {
        self.location(obj_addr)
            .is_some_and(|(p, s)| HeapPage::bit(&self.pages[p].atomics, s))
    }

    pub fn obj_total_size(&self, obj_addr: u32) -> u32 {
        self.location(obj_addr)
            .map_or(0, |(p, _)| self.pages[p].slot_size)
    }

    /// Check if a value could be a valid heap object reference.
    /// Uses page allocation bitmaps, avoiding a hash operation on every
    /// allocation and every conservative root candidate.
    pub fn is_valid_heap_ref(&self, val: u32) -> bool {
        self.location(val)
            .is_some_and(|(p, s)| HeapPage::bit(&self.pages[p].allocated, s))
    }

    pub fn all_objects(&self) -> &[u32] {
        &self.all_objects
    }

    pub fn all_objects_mut(&mut self) -> &mut Vec<u32> {
        &mut self.all_objects
    }

    pub fn total_allocated(&self) -> u32 {
        self.total_allocated
    }

    pub fn gc_threshold(&self) -> u32 {
        self.gc_threshold
    }

    pub fn needs_gc(&self) -> bool {
        self.total_allocated > self.gc_threshold
    }

    pub fn string_pool_values(&self) -> impl Iterator<Item = u32> + '_ {
        self.string_pool.all_values()
    }

    pub fn backing_bytes(&self) -> usize {
        self.data.len()
    }

    pub fn allocated_bytes(&self) -> u32 {
        self.total_allocated
    }

    pub fn live_object_count(&self) -> usize {
        self.all_objects.len()
    }

    pub fn compact_for_snapshot(&self) -> Self {
        let mut compacted = self.clone();
        let highest_live_end = self
            .all_objects
            .iter()
            .map(|&obj_addr| obj_addr.saturating_add(self.obj_total_size(obj_addr)))
            .max()
            .unwrap_or(PAGE_SIZE);

        let trimmed_bump = ((highest_live_end + PAGE_SIZE - 1) & !(PAGE_SIZE - 1))
            .max(PAGE_SIZE)
            .min(self.bump);
        compacted.data.truncate(trimmed_bump as usize);
        compacted.bump = trimmed_bump;
        compacted.pages.retain(|page| page.start < trimmed_bump);
        compacted.rebuild_page_indexes();
        compacted
    }

    /// Called after sweep to update the live object list and adjust threshold
    pub fn gc_finish_sweep(&mut self, retained: Vec<u32>, freed: u32) {
        self.all_objects = retained;
        let live: std::collections::HashSet<u32> = self.all_objects.iter().copied().collect();
        self.identity_hashes.retain(|addr, _| live.contains(addr));
        self.total_allocated = self.total_allocated.saturating_sub(freed);
        self.gc_threshold = (self.total_allocated * GC_GROWTH_FACTOR).max(INITIAL_GC_THRESHOLD);
    }

    pub fn rebuild_free_list_from_retained(&mut self, _retained_sorted: &[u32]) {
        self.rebuild_page_indexes();
    }

    /// Return a block to the free list (used by GC sweep)
    pub fn gc_free_block(&mut self, obj_addr: u32) {
        let Some((page_idx, slot)) = self.location(obj_addr) else {
            return;
        };
        let page = &mut self.pages[page_idx];
        if !HeapPage::bit(&page.allocated, slot) {
            return;
        }
        HeapPage::set_bit(&mut page.allocated, slot, false);
        HeapPage::set_bit(&mut page.marked, slot, false);
        HeapPage::set_bit(&mut page.arrays, slot, false);
        HeapPage::set_bit(&mut page.atomics, slot, false);
        page.free_slots.push(slot as u16);
        self.identity_hashes.remove(&obj_addr);
        if let Some(class_idx) = SIZE_CLASSES.iter().position(|&size| size == page.slot_size) {
            if !self.available_pages[class_idx].contains(&page_idx) {
                self.available_pages[class_idx].push(page_idx);
            }
        } else {
            self.large_pages
                .entry(page.span)
                .or_default()
                .push(page_idx);
        }
    }
}

impl Default for Heap {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_alloc_object() {
        let mut heap = Heap::new();
        let addr = heap.alloc_object(42, 12);
        assert!(addr >= PAGE_SIZE);
        assert_eq!(heap.get_class_id(addr), 42);
        assert!(!heap.identity_hashes.contains_key(&addr));
        let h = heap.get_hash_code(addr);
        assert_ne!(h, 0);
        assert_eq!(heap.identity_hashes.get(&addr), Some(&h));
        // Second call returns the same value
        assert_eq!(heap.get_hash_code(addr), h);
        heap.write_i32(addr + OBJ_HDR_SIZE, 99);
        assert_eq!(heap.read_i32(addr + OBJ_HDR_SIZE), 99);
    }

    #[test]
    fn test_alloc_array() {
        let mut heap = Heap::new();
        let addr = heap.alloc_array(10, 5, 4);
        assert_eq!(heap.array_length(addr), 5);
        heap.array_write_i32(addr, 2, 777);
        assert_eq!(heap.array_read_i32(addr, 2), 777);
    }

    #[test]
    fn test_wide_field() {
        let mut heap = Heap::new();
        let addr = heap.alloc_object(1, 16);
        heap.write_i64(addr + OBJ_HDR_SIZE, 0x1234_5678_9ABC_DEF0i64);
        assert_eq!(heap.read_i64(addr + OBJ_HDR_SIZE), 0x1234_5678_9ABC_DEF0i64);
    }

    #[test]
    fn test_byte_array() {
        let mut heap = Heap::new();
        let addr = heap.alloc_array(20, 4, 1);
        heap.array_write_i8(addr, 0, -128);
        heap.array_write_i8(addr, 3, 127);
        assert_eq!(heap.array_read_i8(addr, 0), -128);
        assert_eq!(heap.array_read_i8(addr, 3), 127);
    }

    #[test]
    fn test_char_array() {
        let mut heap = Heap::new();
        let addr = heap.alloc_array(21, 3, 2);
        heap.array_write_u16(addr, 0, 0x4F60);
        heap.array_write_u16(addr, 1, 0x597D);
        assert_eq!(heap.array_read_u16(addr, 0), 0x4F60);
        assert_eq!(heap.array_read_u16(addr, 1), 0x597D);
    }

    #[test]
    fn test_long_array() {
        let mut heap = Heap::new();
        let addr = heap.alloc_array(22, 3, 8);
        heap.array_write_i64(addr, 1, 123_456_789_012_345i64);
        assert_eq!(heap.array_read_i64(addr, 1), 123_456_789_012_345i64);
    }

    #[test]
    fn test_array_bulk_ranges_and_copy() {
        let mut heap = Heap::new();
        let source = heap.alloc_array(20, 6, 1);
        let destination = heap.alloc_array(20, 6, 1);
        heap.array_bytes_mut(source, 0, 6, 1)
            .unwrap()
            .copy_from_slice(&[1, 2, 3, 4, 5, 6]);

        assert!(heap.copy_array_elements(source, 1, destination, 2, 3, 1));
        assert_eq!(
            heap.array_bytes(destination, 0, 6, 1).unwrap(),
            &[0, 0, 2, 3, 4, 0]
        );
        assert!(heap.array_bytes(source, 5, 2, 1).is_none());
        assert!(!heap.copy_array_elements(source, 5, destination, 0, 2, 1));
    }

    #[test]
    fn test_array_bulk_copy_has_memmove_semantics() {
        let mut heap = Heap::new();
        let array = heap.alloc_array(10, 6, 4);
        for index in 0..6 {
            heap.array_write_i32(array, index, index as i32 + 1);
        }

        assert!(heap.copy_array_elements(array, 0, array, 1, 5, 4));
        let values: Vec<_> = (0..6)
            .map(|index| heap.array_read_i32(array, index))
            .collect();
        assert_eq!(values, vec![1, 1, 2, 3, 4, 5]);

        assert!(heap.copy_array_elements(array, 1, array, 0, 5, 4));
        let values: Vec<_> = (0..6)
            .map(|index| heap.array_read_i32(array, index))
            .collect();
        assert_eq!(values, vec![1, 2, 3, 4, 5, 5]);
    }

    #[test]
    fn test_page_metadata() {
        let mut heap = Heap::new();
        let addr = heap.alloc_object(42, 12);
        assert_eq!(heap.obj_total_size(addr), 16);
        assert!(!heap.is_marked(addr));
        assert!(!heap.is_array_obj(addr));

        // Test mark
        heap.set_mark(addr);
        assert!(heap.is_marked(addr));

        // Test array
        let arr = heap.alloc_array(10, 5, 4);
        assert!(heap.is_array_obj(arr));
        assert!(!heap.is_atomic_obj(arr));

        // Test atomic array
        let prim_arr = heap.alloc_array_typed(20, 4, 1, true);
        assert!(heap.is_array_obj(prim_arr));
        assert!(heap.is_atomic_obj(prim_arr));
    }

    #[test]
    fn test_is_valid_heap_ref() {
        let mut heap = Heap::new();
        let addr = heap.alloc_object(1, 4);
        assert!(heap.is_valid_heap_ref(addr));
        assert!(!heap.is_valid_heap_ref(0));
        assert!(!heap.is_valid_heap_ref(1));
        assert!(!heap.is_valid_heap_ref(addr + 1)); // not aligned
        assert!(!heap.is_valid_heap_ref(999_999)); // beyond bump
    }

    #[test]
    fn test_free_list_reuse() {
        let mut heap = Heap::new();
        let addr1 = heap.alloc_object(1, 4);
        let _size1 = heap.obj_total_size(addr1);

        // Simulate freeing the block
        heap.gc_free_block(addr1);

        // Next alloc of similar size should reuse
        let addr2 = heap.alloc_object(2, 4);
        assert_eq!(addr2, addr1);
    }

    #[test]
    fn test_compact_for_snapshot_trims_trailing_dead_space() {
        let mut heap = Heap::new();
        let live = heap.alloc_object(1, 4);
        let dead = heap.alloc_array(2, 2048, 4);
        let before = heap.backing_bytes();

        heap.gc_free_block(dead);
        heap.gc_finish_sweep(vec![live], heap.obj_total_size(dead));

        let compacted = heap.compact_for_snapshot();
        assert!(
            compacted.backing_bytes() < before,
            "snapshot heap should trim trailing dead space",
        );
        assert_eq!(compacted.all_objects(), &[live]);
    }

    #[test]
    fn test_heap_serde_skips_internal_dead_blocks() {
        let mut heap = Heap::new();
        let live1 = heap.alloc_object(1, 4);
        let dead = heap.alloc_array(2, 4096, 4);
        let live2 = heap.alloc_object(3, 4);

        heap.gc_free_block(dead);
        heap.gc_finish_sweep(vec![live1, live2], heap.obj_total_size(dead));

        let serialized_size = bincode::serialized_size(&heap).expect("serialize heap");
        assert!(
            serialized_size < heap.backing_bytes() as u64,
            "heap snapshot serde should avoid writing internal dead blocks",
        );
    }

    #[test]
    fn test_large_pages_reuse_exact_size() {
        let mut heap = Heap::new();
        let big = heap.alloc_array(2, 4096, 4);
        let big_size = heap.obj_total_size(big);
        heap.gc_free_block(big);
        heap.gc_finish_sweep(vec![], big_size);
        let bump_before = heap.used_bytes();
        let new_arr = heap.alloc_array(3, 4096, 4);
        let bump_after = heap.used_bytes();
        assert_eq!(bump_before, bump_after);
        assert_eq!(new_arr, big);
        assert_ne!(new_arr, 0);
    }

    #[test]
    fn test_large_subpage_allocations_do_not_share_a_page() {
        let mut heap = Heap::new();
        let first = heap.alloc_array_typed(5, 7_782, 1, true);
        heap.array_write_i8(first, 0, 11);
        heap.array_write_i8(first, 7_781, 22);

        let second = heap.alloc_array_typed(5, 7_000, 1, true);

        assert_eq!(heap.array_length(first), 7_782);
        assert_eq!(heap.array_read_i8(first, 0), 11);
        assert_eq!(heap.array_read_i8(first, 7_781), 22);
        assert_ne!(first, second);
        assert_ne!(first / PAGE_SIZE, second / PAGE_SIZE);
    }

    #[test]
    fn test_page_bitmap_rejects_interior_addresses() {
        let mut heap = Heap::new();
        let obj = heap.alloc_object(1, 4);
        assert!(heap.is_valid_heap_ref(obj));
        assert!(!heap.is_valid_heap_ref(obj + 4));
    }

    #[test]
    fn test_size_class_pages_reuse_slots() {
        let mut heap = Heap::new();
        let first = heap.alloc_object(1, 100);
        let second = heap.alloc_object(2, 100);
        let size = heap.obj_total_size(first);
        heap.gc_free_block(first);
        heap.gc_finish_sweep(vec![second], size);
        let bump_before = heap.used_bytes();
        let reused = heap.alloc_object(3, 100);
        assert_eq!(heap.used_bytes(), bump_before);
        assert_eq!(reused, first);
    }

    #[test]
    fn test_different_size_classes_use_dedicated_pages() {
        let mut heap = Heap::new();
        let small = heap.alloc_object(1, 4);
        let bump_before = heap.used_bytes();
        let medium = heap.alloc_object(2, 100);
        assert_eq!(heap.obj_total_size(small), 8);
        assert_eq!(heap.obj_total_size(medium), 128);
        assert_eq!(heap.used_bytes(), bump_before + PAGE_SIZE);
    }

    #[test]
    fn test_repeated_image_sized_churn_does_not_drive_bump_to_heap_limit() {
        let mut heap = Heap::new();
        // Simulate repeated alloc/free of same-sized image arrays (like game room transitions)
        let array_len = 1024; // 4KB arrays
        let class_id = 1;

        // Do 100 cycles of alloc + free
        for _ in 0..100 {
            let arr = heap.alloc_array(class_id, array_len, 4);
            let size = heap.obj_total_size(arr);
            heap.gc_free_block(arr);
            heap.gc_finish_sweep(vec![], size);
        }

        // One reserved null page plus one reusable large-object page.
        assert_eq!(heap.used_bytes(), PAGE_SIZE * 2);
    }

    #[test]
    fn test_large_dead_pages_are_reused_after_sweep() {
        let mut heap = Heap::new();
        // Allocate a small "survivor" and a large "dead tail" block
        let survivor = heap.alloc_object(1, 12); // small object that stays alive
        let _dead1 = heap.alloc_array(2, 4096, 4); // 16KB dead array
        let _dead2 = heap.alloc_array(2, 4096, 4); // another 16KB dead array
        let bump_before_gc = heap.used_bytes();
        assert!(bump_before_gc > 32_000, "should have allocated ~32KB+");

        let dead: Vec<u32> = heap.all_objects()[1..].to_vec();
        let freed: u32 = dead.iter().map(|&addr| heap.obj_total_size(addr)).sum();
        for addr in dead {
            heap.gc_free_block(addr);
        }
        let retained = vec![survivor];
        heap.rebuild_free_list_from_retained(&retained);
        heap.gc_finish_sweep(retained, freed);

        // Now allocate a new large array — it should come from the free-list (tail gap)
        // and bump should NOT grow beyond the previous bump
        let new_arr = heap.alloc_array(2, 4096, 4);
        assert_ne!(new_arr, 0);
        assert!(!heap.oom);
        assert!(
            heap.used_bytes() <= bump_before_gc,
            "bump should not grow past previous value: bump={}, prev={}",
            heap.used_bytes(),
            bump_before_gc
        );
    }
}
