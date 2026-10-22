use super::ClassRepository;
use crate::classfile::types::{
    CachedCpEntry, CodeAttribute, CpEntry, ExceptionTableEntry, MethodId, RuntimeClass,
    RuntimeField, RuntimeMethod,
};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::io::{Read, Write};

const HEADER_LEN: usize = 8;

#[derive(Serialize, Deserialize)]
struct BootRepository {
    strings: Vec<String>,
    classes: Vec<Option<BootClass>>,
    methods: Vec<BootMethod>,
    array_bpe: Vec<u8>,
    next_method_id: u32,
    next_class_id: u32,
    next_imethod_id: u32,
}

#[derive(Serialize, Deserialize)]
struct BootClass {
    name: String,
    super_class_id: u32,
    interface_ids: Vec<u32>,
    all_interface_ids: Vec<u32>,
    access_flags: u16,
    constant_pool: Vec<CpEntry>,
    methods: Vec<MethodId>,
    fields: Vec<RuntimeField>,
    class_id: u32,
    size_of_fields: u32,
    size_of_static_fields: u32,
    depth: u16,
    vtable: Vec<u32>,
    itable: Vec<u32>,
    ref_bitmap: Vec<u8>,
    static_ref_bitmap: Vec<u8>,
}

#[derive(Serialize, Deserialize)]
struct BootMethod {
    name_id: u32,
    descriptor_id: u32,
    access_flags: u16,
    code: Option<BootCode>,
    class_id: u32,
    method_id: u32,
    vtable_index: u32,
    imethod_id: u32,
    native_method_id: u32,
    param_slots: u8,
}

#[derive(Serialize, Deserialize)]
struct BootCode {
    max_stack: u16,
    max_locals: u16,
    code: Vec<u8>,
    exception_table: Vec<ExceptionTableEntry>,
}

impl From<&RuntimeMethod> for BootMethod {
    fn from(method: &RuntimeMethod) -> Self {
        Self {
            name_id: method.name_id,
            descriptor_id: method.descriptor_id,
            access_flags: method.access_flags,
            code: method.code.as_ref().map(|code| BootCode {
                max_stack: code.max_stack,
                max_locals: code.max_locals,
                code: code.code.clone(),
                exception_table: code.exception_table.clone(),
            }),
            class_id: method.class_id,
            method_id: method.method_id,
            vtable_index: method.vtable_index,
            imethod_id: method.imethod_id,
            native_method_id: method.native_method_id,
            param_slots: method.param_slots,
        }
    }
}

impl From<BootMethod> for RuntimeMethod {
    fn from(method: BootMethod) -> Self {
        Self {
            name_id: method.name_id,
            descriptor_id: method.descriptor_id,
            access_flags: method.access_flags,
            code: method.code.map(|code| CodeAttribute {
                max_stack: code.max_stack,
                max_locals: code.max_locals,
                code: code.code,
                exception_table: code.exception_table,
                attributes: Vec::new(),
            }),
            class_id: method.class_id,
            method_id: method.method_id,
            vtable_index: method.vtable_index,
            imethod_id: method.imethod_id,
            native_method_id: method.native_method_id,
            param_slots: method.param_slots,
        }
    }
}

pub(super) fn encode(repo: &ClassRepository, resource_jar: &[u8]) -> Result<Vec<u8>, String> {
    let classes = repo
        .classes
        .iter()
        .map(|slot| {
            slot.as_ref().map(|class| BootClass {
                name: class.name.clone(),
                super_class_id: class.super_class_id,
                interface_ids: class.interface_ids.clone(),
                all_interface_ids: class.all_interface_ids.clone(),
                access_flags: class.access_flags,
                constant_pool: class.constant_pool.clone(),
                methods: class.methods.clone(),
                fields: class.fields.clone(),
                class_id: class.class_id,
                size_of_fields: class.size_of_fields,
                size_of_static_fields: class.size_of_static_fields,
                depth: class.depth,
                vtable: class.vtable.clone(),
                itable: class.itable.clone(),
                ref_bitmap: class.ref_bitmap.clone(),
                static_ref_bitmap: class.static_ref_bitmap.clone(),
            })
        })
        .collect();
    let boot = BootRepository {
        strings: repo.string_table.clone(),
        classes,
        methods: repo.method_arena.iter().map(BootMethod::from).collect(),
        array_bpe: repo.array_bpe.clone(),
        next_method_id: repo.next_method_id,
        next_class_id: repo.next_class_id,
        next_imethod_id: repo.next_imethod_id,
    };
    let serialized = bincode::serialize(&boot).map_err(|error| error.to_string())?;
    let mut encoder = flate2::write::ZlibEncoder::new(Vec::new(), flate2::Compression::best());
    encoder
        .write_all(&serialized)
        .map_err(|error| error.to_string())?;
    let compressed = encoder.finish().map_err(|error| error.to_string())?;
    let metadata_len = u32::try_from(compressed.len())
        .map_err(|_| "compact boot metadata is too large".to_string())?;
    let resource_len = u32::try_from(resource_jar.len())
        .map_err(|_| "compact boot resources are too large".to_string())?;
    let mut image = Vec::with_capacity(HEADER_LEN + compressed.len() + resource_jar.len());
    image.extend_from_slice(&metadata_len.to_le_bytes());
    image.extend_from_slice(&resource_len.to_le_bytes());
    image.extend_from_slice(&compressed);
    image.extend_from_slice(resource_jar);
    Ok(image)
}

pub(super) fn decode(image: &[u8]) -> Option<(ClassRepository, &[u8])> {
    if image.len() < HEADER_LEN {
        return None;
    }
    let metadata_len = u32::from_le_bytes(image[0..4].try_into().ok()?) as usize;
    let resource_len = u32::from_le_bytes(image[4..8].try_into().ok()?) as usize;
    let metadata_end = HEADER_LEN.checked_add(metadata_len)?;
    let resource_end = metadata_end.checked_add(resource_len)?;
    if resource_end != image.len() {
        return None;
    }
    let mut decoder = flate2::read::ZlibDecoder::new(&image[HEADER_LEN..metadata_end]);
    let mut serialized = Vec::new();
    decoder.read_to_end(&mut serialized).ok()?;
    let boot: BootRepository = bincode::deserialize(&serialized).ok()?;

    if boot.methods.len() != boot.next_method_id as usize {
        return None;
    }
    let strings = boot.strings;
    let method_arena: Vec<RuntimeMethod> = boot.methods.into_iter().map(Into::into).collect();
    if method_arena
        .iter()
        .enumerate()
        .any(|(method_id, method)| method.method_id as usize != method_id)
    {
        return None;
    }
    let max_locals_table = method_arena.iter().map(RuntimeMethod::max_locals).collect();
    let imethod_key_to_id = method_arena
        .iter()
        .filter(|method| method.imethod_id != u32::MAX)
        .map(|method| ((method.name_id, method.descriptor_id), method.imethod_id))
        .collect();
    let mut class_name_to_id = HashMap::new();
    let mut internal_class_name_to_id = HashMap::new();
    let mut class_name_by_id = vec![String::new(); boot.classes.len()];
    let mut classes = Vec::with_capacity(boot.classes.len());

    for slot in boot.classes {
        let Some(class) = slot else {
            classes.push(None);
            continue;
        };
        let name = class.name;
        class_name_to_id.insert(name.clone(), class.class_id);
        internal_class_name_to_id.insert(name.replace('.', "/"), class.class_id);
        if class.class_id as usize >= class_name_by_id.len() {
            return None;
        }
        class_name_by_id[class.class_id as usize] = name.clone();
        if class.methods.iter().any(|&method_id| {
            method_arena
                .get(method_id as usize)
                .is_none_or(|method| method.class_id != class.class_id)
        }) {
            return None;
        }
        let display = rebuild_display(&classes, class.super_class_id, class.depth, class.class_id);
        let inline_cache = vec![CachedCpEntry::Empty; class.constant_pool.len()];
        classes.push(Some(RuntimeClass {
            name,
            super_class_id: class.super_class_id,
            interface_ids: class.interface_ids,
            all_interface_ids: class.all_interface_ids,
            access_flags: class.access_flags,
            constant_pool: class.constant_pool,
            methods: class.methods,
            fields: class.fields,
            class_id: class.class_id,
            size_of_fields: class.size_of_fields,
            size_of_static_fields: class.size_of_static_fields,
            initialized: false,
            depth: class.depth,
            display,
            vtable: class.vtable,
            itable: class.itable,
            ref_bitmap: class.ref_bitmap,
            static_ref_bitmap: class.static_ref_bitmap,
            inline_cache,
        }));
    }
    let string_dedup = strings
        .iter()
        .enumerate()
        .map(|(id, value)| (value.clone(), id as u32))
        .collect();
    let repo = ClassRepository {
        classes,
        method_arena,
        max_locals_table,
        next_method_id: boot.next_method_id,
        next_class_id: boot.next_class_id,
        class_name_to_id,
        internal_class_name_to_id,
        class_name_by_id,
        array_bpe: boot.array_bpe,
        imethod_key_to_id,
        next_imethod_id: boot.next_imethod_id,
        string_table: strings,
        string_dedup,
    };
    Some((repo, &image[metadata_end..resource_end]))
}

fn rebuild_display(
    classes: &[Option<RuntimeClass>],
    super_class_id: u32,
    depth: u16,
    class_id: u32,
) -> Vec<u32> {
    let mut display = if super_class_id == 0 {
        Vec::new()
    } else {
        classes
            .get(super_class_id as usize)
            .and_then(Option::as_ref)
            .map(|parent| parent.display.clone())
            .unwrap_or_default()
    };
    display.resize(depth as usize + 1, 0);
    display[depth as usize] = class_id;
    display
}
