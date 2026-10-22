use crate::classfile::class_repo::ClassRepository;
use crate::heap::{Heap, ARRAY_HDR_SIZE, OBJ_HDR_SIZE};
use crate::vm::{ResolvedField, SharedVm};

// ---- value type ----

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum ValueType {
    I8,
    I16,
    I32,
    I64,
    F32,
    F64,
}

impl ValueType {
    pub fn byte_size(self) -> u32 {
        match self {
            ValueType::I8 => 1,
            ValueType::I16 => 2,
            ValueType::I32 | ValueType::F32 => 4,
            ValueType::I64 | ValueType::F64 => 8,
        }
    }

    /// Parse from the i32 tag used in the C ABI (0=i8 1=i16 2=i32 3=i64 4=f32 5=f64).
    pub fn from_tag(tag: i32) -> Option<Self> {
        match tag {
            0 => Some(ValueType::I8),
            1 => Some(ValueType::I16),
            2 => Some(ValueType::I32),
            3 => Some(ValueType::I64),
            4 => Some(ValueType::F32),
            5 => Some(ValueType::F64),
            _ => None,
        }
    }

    pub fn tag(self) -> u8 {
        match self {
            ValueType::I8 => 0,
            ValueType::I16 => 1,
            ValueType::I32 => 2,
            ValueType::I64 => 3,
            ValueType::F32 => 4,
            ValueType::F64 => 5,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            ValueType::I8 => "i8",
            ValueType::I16 => "i16",
            ValueType::I32 => "i32",
            ValueType::I64 => "i64",
            ValueType::F32 => "f32",
            ValueType::F64 => "f64",
        }
    }

    /// Map a Java field descriptor char to the ValueType it should be read as.
    pub fn from_descriptor(desc: &str) -> Option<Self> {
        match desc {
            "B" | "Z" => Some(ValueType::I8),
            "S" | "C" => Some(ValueType::I16),
            "I" => Some(ValueType::I32),
            "J" => Some(ValueType::I64),
            "F" => Some(ValueType::F32),
            "D" => Some(ValueType::F64),
            _ => None, // reference or array — not a primitive
        }
    }
}

const ALL_TYPES: [ValueType; 6] = [
    ValueType::I8,
    ValueType::I16,
    ValueType::I32,
    ValueType::I64,
    ValueType::F32,
    ValueType::F64,
];

// ---- cheat value ----

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum CheatValue {
    I8(i8),
    I16(i16),
    I32(i32),
    I64(i64),
    F32(f32),
    F64(f64),
}

impl CheatValue {
    pub fn value_type(self) -> ValueType {
        match self {
            CheatValue::I8(_) => ValueType::I8,
            CheatValue::I16(_) => ValueType::I16,
            CheatValue::I32(_) => ValueType::I32,
            CheatValue::I64(_) => ValueType::I64,
            CheatValue::F32(_) => ValueType::F32,
            CheatValue::F64(_) => ValueType::F64,
        }
    }

    pub fn read_from(heap: &Heap, addr: u32, vtype: ValueType) -> Self {
        match vtype {
            ValueType::I8 => CheatValue::I8(heap.read_i8(addr)),
            ValueType::I16 => CheatValue::I16(heap.read_i16(addr)),
            ValueType::I32 => CheatValue::I32(heap.read_i32(addr)),
            ValueType::I64 => CheatValue::I64(heap.read_i64(addr)),
            ValueType::F32 => CheatValue::F32(heap.read_f32(addr)),
            ValueType::F64 => CheatValue::F64(heap.read_f64(addr)),
        }
    }

    pub fn write_to(self, heap: &mut Heap, addr: u32) {
        match self {
            CheatValue::I8(v) => heap.write_i8(addr, v),
            CheatValue::I16(v) => heap.write_i16(addr, v),
            CheatValue::I32(v) => heap.write_i32(addr, v),
            CheatValue::I64(v) => heap.write_i64(addr, v),
            CheatValue::F32(v) => heap.write_f32(addr, v),
            CheatValue::F64(v) => heap.write_f64(addr, v),
        }
    }

    pub fn from_le_bytes(vtype: ValueType, bytes: &[u8]) -> Option<Self> {
        Some(match vtype {
            ValueType::I8 => CheatValue::I8(*bytes.first()? as i8),
            ValueType::I16 => CheatValue::I16(i16::from_le_bytes(bytes.get(..2)?.try_into().ok()?)),
            ValueType::I32 => CheatValue::I32(i32::from_le_bytes(bytes.get(..4)?.try_into().ok()?)),
            ValueType::I64 => CheatValue::I64(i64::from_le_bytes(bytes.get(..8)?.try_into().ok()?)),
            ValueType::F32 => CheatValue::F32(f32::from_le_bytes(bytes.get(..4)?.try_into().ok()?)),
            ValueType::F64 => CheatValue::F64(f64::from_le_bytes(bytes.get(..8)?.try_into().ok()?)),
        })
    }

    /// Write self as 8 zero-padded LE bytes.
    pub fn to_le_bytes_8(self) -> [u8; 8] {
        let mut buf = [0u8; 8];
        match self {
            CheatValue::I8(v) => buf[0] = v as u8,
            CheatValue::I16(v) => buf[..2].copy_from_slice(&v.to_le_bytes()),
            CheatValue::I32(v) => buf[..4].copy_from_slice(&v.to_le_bytes()),
            CheatValue::I64(v) => buf.copy_from_slice(&v.to_le_bytes()),
            CheatValue::F32(v) => buf[..4].copy_from_slice(&v.to_le_bytes()),
            CheatValue::F64(v) => buf.copy_from_slice(&v.to_le_bytes()),
        }
        buf
    }

    fn as_i64(self) -> i64 {
        match self {
            CheatValue::I8(v) => v as i64,
            CheatValue::I16(v) => v as i64,
            CheatValue::I32(v) => v as i64,
            CheatValue::I64(v) => v,
            CheatValue::F32(v) => v as i64,
            CheatValue::F64(v) => v as i64,
        }
    }

    fn as_f64(self) -> f64 {
        match self {
            CheatValue::I8(v) => v as f64,
            CheatValue::I16(v) => v as f64,
            CheatValue::I32(v) => v as f64,
            CheatValue::I64(v) => v as f64,
            CheatValue::F32(v) => v as f64,
            CheatValue::F64(v) => v,
        }
    }
}

// ---- search mode ----

pub enum SearchMode {
    ExactValue(f64),
    Changed,
    Unchanged,
    Increased,
    Decreased,
    IncreasedBy(f64),
    DecreasedBy(f64),
    /// Snapshot: record current values as baseline without filtering any candidates.
    /// Use as first pass before Changed/Increased/Decreased refinement.
    Snapshot,
}

impl SearchMode {
    pub fn from_tag(tag: i32, value: f64) -> Option<Self> {
        match tag {
            0 => Some(SearchMode::ExactValue(value)),
            1 => Some(SearchMode::Changed),
            2 => Some(SearchMode::Unchanged),
            3 => Some(SearchMode::Increased),
            4 => Some(SearchMode::Decreased),
            5 => Some(SearchMode::IncreasedBy(value)),
            6 => Some(SearchMode::DecreasedBy(value)),
            7 => Some(SearchMode::Snapshot),
            _ => None,
        }
    }

    fn needs_prev(&self) -> bool {
        !matches!(self, SearchMode::ExactValue(_) | SearchMode::Snapshot)
    }

    fn matches(&self, cur: CheatValue, prev: Option<CheatValue>) -> bool {
        let is_float = matches!(cur, CheatValue::F32(_) | CheatValue::F64(_));
        match self {
            SearchMode::ExactValue(target) => {
                if is_float {
                    (cur.as_f64() - target).abs() < 1e-6_f64.max(target.abs() * 1e-6)
                } else {
                    let target_i64 = *target as i64;
                    if *target != target_i64 as f64 {
                        return false;
                    }
                    cur.as_i64() == target_i64
                }
            }
            SearchMode::Changed => prev.map_or(false, |p| {
                if is_float {
                    (cur.as_f64() - p.as_f64()).abs() > f64::EPSILON
                } else {
                    cur.as_i64() != p.as_i64()
                }
            }),
            SearchMode::Unchanged => prev.map_or(false, |p| {
                if is_float {
                    (cur.as_f64() - p.as_f64()).abs() < f64::EPSILON
                } else {
                    cur.as_i64() == p.as_i64()
                }
            }),
            SearchMode::Increased => prev.map_or(false, |p| {
                if is_float {
                    cur.as_f64() > p.as_f64()
                } else {
                    cur.as_i64() > p.as_i64()
                }
            }),
            SearchMode::Decreased => prev.map_or(false, |p| {
                if is_float {
                    cur.as_f64() < p.as_f64()
                } else {
                    cur.as_i64() < p.as_i64()
                }
            }),
            SearchMode::IncreasedBy(delta) => prev.map_or(false, |p| {
                if is_float {
                    (cur.as_f64() - p.as_f64() - delta).abs() < 1e-6_f64.max(delta.abs() * 1e-6)
                } else {
                    cur.as_i64() - p.as_i64() == *delta as i64
                }
            }),
            SearchMode::DecreasedBy(delta) => prev.map_or(false, |p| {
                if is_float {
                    (p.as_f64() - cur.as_f64() - delta).abs() < 1e-6_f64.max(delta.abs() * 1e-6)
                } else {
                    p.as_i64() - cur.as_i64() == *delta as i64
                }
            }),
            // Snapshot always matches — every address becomes a candidate.
            SearchMode::Snapshot => true,
        }
    }

    fn exact_integer_patterns(&self) -> Vec<(ValueType, Vec<u8>, CheatValue)> {
        match self {
            SearchMode::ExactValue(target) => {
                if !target.is_finite()
                    || target.fract() != 0.0
                    || *target < i64::MIN as f64
                    || *target > i64::MAX as f64
                {
                    return Vec::new();
                }
                let value = *target as i64;
                let mut patterns = Vec::with_capacity(4);
                if let Ok(v) = i8::try_from(value) {
                    patterns.push((ValueType::I8, vec![v as u8], CheatValue::I8(v)));
                }
                if let Ok(v) = i16::try_from(value) {
                    patterns.push((ValueType::I16, v.to_le_bytes().to_vec(), CheatValue::I16(v)));
                }
                if let Ok(v) = i32::try_from(value) {
                    patterns.push((ValueType::I32, v.to_le_bytes().to_vec(), CheatValue::I32(v)));
                }
                patterns.push((
                    ValueType::I64,
                    value.to_le_bytes().to_vec(),
                    CheatValue::I64(value),
                ));
                patterns
            }
            _ => Vec::new(),
        }
    }
}

// ---- freeze entry ----

pub struct FreezeEntry {
    pub address: u32,
    pub value_type: ValueType,
    pub value: CheatValue,
    pub enabled: bool,
    pub stale: bool,
}

// ---- watch entry ----

pub struct WatchEntry {
    pub address: u32,
    pub value_type: ValueType,
    pub label: String,
    pub current: CheatValue,
    pub changed: bool,
    pub stale: bool,
}

// ---- class metadata lookup ----

/// Try to find which Java field this heap address belongs to.
/// Returns (class_name, field_name, descriptor) if found.
fn lookup_field_at(
    heap: &Heap,
    addr: u32,
    class_repo: &ClassRepository,
) -> Option<(String, String, String)> {
    // Find the object that contains this address.
    for &obj_addr in heap.all_objects() {
        let total_size = heap.obj_total_size(obj_addr);
        if total_size == 0 {
            continue;
        }
        let is_array = heap.is_array_obj(obj_addr);
        let header_size = if is_array {
            ARRAY_HDR_SIZE
        } else {
            OBJ_HDR_SIZE
        };
        let field_start = obj_addr + header_size;
        let field_end = obj_addr + total_size;
        if addr < field_start || addr >= field_end {
            continue;
        }

        // Address is inside this object's field area.
        let class_id = heap.get_class_id(obj_addr);
        let class = class_repo.get_class_by_id(class_id)?;
        let byte_offset = addr - obj_addr; // offset from object start (includes header)

        // Walk the field list (instance fields only).
        for field in &class.fields {
            if field.is_static() {
                continue;
            }
            if field.byte_offset == byte_offset {
                return Some((
                    class.name.clone(),
                    class_repo.get_string(field.name_id).to_string(),
                    class_repo.get_string(field.descriptor_id).to_string(),
                ));
            }
        }
        // Address is inside an object but offset doesn't match any field —
        // could be mid-field (e.g. addr points to byte 2 of an int field).
        // Return class name without field details so UI can still show context.
        return Some((class.name.clone(), String::new(), String::new()));
    }
    None
}

// ---- cheat engine ----

const MAX_RESULTS: usize = 1000;
const MAX_OBJECT_RESULTS: usize = 100;
const MAX_ARRAY_DISPLAY_ELEMENTS: usize = 64;

#[derive(Clone)]
struct ObjectSearchField {
    address: u32,
    name: String,
    descriptor: String,
    value_type: ValueType,
    value: CheatValue,
}

struct ObjectSearchMatch {
    object_address: u32,
    /// 0=instance, 1=static storage, 2=primitive array.
    kind: u8,
    class_name: String,
    fields: Vec<ObjectSearchField>,
    matched_indices: Vec<usize>,
    /// Matching field slots in query order. Kept separately because array
    /// previews are sorted by array index for display.
    matched_slots: Vec<ObjectCandidateSlot>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct ObjectCandidateKey {
    object_address: u32,
    class_id: u32,
    /// 0=instance, 1=static storage, 2=primitive array.
    kind: u8,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct ObjectCandidateSlot {
    offset: u32,
    value_type: ValueType,
}

#[derive(Clone, Debug)]
struct ObjectCandidate {
    key: ObjectCandidateKey,
    /// One slot per query value, in the same order as the user's input.
    slots: Vec<ObjectCandidateSlot>,
}

fn value_matches_query(value: CheatValue, query: f64) -> bool {
    if !query.is_finite() {
        return false;
    }
    match value {
        CheatValue::I8(value) => query.fract() == 0.0 && value as f64 == query,
        CheatValue::I16(value) => query.fract() == 0.0 && value as f64 == query,
        CheatValue::I32(value) => query.fract() == 0.0 && value as f64 == query,
        CheatValue::I64(value) => query.fract() == 0.0 && value as f64 == query,
        CheatValue::F32(value) => value == query as f32,
        CheatValue::F64(value) => value == query,
    }
}

fn object_field_storage_size(field: &ObjectSearchField) -> u32 {
    if matches!(field.descriptor.as_str(), "J" | "D") {
        8
    } else {
        4
    }
}

/// Find distinct fields containing all query values, or a consecutive sequence when mode=1.
fn find_object_match_indices(
    fields: &[ObjectSearchField],
    values: &[f64],
    match_mode: i32,
) -> Option<Vec<usize>> {
    if values.is_empty() || fields.len() < values.len() {
        return None;
    }
    if match_mode == 1 {
        return fields
            .windows(values.len())
            .enumerate()
            .find_map(|(start, window)| {
                let values_match = window
                    .iter()
                    .zip(values)
                    .all(|(field, query)| value_matches_query(field.value, *query));
                let fields_are_adjacent = window.windows(2).all(|pair| {
                    pair[0]
                        .address
                        .checked_add(object_field_storage_size(&pair[0]))
                        == Some(pair[1].address)
                });
                (values_match && fields_are_adjacent)
                    .then(|| (start..start + values.len()).collect())
            });
    }

    // Default mode: every query value must occur in a distinct primitive field;
    // declaration order is intentionally ignored.
    let mut used = vec![false; fields.len()];
    let mut matched = Vec::with_capacity(values.len());
    for query in values {
        let index = fields
            .iter()
            .enumerate()
            .position(|(index, field)| !used[index] && value_matches_query(field.value, *query))?;
        used[index] = true;
        matched.push(index);
    }
    Some(matched)
}

fn collect_instance_fields(
    heap: &Heap,
    class_repo: &ClassRepository,
    object_address: u32,
    class_id: u32,
    static_storage: bool,
) -> Vec<ObjectSearchField> {
    let mut class_ids = Vec::new();
    let mut current_id = Some(class_id);
    while let Some(id) = current_id {
        let Some(class) = class_repo.get_class_by_id(id) else {
            break;
        };
        class_ids.push(id);
        current_id = if !static_storage && class.super_class_id > 0 {
            Some(class.super_class_id)
        } else {
            None
        };
    }
    class_ids.reverse();

    let object_end = object_address.saturating_add(heap.obj_total_size(object_address));
    let mut fields = Vec::new();
    for id in class_ids {
        let Some(class) = class_repo.get_class_by_id(id) else {
            continue;
        };
        for field in &class.fields {
            if field.is_static() != static_storage {
                continue;
            }
            let descriptor = class_repo.get_string(field.descriptor_id);
            let Some(value_type) = ValueType::from_descriptor(descriptor) else {
                continue;
            };
            let Some(address) = object_address.checked_add(field.byte_offset) else {
                continue;
            };
            if address.saturating_add(value_type.byte_size()) > object_end {
                continue;
            }
            fields.push(ObjectSearchField {
                address,
                name: class_repo.get_string(field.name_id).to_string(),
                descriptor: descriptor.to_string(),
                value_type,
                value: CheatValue::read_from(heap, address, value_type),
            });
        }
    }
    fields.sort_by_key(|field| field.address);
    fields
}

fn primitive_array_descriptor(class_name: &str) -> Option<&str> {
    match class_name {
        "[B" => Some("B"),
        "[Z" => Some("Z"),
        "[C" => Some("C"),
        "[S" => Some("S"),
        "[I" => Some("I"),
        "[J" => Some("J"),
        "[F" => Some("F"),
        "[D" => Some("D"),
        _ => None,
    }
}

fn array_display_indices(length: usize, matched: &[usize]) -> Vec<usize> {
    // Every actual match must be visible. Fill only the remaining preview budget
    // with neighboring elements, distributed around all matches.
    let limit = MAX_ARRAY_DISPLAY_ELEMENTS.max(matched.len());
    let mut indices: std::collections::BTreeSet<usize> =
        matched.iter().copied().take(limit).collect();
    'neighbors: for radius in 1..=8 {
        for &index in matched {
            if indices.len() >= limit {
                break 'neighbors;
            }
            if let Some(before) = index.checked_sub(radius) {
                indices.insert(before);
            }
            if indices.len() >= limit {
                break 'neighbors;
            }
            if let Some(after) = index.checked_add(radius).filter(|&value| value < length) {
                indices.insert(after);
            }
        }
    }
    indices.into_iter().collect()
}

fn collect_array_match(
    heap: &Heap,
    class_repo: &ClassRepository,
    object_address: u32,
    class_id: u32,
    values: &[f64],
    match_mode: i32,
) -> Option<ObjectSearchMatch> {
    let class_name = class_repo.get_class_name_by_id(class_id)?.to_string();
    let descriptor = primitive_array_descriptor(&class_name)?;
    let value_type = ValueType::from_descriptor(descriptor)?;
    let element_size = class_repo.get_array_bpe(class_id) as u32;
    if element_size == 0 {
        return None;
    }
    let length = heap.array_length(object_address) as usize;
    if length < values.len() {
        return None;
    }
    let value_at = |index: usize| -> Option<(u32, CheatValue)> {
        let address = object_address
            .checked_add(ARRAY_HDR_SIZE)?
            .checked_add((index as u32).checked_mul(element_size)?)?;
        Some((address, CheatValue::read_from(heap, address, value_type)))
    };
    let matched = if match_mode == 1 {
        (0..=length - values.len()).find_map(|start| {
            values
                .iter()
                .enumerate()
                .all(|(offset, query)| {
                    value_at(start + offset)
                        .is_some_and(|(_, value)| value_matches_query(value, *query))
                })
                .then(|| (start..start + values.len()).collect::<Vec<_>>())
        })?
    } else {
        let mut matched = Vec::with_capacity(values.len());
        for query in values {
            let index = (0..length).find(|index| {
                !matched.contains(index)
                    && value_at(*index).is_some_and(|(_, value)| value_matches_query(value, *query))
            })?;
            matched.push(index);
        }
        matched
    };

    // Avoid returning a huge array. Show the matched elements and a small neighborhood.
    let display_indices = array_display_indices(length, &matched);
    let fields: Vec<ObjectSearchField> = display_indices
        .iter()
        .filter_map(|&index| {
            let (address, value) = value_at(index)?;
            Some(ObjectSearchField {
                address,
                name: format!("[{index}]"),
                descriptor: descriptor.to_string(),
                value_type,
                value,
            })
        })
        .collect();
    let matched_indices = display_indices
        .iter()
        .enumerate()
        .filter_map(|(display_index, source_index)| {
            matched.contains(source_index).then_some(display_index)
        })
        .collect();
    let matched_slots = matched
        .iter()
        .filter_map(|&index| {
            let (address, _) = value_at(index)?;
            Some(ObjectCandidateSlot {
                offset: address.checked_sub(object_address)?,
                value_type,
            })
        })
        .collect();
    Some(ObjectSearchMatch {
        object_address,
        kind: 2,
        class_name,
        fields,
        matched_indices,
        matched_slots,
    })
}

fn push_search_string(buf: &mut Vec<u8>, value: &str) {
    let bytes = value.as_bytes();
    let length = bytes.len().min(u16::MAX as usize);
    buf.extend_from_slice(&(length as u16).to_le_bytes());
    buf.extend_from_slice(&bytes[..length]);
}

// ---- object candidate scanning and serialization ----

fn static_object_classes(vm: &SharedVm) -> std::collections::HashMap<u32, u32> {
    vm.static_fields
        .iter_entries()
        .map(|(class_id, address)| (address, class_id))
        .collect()
}

fn current_object_key(
    vm: &SharedVm,
    static_objects: &std::collections::HashMap<u32, u32>,
    live_objects: &std::collections::HashSet<u32>,
    object_address: u32,
) -> Option<ObjectCandidateKey> {
    if !live_objects.contains(&object_address) {
        return None;
    }
    if vm.heap.is_array_obj(object_address) {
        return Some(ObjectCandidateKey {
            object_address,
            class_id: vm.heap.get_class_id(object_address),
            kind: 2,
        });
    }
    if let Some(&class_id) = static_objects.get(&object_address) {
        return Some(ObjectCandidateKey {
            object_address,
            class_id,
            kind: 1,
        });
    }
    Some(ObjectCandidateKey {
        object_address,
        class_id: vm.heap.get_class_id(object_address),
        kind: 0,
    })
}

fn collect_object_match(
    vm: &SharedVm,
    key: ObjectCandidateKey,
    values: &[f64],
    match_mode: i32,
) -> Option<ObjectSearchMatch> {
    if key.kind == 2 {
        return collect_array_match(
            &vm.heap,
            &vm.class_repo,
            key.object_address,
            key.class_id,
            values,
            match_mode,
        );
    }
    let fields = collect_instance_fields(
        &vm.heap,
        &vm.class_repo,
        key.object_address,
        key.class_id,
        key.kind == 1,
    );
    find_object_match_indices(&fields, values, match_mode).map(|matched_indices| {
        let matched_slots = matched_indices
            .iter()
            .map(|&index| ObjectCandidateSlot {
                offset: fields[index].address - key.object_address,
                value_type: fields[index].value_type,
            })
            .collect();
        ObjectSearchMatch {
            object_address: key.object_address,
            kind: key.kind,
            class_name: vm
                .class_repo
                .get_class_name_by_id(key.class_id)
                .unwrap_or("<unknown>")
                .to_string(),
            fields,
            matched_indices,
            matched_slots,
        }
    })
}

fn collect_object_match_at_slots(
    vm: &SharedVm,
    candidate: &ObjectCandidate,
    values: &[f64],
) -> Option<ObjectSearchMatch> {
    if candidate.slots.len() != values.len() {
        return None;
    }
    let key = candidate.key;
    if key.kind == 2 {
        let class_name = vm
            .class_repo
            .get_class_name_by_id(key.class_id)?
            .to_string();
        let descriptor = primitive_array_descriptor(&class_name)?;
        let value_type = ValueType::from_descriptor(descriptor)?;
        let element_size = vm.class_repo.get_array_bpe(key.class_id) as u32;
        if element_size == 0 {
            return None;
        }
        let length = vm.heap.array_length(key.object_address) as usize;
        let mut source_indices = Vec::with_capacity(values.len());
        for (slot, query) in candidate.slots.iter().zip(values) {
            let data_offset = slot.offset.checked_sub(ARRAY_HDR_SIZE)?;
            if slot.value_type != value_type || data_offset % element_size != 0 {
                return None;
            }
            let index = (data_offset / element_size) as usize;
            if index >= length {
                return None;
            }
            let address = key.object_address.checked_add(slot.offset)?;
            let value = CheatValue::read_from(&vm.heap, address, value_type);
            if !value_matches_query(value, *query) {
                return None;
            }
            source_indices.push(index);
        }

        let display_indices = array_display_indices(length, &source_indices);
        let fields: Vec<ObjectSearchField> = display_indices
            .iter()
            .filter_map(|&index| {
                let address = key
                    .object_address
                    .checked_add(ARRAY_HDR_SIZE)?
                    .checked_add((index as u32).checked_mul(element_size)?)?;
                Some(ObjectSearchField {
                    address,
                    name: format!("[{index}]"),
                    descriptor: descriptor.to_string(),
                    value_type,
                    value: CheatValue::read_from(&vm.heap, address, value_type),
                })
            })
            .collect();
        let matched_indices = display_indices
            .iter()
            .enumerate()
            .filter_map(|(display_index, source_index)| {
                source_indices
                    .contains(source_index)
                    .then_some(display_index)
            })
            .collect();
        return Some(ObjectSearchMatch {
            object_address: key.object_address,
            kind: key.kind,
            class_name,
            fields,
            matched_indices,
            matched_slots: candidate.slots.clone(),
        });
    }

    let fields = collect_instance_fields(
        &vm.heap,
        &vm.class_repo,
        key.object_address,
        key.class_id,
        key.kind == 1,
    );
    let mut matched_indices = Vec::with_capacity(values.len());
    for (slot, query) in candidate.slots.iter().zip(values) {
        let address = key.object_address.checked_add(slot.offset)?;
        let index = fields
            .iter()
            .position(|field| field.address == address && field.value_type == slot.value_type)?;
        if !value_matches_query(fields[index].value, *query) {
            return None;
        }
        matched_indices.push(index);
    }
    Some(ObjectSearchMatch {
        object_address: key.object_address,
        kind: key.kind,
        class_name: vm
            .class_repo
            .get_class_name_by_id(key.class_id)
            .unwrap_or("<unknown>")
            .to_string(),
        fields,
        matched_indices,
        matched_slots: candidate.slots.clone(),
    })
}

fn scan_object_candidates(
    vm: &SharedVm,
    values: &[f64],
    match_mode: i32,
) -> (Vec<ObjectCandidate>, Vec<ObjectSearchMatch>) {
    let static_objects = static_object_classes(vm);
    let live_objects: std::collections::HashSet<u32> =
        vm.heap.all_objects().iter().copied().collect();
    let mut retained = Vec::new();
    let mut matches = Vec::new();

    for &object_address in vm.heap.all_objects() {
        let Some(key) = current_object_key(vm, &static_objects, &live_objects, object_address)
        else {
            continue;
        };
        let result = collect_object_match(vm, key, values, match_mode);
        let Some(result) = result else { continue };
        retained.push(ObjectCandidate {
            key,
            slots: result.matched_slots.clone(),
        });
        if matches.len() < MAX_OBJECT_RESULTS {
            matches.push(result);
        }
    }

    (retained, matches)
}

fn refine_object_candidates(
    vm: &SharedVm,
    previous: &[ObjectCandidate],
    values: &[f64],
) -> (Vec<ObjectCandidate>, Vec<ObjectSearchMatch>) {
    let static_objects = static_object_classes(vm);
    let live_objects: std::collections::HashSet<u32> =
        vm.heap.all_objects().iter().copied().collect();
    let mut retained = Vec::new();
    let mut matches = Vec::new();

    for candidate in previous {
        let Some(current_key) = current_object_key(
            vm,
            &static_objects,
            &live_objects,
            candidate.key.object_address,
        ) else {
            continue;
        };
        if current_key != candidate.key {
            // The address was freed and reused for another class/object category.
            continue;
        }
        let Some(result) = collect_object_match_at_slots(vm, candidate, values) else {
            continue;
        };
        retained.push(candidate.clone());
        if matches.len() < MAX_OBJECT_RESULTS {
            matches.push(result);
        }
    }

    (retained, matches)
}

fn serialize_object_matches(matches: Vec<ObjectSearchMatch>, total: usize) -> Vec<u8> {
    let mut buf = Vec::new();
    buf.extend_from_slice(&(matches.len() as u32).to_le_bytes());
    buf.extend_from_slice(&(total.min(u32::MAX as usize) as u32).to_le_bytes());
    buf.push(u8::from(total > matches.len()));
    for result in matches {
        buf.extend_from_slice(&result.object_address.to_le_bytes());
        buf.push(result.kind);
        push_search_string(&mut buf, &result.class_name);
        buf.extend_from_slice(&(result.fields.len().min(u16::MAX as usize) as u16).to_le_bytes());
        for (index, field) in result
            .fields
            .into_iter()
            .take(u16::MAX as usize)
            .enumerate()
        {
            buf.extend_from_slice(&field.address.to_le_bytes());
            buf.push(field.value_type.tag());
            buf.push(u8::from(result.matched_indices.contains(&index)));
            push_search_string(&mut buf, &field.name);
            push_search_string(&mut buf, &field.descriptor);
            buf.extend_from_slice(&field.value.to_le_bytes_8());
        }
    }
    buf
}

/// Search values within one object only. References are not followed.
/// match_mode: 0=all values in distinct fields (unordered), 1=consecutive fields.
///
/// Binary result:
/// [count:u32][total:u32][flags:u8 bit0=truncated]
/// per object: [object_addr:u32][kind:u8][class_name:string][field_count:u16]
/// per field: [addr:u32][vtype:u8][flags:u8 bit0=matched][name:string]
///            [descriptor:string][value:8 bytes]
///
/// This stateless entry point is retained for native callers and focused tests.
pub fn search_objects_binary(vm: &SharedVm, values: &[f64], match_mode: i32) -> Vec<u8> {
    if values.is_empty() || values.iter().any(|value| !value.is_finite()) {
        return vec![0, 0, 0, 0, 0, 0, 0, 0, 0];
    }
    let (candidates, matches) = scan_object_candidates(vm, values, match_mode);
    serialize_object_matches(matches, candidates.len())
}

/// One unique address with all type tags that matched the search.
#[derive(Clone)]
pub struct AddrCandidate {
    pub addr: u32,
    /// Sorted type tags (0=i8..5=f64) that matched.
    pub type_tags: Vec<u8>,
}

/// Internal per-(addr,type) state for the prev_values map.
type PrevKey = (u32, u8);

pub struct CheatEngine {
    candidates: Option<Vec<AddrCandidate>>,
    object_candidates: Option<Vec<ObjectCandidate>>,
    prev_values: std::collections::HashMap<PrevKey, CheatValue>,
    truncated: bool,
    pub freezes: Vec<FreezeEntry>,
    pub watches: Vec<WatchEntry>,
}

impl CheatEngine {
    pub fn new() -> Self {
        Self {
            candidates: None,
            object_candidates: None,
            prev_values: std::collections::HashMap::new(),
            truncated: false,
            freezes: Vec::new(),
            watches: Vec::new(),
        }
    }

    // ---- search ----

    pub fn search_begin(&mut self, heap: &Heap, mode: SearchMode) {
        self.prev_values.clear();
        // Collect (addr, type_tag) pairs per-address using an ordered map.
        let mut addr_map: std::collections::BTreeMap<u32, Vec<u8>> =
            std::collections::BTreeMap::new();

        let exact_patterns = mode.exact_integer_patterns();
        if !exact_patterns.is_empty() {
            for &obj_addr in heap.all_objects() {
                let total_size = heap.obj_total_size(obj_addr);
                if total_size == 0 {
                    continue;
                }
                let header_size = if heap.is_array_obj(obj_addr) {
                    ARRAY_HDR_SIZE
                } else {
                    OBJ_HDR_SIZE
                };
                let field_start = obj_addr + header_size;
                let field_end = obj_addr + total_size;
                if field_end <= field_start {
                    continue;
                }

                let heap_data = heap.data();
                for (vtype, target_bytes, value) in &exact_patterns {
                    let len = target_bytes.len() as u32;
                    if field_end < field_start + len {
                        continue;
                    }
                    let type_tag = vtype.tag();
                    for addr in field_start..=field_end - len {
                        let start = addr as usize;
                        let end = start + target_bytes.len();
                        if end <= heap_data.len() && heap_data[start..end] == target_bytes[..] {
                            let tags = addr_map.entry(addr).or_default();
                            if !tags.contains(&type_tag) {
                                tags.push(type_tag);
                            }
                            self.prev_values.insert((addr, type_tag), *value);
                        }
                    }
                }
            }

            let mut result: Vec<AddrCandidate> = addr_map
                .into_iter()
                .map(|(addr, type_tags)| AddrCandidate { addr, type_tags })
                .collect();

            self.truncated = result.len() > MAX_RESULTS;
            if self.truncated {
                result.truncate(MAX_RESULTS);
            }
            self.candidates = Some(result);
            return;
        }

        for &obj_addr in heap.all_objects() {
            let total_size = heap.obj_total_size(obj_addr);
            if total_size == 0 {
                continue;
            }
            let header_size = if heap.is_array_obj(obj_addr) {
                ARRAY_HDR_SIZE
            } else {
                OBJ_HDR_SIZE
            };
            let field_start = obj_addr + header_size;
            let field_end = obj_addr + total_size;
            if field_end <= field_start {
                continue;
            }

            for &vtype in &ALL_TYPES {
                let step = vtype.byte_size();
                let first = if step == 1 {
                    field_start
                } else {
                    (field_start + step - 1) & !(step - 1)
                };
                let type_tag = vtype.tag();
                let mut addr = first;
                while addr + step <= field_end {
                    let cur = CheatValue::read_from(heap, addr, vtype);
                    let key: PrevKey = (addr, type_tag);
                    let prev = if mode.needs_prev() {
                        self.prev_values.get(&key).copied()
                    } else {
                        None
                    };
                    if mode.matches(cur, prev) {
                        addr_map.entry(addr).or_default().push(type_tag);
                    }
                    self.prev_values.insert(key, cur);
                    addr += step;
                }
            }
        }

        let mut result: Vec<AddrCandidate> = addr_map
            .into_iter()
            .map(|(addr, type_tags)| AddrCandidate { addr, type_tags })
            .collect();

        self.truncated = result.len() > MAX_RESULTS;
        if self.truncated {
            result.truncate(MAX_RESULTS);
        }
        self.candidates = Some(result);
    }

    pub fn search_refine(
        &mut self,
        heap: &Heap,
        class_repo: Option<&ClassRepository>,
        mode: SearchMode,
    ) {
        let candidates = match self.candidates.take() {
            Some(c) => c,
            None => return,
        };

        let mut retained: Vec<AddrCandidate> = Vec::new();
        let exact_patterns = mode.exact_integer_patterns();
        for mut cand in candidates {
            if !exact_patterns.is_empty() {
                cand.type_tags.retain(|&type_tag| {
                    let Some((_, target_bytes, value)) = exact_patterns
                        .iter()
                        .find(|(vtype, _, _)| vtype.tag() == type_tag)
                    else {
                        return false;
                    };
                    let start = cand.addr as usize;
                    let end = start + target_bytes.len();
                    let matched =
                        end <= heap.data().len() && heap.data()[start..end] == target_bytes[..];
                    if matched {
                        self.prev_values.insert((cand.addr, type_tag), *value);
                    }
                    matched
                });
                if !cand.type_tags.is_empty() {
                    retained.push(cand);
                }
                continue;
            }

            cand.type_tags.retain(|&type_tag| {
                let vtype = match ValueType::from_tag(type_tag as i32) {
                    Some(v) => v,
                    None => return false,
                };
                let key: PrevKey = (cand.addr, type_tag);
                let cur = CheatValue::read_from(heap, cand.addr, vtype);
                let prev = self.prev_values.get(&key).copied();
                self.prev_values.insert(key, cur);
                mode.matches(cur, prev)
            });

            // Results are displayed and edited with class metadata when available.
            // If the initial scan matched the same address with a different inferred
            // type, refine should still keep the address when its confirmed field
            // type matches the new filter value.
            if let Some(vtype) = class_repo
                .and_then(|repo| lookup_field_at(heap, cand.addr, repo))
                .and_then(|(_, _, desc)| ValueType::from_descriptor(&desc))
            {
                let type_tag = vtype.tag();
                if !cand.type_tags.contains(&type_tag) {
                    let key: PrevKey = (cand.addr, type_tag);
                    let cur = CheatValue::read_from(heap, cand.addr, vtype);
                    let prev = self.prev_values.get(&key).copied();
                    self.prev_values.insert(key, cur);
                    if mode.matches(cur, prev) {
                        cand.type_tags.push(type_tag);
                        cand.type_tags.sort_unstable();
                    }
                }
            }

            if !cand.type_tags.is_empty() {
                retained.push(cand);
            }
        }

        self.truncated = retained.len() > MAX_RESULTS;
        if self.truncated {
            retained.truncate(MAX_RESULTS);
        }
        self.candidates = Some(retained);
    }

    pub fn search_clear(&mut self) {
        self.candidates = None;
        self.object_search_clear();
        self.prev_values.clear();
        self.truncated = false;
    }

    pub fn object_search_clear(&mut self) {
        self.object_candidates = None;
    }

    /// Start an object search across the whole heap and retain every matching
    /// object's stable identity for subsequent refinement passes.
    pub fn object_search_begin_binary(
        &mut self,
        vm: &SharedVm,
        values: &[f64],
        match_mode: i32,
    ) -> Vec<u8> {
        if values.is_empty() || values.iter().any(|value| !value.is_finite()) {
            self.object_candidates = None;
            return vec![0, 0, 0, 0, 0, 0, 0, 0, 0];
        }
        let (candidates, matches) = scan_object_candidates(vm, values, match_mode);
        let total = candidates.len();
        self.object_candidates = Some(candidates);
        serialize_object_matches(matches, total)
    }

    /// Refine only the previous object candidates by comparing each new query
    /// value with the field slot matched by the value at the same query index.
    /// Addresses that disappeared or were reused for another object are discarded.
    pub fn object_search_refine_binary(
        &mut self,
        vm: &SharedVm,
        values: &[f64],
        _match_mode: i32,
    ) -> Vec<u8> {
        if values.is_empty() || values.iter().any(|value| !value.is_finite()) {
            return vec![0, 0, 0, 0, 0, 0, 0, 0, 0];
        }
        let Some(previous) = self.object_candidates.take() else {
            return vec![0, 0, 0, 0, 0, 0, 0, 0, 0];
        };
        let (candidates, matches) = refine_object_candidates(vm, &previous, values);
        let total = candidates.len();
        self.object_candidates = Some(candidates);
        serialize_object_matches(matches, total)
    }

    /// Returns search results as a binary buffer.
    ///
    /// Layout:
    ///   Header (9 bytes): [count: u32][total_candidates: u32][flags: u8 (bit0=truncated)]
    ///   Per entry (variable):
    ///     [addr: u32][type_count: u8][type_tags: u8 × type_count]
    ///     [confirmed_type: u8 (0xFF=none)][value: 8 bytes][field_len: u16][field: UTF-8]
    pub fn results_binary(&self, heap: &Heap, class_repo: Option<&ClassRepository>) -> Vec<u8> {
        let candidates = match &self.candidates {
            Some(c) => c,
            None => {
                // Empty result: count=0, total=0, flags=0
                return vec![0, 0, 0, 0, 0, 0, 0, 0, 0];
            }
        };

        let mut buf = Vec::with_capacity(9 + candidates.len() * 20);
        // Header
        buf.extend_from_slice(&(candidates.len() as u32).to_le_bytes());
        buf.extend_from_slice(&(candidates.len() as u32).to_le_bytes()); // total_candidates == count (already truncated)
        let flags: u8 = self.truncated as u8;
        buf.push(flags);

        for cand in candidates {
            let addr = cand.addr;
            buf.extend_from_slice(&addr.to_le_bytes());

            // type_count + type_tags
            buf.push(cand.type_tags.len() as u8);
            buf.extend_from_slice(&cand.type_tags);

            // Try class metadata lookup to confirm the real type.
            let meta = class_repo.and_then(|repo| lookup_field_at(heap, addr, repo));
            let confirmed_vtype: Option<ValueType> = meta.as_ref().and_then(|(_, _, desc)| {
                if desc.is_empty() {
                    None
                } else {
                    ValueType::from_descriptor(desc)
                }
            });

            // confirmed_type tag (0xFF = none)
            buf.push(confirmed_vtype.map_or(0xFF, |v| v.tag()));

            // Pick the best type for reading the display value.
            let display_vtype = confirmed_vtype.unwrap_or_else(|| {
                let first_tag = cand.type_tags.first().copied().unwrap_or(2);
                ValueType::from_tag(first_tag as i32).unwrap_or(ValueType::I32)
            });
            let value = CheatValue::read_from(heap, addr, display_vtype);
            buf.extend_from_slice(&value.to_le_bytes_8());

            // field string
            let field_str: String = match &meta {
                Some((class_name, field_name, _)) if !field_name.is_empty() => {
                    let simple = class_name.rsplit('/').next().unwrap_or(class_name);
                    format!("{}.{}", simple, field_name)
                }
                Some((class_name, _, _)) => class_name
                    .rsplit('/')
                    .next()
                    .unwrap_or(class_name)
                    .to_string(),
                None => String::new(),
            };
            let field_bytes = field_str.as_bytes();
            buf.extend_from_slice(&(field_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(field_bytes);
        }

        buf
    }

    // ---- direct read / write ----

    /// Read a value and return as binary: [vtype: u8][value: 8 bytes LE]
    pub fn read_binary(&self, heap: &Heap, addr: u32, vtype: ValueType) -> [u8; 9] {
        let val = CheatValue::read_from(heap, addr, vtype);
        let mut buf = [0u8; 9];
        buf[0] = vtype.tag();
        buf[1..9].copy_from_slice(&val.to_le_bytes_8());
        buf
    }

    pub fn write_value(&self, heap: &mut Heap, addr: u32, value: CheatValue) {
        value.write_to(heap, addr);
    }

    // ---- freeze management ----

    pub fn freeze_add(&mut self, address: u32, value_type: ValueType, value: CheatValue) {
        if let Some(entry) = self
            .freezes
            .iter_mut()
            .find(|e| e.address == address && e.value_type == value_type)
        {
            entry.value = value;
            entry.enabled = true;
            entry.stale = false;
            return;
        }
        self.freezes.push(FreezeEntry {
            address,
            value_type,
            value,
            enabled: true,
            stale: false,
        });
    }

    pub fn freeze_remove(&mut self, address: u32, value_type: ValueType) {
        self.freezes
            .retain(|e| !(e.address == address && e.value_type == value_type));
    }

    pub fn freeze_clear(&mut self) {
        self.freezes.clear();
    }

    pub fn freeze_set_enabled(&mut self, address: u32, value_type: ValueType, enabled: bool) {
        if let Some(entry) = self
            .freezes
            .iter_mut()
            .find(|e| e.address == address && e.value_type == value_type)
        {
            entry.enabled = enabled;
        }
    }

    /// Returns freeze list as binary buffer.
    ///
    /// Layout: [count: u32] per entry (14 bytes each):
    ///   [addr: u32][vtype: u8][flags: u8 (bit0=enabled, bit1=stale)][value: 8 bytes]
    pub fn freeze_list_binary(&self) -> Vec<u8> {
        let mut buf = Vec::with_capacity(4 + self.freezes.len() * 14);
        buf.extend_from_slice(&(self.freezes.len() as u32).to_le_bytes());
        for e in &self.freezes {
            buf.extend_from_slice(&e.address.to_le_bytes());
            buf.push(e.value_type.tag());
            let flags: u8 = (e.enabled as u8) | ((e.stale as u8) << 1);
            buf.push(flags);
            buf.extend_from_slice(&e.value.to_le_bytes_8());
        }
        buf
    }

    pub fn apply_freezes(&self, heap: &mut Heap) {
        for entry in &self.freezes {
            if entry.enabled && !entry.stale {
                entry.value.write_to(heap, entry.address);
            }
        }
    }

    pub fn on_heap_compacted(&mut self) {
        self.search_clear();
        for entry in &mut self.freezes {
            entry.stale = true;
        }
        for entry in &mut self.watches {
            entry.stale = true;
        }
    }

    // ---- watch management ----

    fn watch_upsert(&mut self, heap: &Heap, address: u32, value_type: ValueType, label: String) {
        if let Some(entry) = self
            .watches
            .iter_mut()
            .find(|e| e.address == address && e.value_type == value_type)
        {
            entry.label = label;
            entry.stale = false;
            return;
        }
        let current = CheatValue::read_from(heap, address, value_type);
        self.watches.push(WatchEntry {
            address,
            value_type,
            label,
            current,
            changed: false,
            stale: false,
        });
    }

    /// Add or update a single watch entry.
    pub fn watch_add(&mut self, heap: &Heap, address: u32, value_type: ValueType, label: String) {
        self.watch_upsert(heap, address, value_type, label);
    }

    /// Add or update multiple watch entries from a binary buffer.
    ///
    /// Buffer layout: [count: u32 LE] then per entry:
    ///   [addr: u32 LE][vtype: u8][label_len: u16 LE][label: label_len bytes UTF-8]
    pub fn watch_add_batch(&mut self, heap: &Heap, buf: &[u8]) {
        if buf.len() < 4 {
            return;
        }
        let count = u32::from_le_bytes([buf[0], buf[1], buf[2], buf[3]]) as usize;
        let mut pos = 4;
        for _ in 0..count {
            if pos + 7 > buf.len() {
                break;
            }
            let addr = u32::from_le_bytes([buf[pos], buf[pos + 1], buf[pos + 2], buf[pos + 3]]);
            let vtype_tag = buf[pos + 4] as i32;
            let label_len = u16::from_le_bytes([buf[pos + 5], buf[pos + 6]]) as usize;
            pos += 7;
            if pos + label_len > buf.len() {
                break;
            }
            let label = std::str::from_utf8(&buf[pos..pos + label_len])
                .unwrap_or("")
                .to_owned();
            pos += label_len;
            if let Some(vtype) = ValueType::from_tag(vtype_tag) {
                self.watch_upsert(heap, addr, vtype, label);
            }
        }
    }

    pub fn watch_remove(&mut self, address: u32, value_type: ValueType) {
        self.watches
            .retain(|e| !(e.address == address && e.value_type == value_type));
    }

    pub fn watch_clear(&mut self) {
        self.watches.clear();
    }

    /// Re-read all watched addresses and mark changed ones. Call once per step.
    pub fn watch_update(&mut self, heap: &Heap) {
        for entry in &mut self.watches {
            if entry.stale {
                continue;
            }
            let new_val = CheatValue::read_from(heap, entry.address, entry.value_type);
            let is_float = matches!(new_val, CheatValue::F32(_) | CheatValue::F64(_));
            entry.changed = if is_float {
                (new_val.as_f64() - entry.current.as_f64()).abs() > f64::EPSILON
            } else {
                new_val.as_i64() != entry.current.as_i64()
            };
            entry.current = new_val;
        }
    }

    pub fn watch_write(&self, heap: &mut Heap, address: u32, value: CheatValue) {
        value.write_to(heap, address);
    }

    /// Serialize the watch list as a binary buffer.
    ///
    /// Layout: [count: u32 LE] then per entry:
    ///   [addr: u32 LE][vtype: u8][flags: u8 (bit0=changed, bit1=stale)][value: 8 bytes LE][label_len: u16 LE][label: label_len bytes UTF-8]
    pub fn watch_list_binary(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        buf.extend_from_slice(&(self.watches.len() as u32).to_le_bytes());
        for e in &self.watches {
            buf.extend_from_slice(&e.address.to_le_bytes());
            buf.push(e.value_type.tag());
            let flags: u8 = (e.changed as u8) | ((e.stale as u8) << 1);
            buf.push(flags);
            buf.extend_from_slice(&e.current.to_le_bytes_8());
            let label_bytes = e.label.as_bytes();
            buf.extend_from_slice(&(label_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(label_bytes);
        }
        buf
    }
}

// ---- stable cheat code (方案 B) ----

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum CheatCodeAction {
    None,
    Write,
    Freeze,
}

impl CheatCodeAction {
    pub fn from_str(s: &str) -> Self {
        match s {
            "write" => CheatCodeAction::Write,
            "freeze" => CheatCodeAction::Freeze,
            _ => CheatCodeAction::None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            CheatCodeAction::None => "none",
            CheatCodeAction::Write => "write",
            CheatCodeAction::Freeze => "freeze",
        }
    }
}

/// One entry in a stable cheat code file. Identifies a static field by class+field name,
/// so it remains valid across sessions as long as the same JAR is loaded.
pub struct CheatCodeEntry {
    pub id: String,
    pub label: String,
    pub class_name: String,
    pub field_name: String,
    pub descriptor: String,
    pub value_type: ValueType,
    pub action: CheatCodeAction,
    /// Value to write/freeze when action != None.
    pub value: Option<CheatValue>,
}

/// Result of resolving one CheatCodeEntry against the live VM.
pub struct CheatCodeResolveResult {
    pub id: String,
    pub addr: u32,
    pub value_type: ValueType,
    pub ok: bool,
    pub error: String,
}

/// Try to resolve a single CheatCodeEntry to a live heap address using the VM.
/// Returns None if the class/field is not found or is not a static field.
pub fn resolve_code_entry(entry: &CheatCodeEntry, vm: &SharedVm) -> CheatCodeResolveResult {
    let result = vm.resolve_field(&entry.class_name, &entry.field_name, &entry.descriptor);
    match result {
        None => CheatCodeResolveResult {
            id: entry.id.clone(),
            addr: 0,
            value_type: entry.value_type,
            ok: false,
            error: format!("field not found: {}.{}", entry.class_name, entry.field_name),
        },
        Some(ResolvedField {
            is_static: false, ..
        }) => CheatCodeResolveResult {
            id: entry.id.clone(),
            addr: 0,
            value_type: entry.value_type,
            ok: false,
            error: format!(
                "{}.{} is not a static field",
                entry.class_name, entry.field_name
            ),
        },
        Some(ResolvedField {
            class_name: owner,
            byte_offset,
            ..
        }) => {
            let base = vm.static_fields.get_addr(&owner);
            if base == 0 {
                CheatCodeResolveResult {
                    id: entry.id.clone(),
                    addr: 0,
                    value_type: entry.value_type,
                    ok: false,
                    error: format!("static object not initialized for {}", owner),
                }
            } else {
                CheatCodeResolveResult {
                    id: entry.id.clone(),
                    addr: base + byte_offset,
                    value_type: entry.value_type,
                    ok: true,
                    error: String::new(),
                }
            }
        }
    }
}

/// Inspect a heap address (already found by method A) and determine if it belongs
/// to a static field. Returns Some(CheatCodeEntry) if yes, None otherwise.
pub fn code_from_candidate(
    heap: &Heap,
    addr: u32,
    value_type: ValueType,
    class_repo: &ClassRepository,
    vm: &SharedVm,
) -> Option<CheatCodeEntry> {
    // Use the existing lookup to find class + field metadata.
    let (class_name, field_name, descriptor) = lookup_field_at(heap, addr, class_repo)?;
    if field_name.is_empty() || descriptor.is_empty() {
        return None;
    }

    // Confirm it is actually a static field.
    let resolved = vm.resolve_field(&class_name, &field_name, &descriptor)?;
    if !resolved.is_static {
        return None;
    }

    // Determine value_type from descriptor if possible, else use the one from search.
    let vtype = ValueType::from_descriptor(&descriptor).unwrap_or(value_type);

    Some(CheatCodeEntry {
        id: format!(
            "{}.{}",
            class_name.rsplit('/').next().unwrap_or(&class_name),
            field_name
        ),
        label: String::new(),
        class_name,
        field_name,
        descriptor,
        value_type: vtype,
        action: CheatCodeAction::None,
        value: None,
    })
}

/// Serialize code_from_candidate result as binary.
///
/// Layout:
///   [status: u8] — 0=error, 1=ok
///   If error: [error_len: u16][error: UTF-8]
///   If ok:    [vtype: u8][id_len: u16][id][class_name_len: u16][class_name]
///             [field_name_len: u16][field_name][descriptor_len: u16][descriptor]
pub fn code_from_candidate_binary(
    heap: &Heap,
    addr: u32,
    value_type: ValueType,
    class_repo: &ClassRepository,
    vm: &SharedVm,
) -> Vec<u8> {
    match code_from_candidate(heap, addr, value_type, class_repo, vm) {
        None => {
            let err = b"not a static field or field not found";
            let mut buf = Vec::with_capacity(3 + err.len());
            buf.push(0); // status = error
            buf.extend_from_slice(&(err.len() as u16).to_le_bytes());
            buf.extend_from_slice(err);
            buf
        }
        Some(entry) => {
            let mut buf = Vec::new();
            buf.push(1); // status = ok
            buf.push(entry.value_type.tag());
            // id
            let id_bytes = entry.id.as_bytes();
            buf.extend_from_slice(&(id_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(id_bytes);
            // class_name
            let cn_bytes = entry.class_name.as_bytes();
            buf.extend_from_slice(&(cn_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(cn_bytes);
            // field_name
            let fn_bytes = entry.field_name.as_bytes();
            buf.extend_from_slice(&(fn_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(fn_bytes);
            // descriptor
            let desc_bytes = entry.descriptor.as_bytes();
            buf.extend_from_slice(&(desc_bytes.len() as u16).to_le_bytes());
            buf.extend_from_slice(desc_bytes);
            buf
        }
    }
}

/// Parse binary input for code_resolve.
///
/// Layout: [count: u32] per entry:
///   [id_len: u16][id][class_name_len: u16][class_name][field_name_len: u16][field_name]
///   [descriptor_len: u16][descriptor][vtype: u8][action: u8][has_value: u8][value: 8 bytes]
pub fn parse_cheat_code_binary(buf: &[u8]) -> Result<Vec<CheatCodeEntry>, &'static str> {
    if buf.len() < 4 {
        return Err("buffer too short");
    }
    let count = u32::from_le_bytes([buf[0], buf[1], buf[2], buf[3]]) as usize;
    let mut pos = 4;
    let mut entries = Vec::with_capacity(count);

    for _ in 0..count {
        // Helper to read a string
        macro_rules! read_str {
            () => {{
                if pos + 2 > buf.len() {
                    return Err("unexpected end of buffer");
                }
                let len = u16::from_le_bytes([buf[pos], buf[pos + 1]]) as usize;
                pos += 2;
                if pos + len > buf.len() {
                    return Err("unexpected end of buffer");
                }
                let s = std::str::from_utf8(&buf[pos..pos + len]).map_err(|_| "invalid utf-8")?;
                pos += len;
                s.to_string()
            }};
        }

        let id = read_str!();
        let class_name = read_str!();
        let field_name = read_str!();
        let descriptor = read_str!();

        if pos + 11 > buf.len() {
            return Err("unexpected end of buffer");
        }
        let vtype_tag = buf[pos];
        pos += 1;
        let action_tag = buf[pos];
        pos += 1;
        let has_value = buf[pos];
        pos += 1;
        // Always consume 8 bytes for value slot
        let value_bytes = &buf[pos..pos + 8];
        pos += 8;

        let value_type = ValueType::from_tag(vtype_tag as i32).ok_or("invalid value_type")?;
        let action = match action_tag {
            0 => CheatCodeAction::None,
            1 => CheatCodeAction::Write,
            2 => CheatCodeAction::Freeze,
            _ => CheatCodeAction::None,
        };
        let value = if has_value != 0 {
            CheatValue::from_le_bytes(value_type, value_bytes)
        } else {
            None
        };

        entries.push(CheatCodeEntry {
            id,
            label: String::new(),
            class_name,
            field_name,
            descriptor,
            value_type,
            action,
            value,
        });
    }
    Ok(entries)
}

#[cfg(test)]
mod object_search_tests {
    use super::*;

    fn field(address: u32, value: i32) -> ObjectSearchField {
        ObjectSearchField {
            address,
            name: format!("field_{address}"),
            descriptor: "I".to_string(),
            value_type: ValueType::I32,
            value: CheatValue::I32(value),
        }
    }

    #[test]
    fn object_contains_matches_values_without_requiring_field_order() {
        let fields = vec![field(8, 10), field(12, 99), field(16, 5), field(20, 3)];
        let matched = find_object_match_indices(&fields, &[5.0, 10.0, 3.0], 0).unwrap();
        assert_eq!(matched, vec![2, 0, 3]);
    }

    #[test]
    fn object_contains_requires_distinct_fields_for_duplicate_values() {
        let fields = vec![field(8, 5), field(12, 10)];
        assert!(find_object_match_indices(&fields, &[5.0, 5.0], 0).is_none());
    }

    #[test]
    fn consecutive_mode_only_matches_adjacent_primitive_fields() {
        let fields = vec![field(8, 5), field(12, 99), field(16, 10), field(20, 12)];
        assert!(find_object_match_indices(&fields, &[5.0, 10.0], 1).is_none());
        assert_eq!(
            find_object_match_indices(&fields, &[10.0, 12.0], 1),
            Some(vec![2, 3])
        );
    }

    #[test]
    fn f32_query_uses_the_stored_float_precision() {
        let fields = vec![ObjectSearchField {
            address: 8,
            name: "ratio".to_string(),
            descriptor: "F".to_string(),
            value_type: ValueType::F32,
            value: CheatValue::F32(0.1),
        }];
        assert_eq!(find_object_match_indices(&fields, &[0.1], 0), Some(vec![0]));
    }

    #[test]
    fn array_preview_never_drops_distant_matches() {
        let matched = vec![2, 80, 160, 240];
        let displayed = array_display_indices(300, &matched);
        for index in matched {
            assert!(displayed.contains(&index), "missing matched index {index}");
        }
        assert!(displayed.len() <= MAX_ARRAY_DISPLAY_ELEMENTS);
    }
}
