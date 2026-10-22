use super::parser::{parse_class, ParsedClassFile};
use super::types::*;
use crate::heap::OBJ_HDR_SIZE;
use std::collections::HashMap;

mod boot_image;

#[path = "class_repo_rewrite.rs"]
mod rewrite;
pub use rewrite::rewrite_super_instructions;

fn array_descriptor_bpe(name: &str) -> u8 {
    if !name.starts_with('[') || name.starts_with("[[") || name.starts_with("[L") {
        return 4;
    }

    match name.as_bytes().get(1).copied() {
        Some(b'Z') | Some(b'B') => 1,
        Some(b'C') | Some(b'S') => 2,
        Some(b'J') | Some(b'D') => 8,
        _ => 4,
    }
}

fn descriptor_object_name(component: &str) -> Option<&str> {
    component
        .strip_prefix('L')
        .and_then(|name| name.strip_suffix(';'))
}

pub struct ClassRepository {
    /// 主存储：按 class_id 索引的 Vec，O(1) 访问。None 表示 slot 已分配但类未加载
    classes: Vec<Option<RuntimeClass>>,
    /// Stable MethodId -> method metadata arena. Classes, vtables and itables
    /// retain only integer IDs.
    method_arena: Vec<RuntimeMethod>,
    /// method_id → max_locals lookup table (for pop_frame without external params)
    max_locals_table: Vec<u16>,
    next_method_id: u32,
    next_class_id: u32,
    /// 类名 → class_id 映射，仅用于初始查找（类加载、bootstrap 等）
    class_name_to_id: HashMap<String, u32>,
    /// JVM internal class name (`java/lang/Object`) → class_id. This avoids
    /// allocating a dotted String while resolving constant-pool call sites.
    internal_class_name_to_id: HashMap<String, u32>,
    /// class_id → 类名反向索引（覆盖所有已分配 ID，包括未加载的数组类型）
    class_name_by_id: Vec<String>,
    /// class_id → bytes_per_element. 0 = 非数组类, 1/2/4/8 = 基本/对象数组.
    array_bpe: Vec<u8>,
    /// Global imethod_id allocation: (name_id, descriptor_id) → imethod_id.
    imethod_key_to_id: HashMap<(u32, u32), u32>,
    next_imethod_id: u32,
    /// Global string table: string_id → String. Used by CpEntry::Utf8.
    /// Reduces CpEntry size from ~32 bytes to ~12 bytes (Utf8 stores u32 instead of String).
    string_table: Vec<String>,
    /// Deduplication map for string interning (only used during class loading).
    string_dedup: HashMap<String, u32>,
}

impl ClassRepository {
    pub fn encode_compact_boot_image(&self, resource_jar: &[u8]) -> Result<Vec<u8>, String> {
        boot_image::encode(self, resource_jar)
    }

    pub fn decode_compact_boot_image(image: &[u8]) -> Option<(Self, &[u8])> {
        boot_image::decode(image)
    }

    pub fn static_layouts(&self) -> impl Iterator<Item = (&str, u32, u32)> {
        self.classes.iter().flatten().map(|class| {
            (
                class.name.as_str(),
                class.size_of_static_fields,
                class.class_id,
            )
        })
    }

    pub fn parsed_class_name(parsed: &ParsedClassFile) -> String {
        let resolve = |id: u32| -> &str { &parsed.raw_strings[id as usize] };
        parsed
            .class_file
            .this_class_name_with(&resolve)
            .replace('/', ".")
    }

    /// Linking needs complete parent/interface metadata for field layout and
    /// dispatch tables. This check lets the JAR loader topologically link
    /// parsed classes without reparsing their bytes on every retry.
    pub fn parsed_dependencies_loaded(&self, parsed: &ParsedClassFile) -> bool {
        let resolve = |id: u32| -> &str { &parsed.raw_strings[id as usize] };
        let parent_ready = parsed
            .class_file
            .super_class_name_with(&resolve)
            .map(|name| name.replace('/', "."))
            .map_or(true, |name| {
                self.get_class_id(&name)
                    .is_some_and(|id| self.is_class_loaded(id))
            });
        parent_ready
            && parsed.class_file.interfaces.iter().all(|&index| {
                parsed
                    .class_file
                    .get_class_name_with(index, &resolve)
                    .map(|name| name.replace('/', "."))
                    .and_then(|name| self.get_class_id(&name))
                    .is_some_and(|id| self.is_class_loaded(id))
            })
    }

    /// Validate invocation forms declared by typed native bindings before loading a class.
    ///
    /// A JVM descriptor does not encode whether a method is static, so this must use the
    /// method access flags from the classfile. Keeping this check before mutation makes a
    /// failed standalone `load_class` atomic with respect to this validation.
    #[cfg(test)]
    pub fn validate_native_binding_invocations(
        &self,
        data: &[u8],
        native_invocation_kinds: &HashMap<
            String,
            crate::interpreter::native::registry::InvocationKind,
        >,
    ) -> Result<(), String> {
        let parsed = parse_class(data)?;
        let resolve = |id: u32| -> &str { &parsed.raw_strings[id as usize] };
        let class_name = parsed
            .class_file
            .this_class_name_with(&resolve)
            .replace('/', ".");
        let mut mismatches = Vec::new();

        for method in &parsed.class_file.methods {
            if method.access_flags & ACC_NATIVE == 0 {
                continue;
            }
            let name = parsed
                .class_file
                .get_utf8_with(method.name_index, &resolve)
                .unwrap_or("");
            let descriptor = parsed
                .class_file
                .get_utf8_with(method.descriptor_index, &resolve)
                .unwrap_or("");
            let key = format!("{class_name}.{name}{descriptor}");
            let Some(kind) = native_invocation_kinds.get(&key) else {
                continue;
            };

            let binding_is_static = matches!(
                kind,
                crate::interpreter::native::registry::InvocationKind::Static
            );
            let classfile_is_static = method.access_flags & ACC_STATIC != 0;
            if binding_is_static != classfile_is_static {
                mismatches.push(format!(
                    "native binding mismatch: {key}: binding declares {}, classfile declares {}",
                    if binding_is_static {
                        "static"
                    } else {
                        "instance"
                    },
                    if classfile_is_static {
                        "static"
                    } else {
                        "instance"
                    },
                ));
            }
        }

        if mismatches.is_empty() {
            Ok(())
        } else {
            Err(mismatches.join("\n"))
        }
    }

    /// Return fully-qualified keys for every method declared in a classfile.
    /// Keys use the same `Class.method(descriptor)` form as NativeRegistry.
    #[cfg(test)]
    pub fn declared_method_keys(&self, data: &[u8]) -> Result<Vec<String>, String> {
        let parsed = parse_class(data)?;
        let resolve = |id: u32| -> &str { &parsed.raw_strings[id as usize] };
        let class_name = parsed
            .class_file
            .this_class_name_with(&resolve)
            .replace('/', ".");

        Ok(parsed
            .class_file
            .methods
            .iter()
            .filter(|method| method.access_flags & ACC_NATIVE != 0)
            .map(|method| {
                let name = parsed
                    .class_file
                    .get_utf8_with(method.name_index, &resolve)
                    .unwrap_or("");
                let descriptor = parsed
                    .class_file
                    .get_utf8_with(method.descriptor_index, &resolve)
                    .unwrap_or("");
                format!("{class_name}.{name}{descriptor}")
            })
            .collect())
    }

    pub fn new() -> Self {
        let mut repo = Self {
            classes: vec![None], // slot 0 unused (class IDs start at 1)
            method_arena: vec![RuntimeMethod {
                name_id: 0,
                descriptor_id: 0,
                access_flags: 0,
                code: None,
                class_id: 0,
                method_id: 0,
                vtable_index: u32::MAX,
                imethod_id: u32::MAX,
                native_method_id: u32::MAX,
                param_slots: 0,
            }],
            max_locals_table: vec![0], // slot 0 is the dummy method
            next_method_id: 1,
            next_class_id: 1,
            class_name_to_id: HashMap::new(),
            internal_class_name_to_id: HashMap::new(),
            class_name_by_id: vec![String::new()], // slot 0 unused
            array_bpe: Vec::new(),
            imethod_key_to_id: HashMap::new(),
            next_imethod_id: 0,
            string_table: vec![String::new()], // slot 0 = empty string
            string_dedup: HashMap::new(),
        };
        repo.register_primitive_arrays();
        repo
    }

    /// Intern a string into the global string table. Returns a deduplicated string_id.
    pub fn intern_string(&mut self, s: &str) -> u32 {
        if let Some(&id) = self.string_dedup.get(s) {
            return id;
        }
        let id = self.string_table.len() as u32;
        self.string_table.push(s.to_string());
        self.string_dedup.insert(s.to_string(), id);
        id
    }

    /// Look up a string by string_id. O(1) Vec index.
    #[inline]
    pub fn get_string(&self, string_id: u32) -> &str {
        &self.string_table[string_id as usize]
    }

    /// Convert a parsed CP (with temporary Utf8RawString entries) to interned CP.
    /// The parser produces CpEntry with Utf8 strings stored in a side Vec.
    pub fn intern_cp_from_parsed(
        &mut self,
        raw_cp: Vec<CpEntry>,
        raw_strings: Vec<String>,
    ) -> Vec<CpEntry> {
        let mut cp = raw_cp;
        for entry in cp.iter_mut() {
            if let CpEntry::Utf8 { string_id } = entry {
                let raw_idx = *string_id as usize;
                let s = &raw_strings[raw_idx];
                *string_id = self.intern_string(s);
            }
        }
        cp
    }

    /// Resolve a CpEntry::Utf8 string_id to the actual string.
    /// Convenience method for resolving CP entries.
    #[inline]
    pub fn resolve_utf8<'a>(&'a self, cp: &[CpEntry], index: u16) -> Option<&'a str> {
        match cp.get(index as usize) {
            Some(CpEntry::Utf8 { string_id }) => Some(self.get_string(*string_id)),
            _ => None,
        }
    }

    /// Resolve method name from a RuntimeMethod. O(1) Vec index.
    #[inline]
    pub fn method_name(&self, m: &RuntimeMethod) -> &str {
        self.get_string(m.name_id)
    }

    /// Resolve method descriptor from a RuntimeMethod. O(1) Vec index.
    #[inline]
    pub fn method_descriptor(&self, m: &RuntimeMethod) -> &str {
        self.get_string(m.descriptor_id)
    }

    fn register_primitive_arrays(&mut self) {
        let arrays: &[(&str, u8)] = &[
            ("[Z", 1),
            ("[B", 1),
            ("[C", 2),
            ("[S", 2),
            ("[I", 4),
            ("[F", 4),
            ("[J", 8),
            ("[D", 8),
            ("[L", 4),
        ];
        for &(name, bpe) in arrays {
            self.register_array_class(name, bpe);
        }
    }

    pub fn register_array_class(&mut self, name: &str, bpe: u8) -> u32 {
        let name = name.replace('/', ".");
        let id = self.alloc_class_id(&name);
        self.set_array_bpe(id, bpe);
        self.ensure_array_runtime_class(id, &name);
        id
    }

    pub fn get_or_register_array_class(&mut self, name: &str) -> u32 {
        let name = name.replace('/', ".");
        if let Some(id) = self.get_class_id(&name) {
            if !self.is_class_loaded(id) {
                self.ensure_array_runtime_class(id, &name);
            }
            return id;
        }

        let bpe = array_descriptor_bpe(&name);
        self.register_array_class(&name, bpe)
    }

    /// Array classes are created by the VM rather than loaded from class files,
    /// but they are still real Java reference types. Register minimal
    /// RuntimeClass metadata up front so normal class resolution and assignable
    /// checks can treat arrays the same as other classes.
    fn ensure_array_runtime_class(&mut self, class_id: u32, name: &str) {
        if self.is_class_loaded(class_id) {
            return;
        }

        let object_id = self.alloc_class_id("java.lang.Object");
        let cloneable_id = self.alloc_class_id("java.lang.Cloneable");
        let serializable_id = self.alloc_class_id("java.io.Serializable");

        let interface_ids = vec![cloneable_id, serializable_id];
        let mut all_interface_ids = interface_ids.clone();
        all_interface_ids.sort_unstable();
        all_interface_ids.dedup();

        let rc = RuntimeClass {
            name: name.to_string(),
            super_class_id: object_id,
            interface_ids,
            all_interface_ids,
            access_flags: ACC_PUBLIC | ACC_FINAL,
            constant_pool: Vec::new(),
            methods: Vec::new(),
            fields: Vec::new(),
            class_id,
            size_of_fields: 0,
            size_of_static_fields: 0,
            initialized: true,
            depth: 1,
            display: vec![object_id, class_id],
            vtable: Vec::new(),
            itable: Vec::new(),
            ref_bitmap: Vec::new(),
            static_ref_bitmap: Vec::new(),
            inline_cache: Vec::new(),
        };

        self.classes[class_id as usize] = Some(rc);
    }

    #[inline]
    fn set_array_bpe(&mut self, class_id: u32, bpe: u8) {
        let idx = class_id as usize;
        if idx >= self.array_bpe.len() {
            self.array_bpe.resize(idx + 1, 0);
        }
        self.array_bpe[idx] = bpe;
    }

    #[inline]
    pub fn get_array_bpe(&self, class_id: u32) -> u8 {
        self.array_bpe.get(class_id as usize).copied().unwrap_or(0)
    }

    /// 分配 class_id。如果已存在则返回已有 ID，否则分配新 slot。
    fn alloc_class_id(&mut self, name: &str) -> u32 {
        if let Some(&id) = self.class_name_to_id.get(name) {
            return id;
        }
        let id = self.next_class_id;
        self.next_class_id += 1;
        self.class_name_to_id.insert(name.to_string(), id);
        self.internal_class_name_to_id
            .insert(name.replace('.', "/"), id);
        // 扩展 classes Vec 和 class_name_by_id
        if id as usize >= self.classes.len() {
            self.classes.resize(id as usize + 1, None);
        }
        if id as usize >= self.class_name_by_id.len() {
            self.class_name_by_id.resize(id as usize + 1, String::new());
        }
        self.class_name_by_id[id as usize] = name.to_string();
        id
    }

    pub fn get_class_id(&self, name: &str) -> Option<u32> {
        self.class_name_to_id.get(name).copied()
    }

    #[inline]
    pub fn get_class_id_by_internal_name(&self, name: &str) -> Option<u32> {
        self.internal_class_name_to_id.get(name).copied()
    }

    /// 检查 class_id 对应的 slot 是否已填充（类已加载）
    pub fn is_class_loaded(&self, class_id: u32) -> bool {
        self.classes
            .get(class_id as usize)
            .map_or(false, |opt| opt.is_some())
    }

    /// O(1) 按 class_id 获取类
    pub fn get_class_by_id(&self, class_id: u32) -> Option<&RuntimeClass> {
        self.classes
            .get(class_id as usize)
            .and_then(|opt| opt.as_ref())
    }

    /// O(1) 按 class_id 获取类（可变）
    pub fn get_class_by_id_mut(&mut self, class_id: u32) -> Option<&mut RuntimeClass> {
        self.classes
            .get_mut(class_id as usize)
            .and_then(|opt| opt.as_mut())
    }

    /// 按 class_id 获取类名（O(1)，覆盖所有已分配 ID，包括未加载的数组类型）
    pub fn get_class_name_by_id(&self, id: u32) -> Option<&str> {
        self.class_name_by_id
            .get(id as usize)
            .filter(|s| !s.is_empty())
            .map(|s| s.as_str())
    }

    pub fn load_class_from_bytes(&mut self, data: &[u8]) -> Result<String, String> {
        self.link_parsed_class(parse_class(data)?)
    }

    /// Link an already parsed classfile. JAR loading parses every class once and
    /// retries only this dependency-sensitive link step.
    pub fn link_parsed_class(&mut self, parsed: ParsedClassFile) -> Result<String, String> {
        let raw_strings = parsed.raw_strings;
        let cf = parsed.class_file;
        let resolve = |id: u32| -> &str { &raw_strings[id as usize] };

        let class_name = cf.this_class_name_with(&resolve).replace('/', ".");

        // 检查是否已加载
        if let Some(class_id) = self.class_name_to_id.get(&class_name) {
            if self.is_class_loaded(*class_id) {
                return Ok(class_name);
            }
        }

        let class_id = self.alloc_class_id(&class_name);
        let super_class = cf
            .super_class_name_with(&resolve)
            .map(|s| s.replace('/', "."));
        let interfaces: Vec<String> = cf
            .interfaces
            .iter()
            .filter_map(|&idx| cf.get_class_name_with(idx, &resolve))
            .map(|s| s.replace('/', "."))
            .collect();
        let class_access_flags = cf.access_flags;
        let cp_len = cf.constant_pool.len();

        // 解析 super_class_id（预分配 ID，即使父类尚未加载）
        let super_class_id = if let Some(ref sc) = super_class {
            self.alloc_class_id(sc)
        } else {
            0
        };

        // 解析 interface_ids（预分配 ID，即使接口尚未加载）
        let interface_ids: Vec<u32> = interfaces
            .iter()
            .map(|iname| self.alloc_class_id(iname))
            .collect();

        // Build transitive closure of all interface IDs (sorted, deduped for binary search)
        let mut all_iface_set: Vec<u32> = Vec::new();
        // Inherit from parent class
        if super_class_id > 0 {
            if let Some(parent) = self
                .classes
                .get(super_class_id as usize)
                .and_then(|o| o.as_ref())
            {
                all_iface_set.extend_from_slice(&parent.all_interface_ids);
            }
        }
        // Add direct interfaces + their transitive closures
        for &iid in &interface_ids {
            all_iface_set.push(iid);
            if let Some(iface) = self.classes.get(iid as usize).and_then(|o| o.as_ref()) {
                all_iface_set.extend_from_slice(&iface.all_interface_ids);
            }
        }
        all_iface_set.sort_unstable();
        all_iface_set.dedup();

        let mut size_of_fields: u32 = if let Some(ref sc) = super_class {
            match self.get_class_by_name_internal(sc) {
                Some(c) => c.size_of_fields,
                None => return Err(format!("Super class not loaded: {}", sc)),
            }
        } else {
            0
        };

        let mut size_of_static_fields: u32 = 0;
        let mut runtime_fields = Vec::new();

        for fi in &cf.fields {
            let name = cf
                .get_utf8_with(fi.name_index, &resolve)
                .unwrap_or("")
                .to_string();
            let descriptor = cf
                .get_utf8_with(fi.descriptor_index, &resolve)
                .unwrap_or("")
                .to_string();
            let is_static = fi.access_flags & ACC_STATIC != 0;
            let bsz = descriptor_byte_size(&descriptor);
            let byte_offset = if is_static {
                let off = OBJ_HDR_SIZE + size_of_static_fields;
                size_of_static_fields += bsz;
                off
            } else {
                let off = OBJ_HDR_SIZE + size_of_fields;
                size_of_fields += bsz;
                off
            };
            let name_id = self.intern_string(&name);
            let descriptor_id = self.intern_string(&descriptor);
            runtime_fields.push(RuntimeField {
                name_id,
                descriptor_id,
                access_flags: fi.access_flags,
                class_id,
                byte_offset,
                wide: matches!(descriptor.as_str(), "J" | "D"),
                reference: descriptor.starts_with('L') || descriptor.starts_with('['),
            });
        }

        let mut runtime_methods = Vec::new();
        let first_method_id = self.next_method_id;
        for mi in &cf.methods {
            let name = cf
                .get_utf8_with(mi.name_index, &resolve)
                .unwrap_or("")
                .to_string();
            let descriptor = cf
                .get_utf8_with(mi.descriptor_index, &resolve)
                .unwrap_or("")
                .to_string();
            let method_id = self.next_method_id;
            self.next_method_id += 1;

            let name_id = self.intern_string(&name);
            let descriptor_id = self.intern_string(&descriptor);
            let mut code_attr = mi.code.clone();
            if let Some(ref mut ca) = code_attr {
                rewrite_super_instructions(&mut ca.code);
            }
            let rm = RuntimeMethod {
                name_id,
                descriptor_id,
                access_flags: mi.access_flags,
                code: code_attr,
                class_id: class_id,
                method_id,
                vtable_index: u32::MAX,
                imethod_id: u32::MAX,
                native_method_id: u32::MAX,
                param_slots: RuntimeMethod::compute_param_slots(
                    &descriptor,
                    mi.access_flags & ACC_STATIC != 0,
                ),
            };

            runtime_methods.push(rm);
        }

        // Build vtable: inherit from super as array, then override or append.
        let (mut vtable, mut vtable_map) = if let Some(ref sc) = super_class {
            if let Some(c) = self.get_class_by_name_internal(sc) {
                let mut map = HashMap::new();
                for (idx, &mid) in c.vtable.iter().enumerate() {
                    if let Some(m) = self.method_arena.get(mid as usize) {
                        let key = (m.name_id, m.descriptor_id);
                        map.insert(key, idx as u32);
                    }
                }
                (c.vtable.clone(), map)
            } else {
                (Vec::new(), HashMap::new())
            }
        } else {
            (Vec::new(), HashMap::new())
        };
        for m in &mut runtime_methods {
            if !m.is_static()
                && self.get_string(m.name_id) != "<init>"
                && self.get_string(m.name_id) != "<clinit>"
            {
                let key = (m.name_id, m.descriptor_id);
                if let Some(&existing_idx) = vtable_map.get(&key) {
                    vtable[existing_idx as usize] = m.method_id;
                    m.vtable_index = existing_idx;
                } else {
                    let idx = vtable.len() as u32;
                    vtable.push(m.method_id);
                    vtable_map.insert(key, idx);
                    m.vtable_index = idx;
                }
            }
        }

        // Synthesize vtable entries for interface methods not yet in vtable.
        // This ensures all subclasses share the same vtable index for inherited interface methods,
        // enabling correct INVOKEVIRTUAL dispatch (similar to pluotsorbet's createAbstractMethods).
        let is_interface_class = class_access_flags & ACC_INTERFACE != 0;
        if !is_interface_class {
            // Collect all interface method signatures that need vtable entries
            let iface_methods: Vec<((u32, u32), u32)> = all_iface_set
                .iter()
                .filter_map(|&iid| self.classes.get(iid as usize).and_then(|o| o.as_ref()))
                .flat_map(|iface| {
                    iface
                        .methods
                        .iter()
                        .filter_map(|&method_id| {
                            let method = self.method_arena.get(method_id as usize)?;
                            (!method.is_static() && self.get_string(method.name_id) != "<clinit>")
                                .then_some(((method.name_id, method.descriptor_id), method_id))
                        })
                        .collect::<Vec<_>>()
                })
                .collect();
            for (key, fallback_mid) in iface_methods {
                if !vtable_map.contains_key(&key) {
                    let idx = vtable.len() as u32;
                    vtable.push(fallback_mid);
                    vtable_map.insert(key, idx);
                }
            }
        }

        // Assign imethod_ids for interface methods
        if is_interface_class {
            for m in &mut runtime_methods {
                if !m.is_static() && self.get_string(m.name_id) != "<clinit>" {
                    let key = (m.name_id, m.descriptor_id);
                    let imid = self.get_or_alloc_imethod_id(&key);
                    m.imethod_id = imid;
                }
            }
        }

        // Register all methods in the unique arena. RuntimeClass retains only IDs.
        let last_method_id = first_method_id + runtime_methods.len() as u32;
        if last_method_id as usize > self.method_arena.len() {
            self.method_arena
                .resize(last_method_id as usize, self.method_arena[0].clone());
            self.max_locals_table.resize(last_method_id as usize, 0);
        }
        for m in &runtime_methods {
            let mid = m.method_id as usize;
            self.max_locals_table[mid] = m.max_locals();
        }
        let method_ids = runtime_methods
            .iter()
            .map(|method| method.method_id)
            .collect();
        for method in runtime_methods {
            let method_id = method.method_id as usize;
            self.method_arena[method_id] = method;
        }

        // Build itable as Vec<u32> indexed by imethod_id
        let mut itable: Vec<u32> = if let Some(ref sc) = super_class {
            self.get_class_by_name_internal(sc)
                .map_or_else(Vec::new, |c| c.itable.clone())
        } else {
            Vec::new()
        };
        // Update inherited itable entries with overridden methods from current class's vtable
        for method_id_slot in itable.iter_mut() {
            if *method_id_slot != u32::MAX {
                if let Some(old_method) = self.method_arena.get(*method_id_slot as usize) {
                    let key = (old_method.name_id, old_method.descriptor_id);
                    if let Some(&vtable_idx) = vtable_map.get(&key) {
                        if let Some(&new_mid) = vtable.get(vtable_idx as usize) {
                            *method_id_slot = new_mid;
                        }
                    }
                }
            }
        }
        for iface_name in &interfaces {
            self.build_itable_vec(iface_name, &vtable_map, &vtable, &mut itable);
        }

        // Build ref bitmaps
        let mut all_instance_fields: Vec<&RuntimeField> =
            runtime_fields.iter().filter(|f| !f.is_static()).collect();
        if let Some(ref sc) = super_class {
            if let Some(parent) = self.get_class_by_name_internal(sc) {
                for f in &parent.fields {
                    if !f.is_static() && f.is_reference() {
                        all_instance_fields.push(f);
                    }
                }
            }
        }

        let ref_bitmap = build_ref_bitmap(&runtime_fields, false);
        let ref_bitmap = if let Some(ref sc) = super_class {
            if let Some(parent) = self.get_class_by_name_internal(sc) {
                merge_ref_bitmaps(&parent.ref_bitmap, &ref_bitmap)
            } else {
                ref_bitmap
            }
        } else {
            ref_bitmap
        };
        let static_ref_bitmap = build_ref_bitmap(&runtime_fields, true);

        let inline_cache = vec![CachedCpEntry::Empty; cp_len];

        // Intern CP strings into global string table
        let interned_cp = self.intern_cp_from_parsed(cf.constant_pool, raw_strings);

        // Build depth and Cohen's display
        let (depth, display) = if let Some(ref sc) = super_class {
            if let Some(parent) = self.get_class_by_name_internal(sc) {
                let d = parent.depth + 1;
                let mut disp = parent.display.clone();
                // Ensure display is long enough
                if class_id as usize >= disp.len() {
                    disp.resize(d as usize + 1, 0);
                }
                disp[d as usize] = class_id;
                (d, disp)
            } else {
                (0, vec![class_id])
            }
        } else {
            // java.lang.Object: depth = 0
            (0, vec![class_id])
        };

        let rc = RuntimeClass {
            name: class_name.clone(),
            super_class_id,
            interface_ids,
            all_interface_ids: all_iface_set,
            access_flags: class_access_flags,
            constant_pool: interned_cp,
            methods: method_ids,
            fields: runtime_fields,
            class_id,
            size_of_fields,
            size_of_static_fields,
            initialized: false,
            depth,
            display,
            vtable,
            itable,
            ref_bitmap,
            static_ref_bitmap,
            inline_cache,
        };

        self.classes[class_id as usize] = Some(rc);
        Ok(class_name)
    }

    /// Assign native_method_id to all native methods of a newly loaded class.
    /// Called by SharedVm::load_class() after load_class_from_bytes() succeeds.
    /// RuntimeClass stores only IDs, so binding mutates the unique arena entry.
    /// `native_key_to_id` is the NativeRegistry's key→id map, passed separately
    /// to avoid borrow checker issues (SharedVm owns both class_repo and native_registry).
    pub fn assign_native_ids(
        &mut self,
        class_name: &str,
        native_key_to_id: &HashMap<String, u32>,
        native_invocation_kinds: &HashMap<
            String,
            crate::interpreter::native::registry::InvocationKind,
        >,
    ) -> Result<(), String> {
        let class_id = match self.class_name_to_id.get(class_name) {
            Some(&id) => id,
            None => return Ok(()),
        };
        let class = match self.classes.get(class_id as usize).and_then(|o| o.as_ref()) {
            Some(c) => c,
            None => return Ok(()),
        };

        // Collect (method_index, method_id, native_id) to apply later
        let mut updates = Vec::new();
        let mut mismatches = Vec::new();
        for &method_id in &class.methods {
            let Some(method) = self.method_arena.get(method_id as usize) else {
                continue;
            };
            if method.is_native() {
                let key = format!(
                    "{}.{}{}",
                    class.name,
                    self.get_string(method.name_id),
                    self.get_string(method.descriptor_id)
                );
                if let Some(&native_id) = native_key_to_id.get(&key) {
                    if let Some(kind) = native_invocation_kinds.get(&key) {
                        let binding_is_static = matches!(
                            kind,
                            crate::interpreter::native::registry::InvocationKind::Static
                        );
                        if binding_is_static != method.is_static() {
                            mismatches.push(format!(
                                "native binding mismatch: {key}: binding declares {}, classfile declares {}",
                                if binding_is_static { "static" } else { "instance" },
                                if method.is_static() { "static" } else { "instance" },
                            ));
                        }
                    }
                    updates.push((method_id, native_id));
                }
            }
        }

        if !mismatches.is_empty() {
            return Err(mismatches.join("\n"));
        }

        for (method_id, native_id) in updates {
            if let Some(method) = self.method_arena.get_mut(method_id as usize) {
                method.native_method_id = native_id;
            }
        }
        Ok(())
    }

    /// 内部方法：按名称获取类（用于内部调用）
    #[inline]
    fn get_class_by_name_internal(&self, name: &str) -> Option<&RuntimeClass> {
        self.class_name_to_id
            .get(name)
            .and_then(|&id| self.classes.get(id as usize))
            .and_then(|opt| opt.as_ref())
    }

    #[inline]
    fn get_class_by_name_internal_mut(&mut self, name: &str) -> Option<&mut RuntimeClass> {
        let id = *self.class_name_to_id.get(name)?;
        self.classes
            .get_mut(id as usize)
            .and_then(|opt| opt.as_mut())
    }

    pub fn get_class(&self, name: &str) -> Option<&RuntimeClass> {
        self.get_class_by_name_internal(name)
    }

    pub fn get_class_mut(&mut self, name: &str) -> Option<&mut RuntimeClass> {
        self.get_class_by_name_internal_mut(name)
    }

    pub fn get_method(&self, method_id: MethodId) -> Option<&RuntimeMethod> {
        self.method_arena.get(method_id as usize)
    }

    /// Borrow a method reference without Rc clone — use when &mut ClassRepository is not needed.
    pub fn get_method_ref(&self, method_id: MethodId) -> Option<&RuntimeMethod> {
        self.method_arena.get(method_id as usize)
    }

    /// Stable pointer into the method arena for the interpreter's current frame.
    /// The arena is not structurally mutated during an interpreter step.
    #[inline]
    pub fn get_method_ptr(&self, method_id: MethodId) -> Option<*const RuntimeMethod> {
        self.method_arena
            .get(method_id as usize)
            .map(|method| method as *const RuntimeMethod)
    }

    /// Get the max_locals lookup table as a slice for pop_frame.
    pub fn max_locals_table(&self) -> &[u16] {
        &self.max_locals_table
    }

    pub fn find_method(
        &self,
        class_name: &str,
        method_name: &str,
        descriptor: &str,
    ) -> Option<u32> {
        let class = self.get_class_by_name_internal(class_name)?;
        class.methods.iter().copied().find(|&method_id| {
            self.method_arena
                .get(method_id as usize)
                .is_some_and(|method| {
                    self.get_string(method.name_id) == method_name
                        && self.get_string(method.descriptor_id) == descriptor
                })
        })
    }

    /// Find a method directly in a class (by class_id), without traversing the hierarchy.
    pub fn find_method_by_id(&self, class_id: u32, name: &str, desc: &str) -> Option<u32> {
        let class = self
            .classes
            .get(class_id as usize)
            .and_then(|opt| opt.as_ref())?;
        class.methods.iter().copied().find(|&method_id| {
            self.method_arena
                .get(method_id as usize)
                .is_some_and(|method| {
                    self.get_string(method.name_id) == name
                        && self.get_string(method.descriptor_id) == desc
                })
        })
    }

    pub fn find_method_in_hierarchy(
        &self,
        class_name: &str,
        method_name: &str,
        descriptor: &str,
    ) -> Option<u32> {
        let mut current_id = self.class_name_to_id.get(class_name).copied();
        while let Some(id) = current_id {
            if let Some(class) = self.classes.get(id as usize).and_then(|opt| opt.as_ref()) {
                if let Some(method_id) = class.methods.iter().copied().find(|&method_id| {
                    self.method_arena
                        .get(method_id as usize)
                        .is_some_and(|method| {
                            self.get_string(method.name_id) == method_name
                                && self.get_string(method.descriptor_id) == descriptor
                        })
                }) {
                    return Some(method_id);
                }
                current_id = if class.super_class_id > 0 {
                    Some(class.super_class_id)
                } else {
                    None
                };
            } else {
                break;
            }
        }
        None
    }

    pub fn find_virtual_method(
        &self,
        class_name: &str,
        method_name: &str,
        descriptor: &str,
    ) -> Option<u32> {
        self.find_method_in_hierarchy(class_name, method_name, descriptor)
    }

    /// O(1) vtable lookup for INVOKEVIRTUAL — array indexed, zero allocation.
    pub fn vtable_lookup(&self, class_name: &str, vtable_index: u32) -> Option<u32> {
        let class = self.get_class_by_name_internal(class_name)?;
        class.vtable.get(vtable_index as usize).copied()
    }

    /// Get or allocate a global imethod_id for an interned method signature.
    pub fn get_or_alloc_imethod_id(&mut self, method_key: &(u32, u32)) -> u32 {
        if let Some(&id) = self.imethod_key_to_id.get(method_key) {
            return id;
        }
        let id = self.next_imethod_id;
        self.next_imethod_id += 1;
        self.imethod_key_to_id.insert(*method_key, id);
        id
    }

    /// Recursively build itable Vec entries from an interface and its super-interfaces.
    fn build_itable_vec(
        &mut self,
        iface_name: &str,
        vtable_map: &HashMap<(u32, u32), u32>,
        vtable: &[u32],
        itable: &mut Vec<u32>,
    ) {
        // Collect interface data first to avoid borrow conflict
        let iface_data: Option<(Vec<(u32, (u32, u32))>, Vec<String>)> =
            self.get_class_by_name_internal(iface_name).map(|iface| {
                let methods: Vec<(u32, (u32, u32))> = iface
                    .methods
                    .iter()
                    .filter_map(|&method_id| {
                        let method = self.method_arena.get(method_id as usize)?;
                        (!method.is_static() && self.get_string(method.name_id) != "<clinit>")
                            .then_some((method_id, (method.name_id, method.descriptor_id)))
                    })
                    .collect();
                let super_ifaces: Vec<String> = iface
                    .interface_ids
                    .iter()
                    .filter_map(|&id| self.get_class_name_by_id(id).map(|s| s.to_string()))
                    .collect();
                (methods, super_ifaces)
            });

        if let Some((methods, super_ifaces)) = iface_data {
            for (fallback_method_id, method_key) in &methods {
                let imethod_id = self.get_or_alloc_imethod_id(method_key);
                let method_id = vtable_map
                    .get(method_key)
                    .and_then(|&idx| vtable.get(idx as usize).copied())
                    .unwrap_or(*fallback_method_id);
                // Resize itable if needed
                if imethod_id as usize >= itable.len() {
                    itable.resize(imethod_id as usize + 1, u32::MAX);
                }
                itable[imethod_id as usize] = method_id;
            }
            for super_iface in &super_ifaces {
                self.build_itable_vec(super_iface, vtable_map, vtable, itable);
            }
        }
    }

    #[cfg(test)]
    pub fn add_class(&mut self, mut class: RuntimeClass, mut methods: Vec<RuntimeMethod>) {
        let name = class.name.clone();
        let class_id = self.alloc_class_id(&name);
        class.class_id = class_id;
        class.methods.clear();
        for m in &mut methods {
            m.class_id = class_id;
            let mid = m.method_id as usize;
            if mid >= self.method_arena.len() {
                self.method_arena
                    .resize(mid + 1, self.method_arena[0].clone());
                self.max_locals_table.resize(mid + 1, 0);
            }
            self.max_locals_table[mid] = m.max_locals();
            class.methods.push(m.method_id);
        }
        for method in methods {
            let method_id = method.method_id as usize;
            self.method_arena[method_id] = method;
        }
        if class_id as usize >= self.classes.len() {
            self.classes.resize(class_id as usize + 1, None);
        }
        self.classes[class_id as usize] = Some(class);
    }

    #[cfg(test)]
    pub fn alloc_method_id(&mut self) -> u32 {
        let id = self.next_method_id;
        self.next_method_id += 1;
        id
    }

    pub fn find_field_in_hierarchy(
        &self,
        class_name: &str,
        field_name: &str,
        descriptor: &str,
    ) -> Option<(&RuntimeField, String)> {
        let mut current_id = self.class_name_to_id.get(class_name).copied();
        while let Some(id) = current_id {
            if let Some(class) = self.classes.get(id as usize).and_then(|opt| opt.as_ref()) {
                if let Some(f) = class.fields.iter().find(|f| {
                    self.get_string(f.name_id) == field_name
                        && self.get_string(f.descriptor_id) == descriptor
                }) {
                    return Some((f, class.name.clone()));
                }
                // Also search interfaces
                for &iface_id in &class.interface_ids {
                    let iface_name = match self.get_class_name_by_id(iface_id) {
                        Some(n) => n.to_string(),
                        None => continue,
                    };
                    if let Some(result) =
                        self.find_field_in_hierarchy(&iface_name, field_name, descriptor)
                    {
                        return Some(result);
                    }
                }
                current_id = if class.super_class_id > 0 {
                    Some(class.super_class_id)
                } else {
                    None
                };
            } else {
                break;
            }
        }
        None
    }

    pub fn find_field_offset(&self, class_name: &str, field_name: &str) -> Option<u32> {
        // 先在当前类中查找
        if let Some(class) = self.get_class_by_name_internal(class_name) {
            if let Some(f) = class
                .fields
                .iter()
                .find(|f| self.get_string(f.name_id) == field_name && !f.is_static())
            {
                return Some(f.byte_offset);
            }
        }
        // 沿继承链查找
        let mut current_id = self
            .class_name_to_id
            .get(class_name)
            .and_then(|&id| self.classes.get(id as usize))
            .and_then(|opt| opt.as_ref())
            .and_then(|c| {
                if c.super_class_id > 0 {
                    Some(c.super_class_id)
                } else {
                    None
                }
            });
        while let Some(id) = current_id {
            if let Some(class) = self.classes.get(id as usize).and_then(|opt| opt.as_ref()) {
                if let Some(f) = class
                    .fields
                    .iter()
                    .find(|f| self.get_string(f.name_id) == field_name && !f.is_static())
                {
                    return Some(f.byte_offset);
                }
                current_id = if class.super_class_id > 0 {
                    Some(class.super_class_id)
                } else {
                    None
                };
            } else {
                break;
            }
        }
        None
    }

    pub fn is_assignable(&self, sub: &str, sup: &str) -> bool {
        if sub == sup {
            return true;
        }
        if sup == "java.lang.Object" {
            return true;
        }

        let sub_id = match self.class_name_to_id.get(sub) {
            Some(&id) => id,
            None => return false,
        };

        let sup_id = match self.class_name_to_id.get(sup) {
            Some(&id) => id,
            None => return false,
        };

        self.is_assignable_by_id_internal(sub_id, sup_id)
    }

    /// 内部方法：按 ID 检查类型兼容性
    /// 非接口类使用 Cohen's display O(1) 检查；接口类回退到递归遍历。
    fn is_assignable_by_id_internal(&self, sub_id: u32, sup_id: u32) -> bool {
        if sub_id == sup_id {
            return true;
        }

        if self.are_array_classes_assignable(sub_id, sup_id) {
            return true;
        }

        let sub_class = match self
            .classes
            .get(sub_id as usize)
            .and_then(|opt| opt.as_ref())
        {
            Some(c) => c,
            None => return false,
        };

        let sup_class = match self
            .classes
            .get(sup_id as usize)
            .and_then(|opt| opt.as_ref())
        {
            Some(c) => c,
            None => return false,
        };

        // Non-interface target: O(1) Cohen's display check
        if !sup_class.is_interface() {
            let d = sup_class.depth as usize;
            return sub_class.display.get(d).copied() == Some(sup_id);
        }

        // Interface target: O(1) binary search on precomputed all_interface_ids
        sub_class.all_interface_ids.binary_search(&sup_id).is_ok()
    }

    fn are_array_classes_assignable(&self, sub_id: u32, sup_id: u32) -> bool {
        let sub_name = match self.get_class_name_by_id(sub_id) {
            Some(name) if name.starts_with('[') => name,
            _ => return false,
        };
        let sup_name = match self.get_class_name_by_id(sup_id) {
            Some(name) if name.starts_with('[') => name,
            _ => return false,
        };

        self.are_array_descriptors_assignable(sub_name, sup_name)
    }

    fn are_array_descriptors_assignable(&self, sub: &str, sup: &str) -> bool {
        if sub == sup {
            return true;
        }

        let sub_component = match sub.strip_prefix('[') {
            Some(component) => component,
            None => return false,
        };
        let sup_component = match sup.strip_prefix('[') {
            Some(component) => component,
            None => return false,
        };

        match (
            sub_component.as_bytes().first(),
            sup_component.as_bytes().first(),
        ) {
            (Some(b'['), Some(b'[')) => {
                self.are_array_descriptors_assignable(sub_component, sup_component)
            }
            (Some(b'['), Some(b'L')) => {
                let sub_array_id = self.get_class_id(sub_component);
                let sup_class_id = descriptor_object_name(sup_component)
                    .and_then(|sup_class| self.get_class_id(sup_class));
                match (sub_array_id, sup_class_id) {
                    (Some(sub_id), Some(sup_id)) => {
                        self.is_assignable_by_id_internal(sub_id, sup_id)
                    }
                    _ => false,
                }
            }
            (Some(b'L'), Some(b'L')) => {
                let sub_class_id =
                    descriptor_object_name(sub_component).and_then(|name| self.get_class_id(name));
                let sup_class_id =
                    descriptor_object_name(sup_component).and_then(|name| self.get_class_id(name));
                match (sub_class_id, sup_class_id) {
                    (Some(sub_id), Some(sup_id)) => {
                        self.is_assignable_by_id_internal(sub_id, sup_id)
                    }
                    _ => false,
                }
            }
            _ => false,
        }
    }

    pub fn has_class(&self, name: &str) -> bool {
        self.class_name_to_id
            .get(name)
            .map_or(false, |&id| self.is_class_loaded(id))
    }

    pub fn class_count(&self) -> usize {
        self.classes.iter().filter(|opt| opt.is_some()).count()
    }

    pub fn get_constant_pool(&self, class_name: &str) -> &[CpEntry] {
        self.get_class_by_name_internal(class_name)
            .map(|c| c.constant_pool.as_slice())
            .unwrap_or(&[])
    }

    /// O(1) constant pool access by class_id (no HashMap lookup).
    pub fn get_constant_pool_by_id(&self, class_id: u32) -> &[CpEntry] {
        self.classes
            .get(class_id as usize)
            .and_then(|opt| opt.as_ref())
            .map(|c| c.constant_pool.as_slice())
            .unwrap_or(&[])
    }

    /// 按需从 constant_pool 解析 Class 条目
    pub fn resolve_cp_class(&self, class_name: &str, index: u16) -> Option<String> {
        let cp = &self.get_class_by_name_internal(class_name)?.constant_pool;
        match cp.get(index as usize)? {
            CpEntry::Class { name_index } => self
                .resolve_utf8(cp, *name_index)
                .map(|s| s.replace('/', ".")),
            _ => None,
        }
    }

    /// O(1) variant using class_id (no HashMap lookup)
    pub fn resolve_cp_class_by_id(&self, class_id: u32, index: u16) -> Option<String> {
        let cp = self.get_constant_pool_by_id(class_id);
        match cp.get(index as usize)? {
            CpEntry::Class { name_index } => self
                .resolve_utf8(cp, *name_index)
                .map(|s| s.replace('/', ".")),
            _ => None,
        }
    }

    /// 按需从 constant_pool 解析 Fieldref 条目
    pub fn resolve_cp_fieldref(
        &self,
        class_name: &str,
        index: u16,
    ) -> Option<(String, String, String)> {
        let cp = &self.get_class_by_name_internal(class_name)?.constant_pool;
        match cp.get(index as usize)? {
            CpEntry::Fieldref {
                class_index,
                name_and_type_index,
            } => {
                let cn = match cp.get(*class_index as usize)? {
                    CpEntry::Class { name_index } => {
                        self.resolve_utf8(cp, *name_index)?.replace('/', ".")
                    }
                    _ => return None,
                };
                let (n, d) = match cp.get(*name_and_type_index as usize)? {
                    CpEntry::NameAndType {
                        name_index,
                        descriptor_index,
                    } => {
                        let n = self.resolve_utf8(cp, *name_index)?.to_string();
                        let d = self.resolve_utf8(cp, *descriptor_index)?.to_string();
                        (n, d)
                    }
                    _ => return None,
                };
                Some((cn, n, d))
            }
            _ => None,
        }
    }

    /// Resolve a field reference using only dense class/string IDs.
    pub fn resolve_cp_fieldref_ids_by_id(
        &self,
        caller_class_id: u32,
        index: u16,
    ) -> Option<(u32, u32, u32)> {
        let cp = self.get_constant_pool_by_id(caller_class_id);
        let (class_index, name_and_type_index) = match cp.get(index as usize)? {
            CpEntry::Fieldref {
                class_index,
                name_and_type_index,
            } => (*class_index, *name_and_type_index),
            _ => return None,
        };
        let class_name_id = match cp.get(class_index as usize)? {
            CpEntry::Class { name_index } => match cp.get(*name_index as usize)? {
                CpEntry::Utf8 { string_id } => *string_id,
                _ => return None,
            },
            _ => return None,
        };
        let (name_id, descriptor_id) = match cp.get(name_and_type_index as usize)? {
            CpEntry::NameAndType {
                name_index,
                descriptor_index,
            } => {
                let name_id = match cp.get(*name_index as usize)? {
                    CpEntry::Utf8 { string_id } => *string_id,
                    _ => return None,
                };
                let descriptor_id = match cp.get(*descriptor_index as usize)? {
                    CpEntry::Utf8 { string_id } => *string_id,
                    _ => return None,
                };
                (name_id, descriptor_id)
            }
            _ => return None,
        };
        let class_id = self.get_class_id_by_internal_name(self.get_string(class_name_id))?;
        Some((class_id, name_id, descriptor_id))
    }

    /// Resolve a method reference entirely through dense IDs. No class, method,
    /// or descriptor String is allocated on the invocation slow path.
    pub fn resolve_cp_methodref_ids_by_id(
        &self,
        caller_class_id: u32,
        index: u16,
    ) -> Option<(u32, u32, u32)> {
        let cp = self.get_constant_pool_by_id(caller_class_id);
        let (class_index, name_and_type_index) = match cp.get(index as usize)? {
            CpEntry::Methodref {
                class_index,
                name_and_type_index,
            }
            | CpEntry::InterfaceMethodref {
                class_index,
                name_and_type_index,
            } => (*class_index, *name_and_type_index),
            _ => return None,
        };
        let class_name_id = match cp.get(class_index as usize)? {
            CpEntry::Class { name_index } => match cp.get(*name_index as usize)? {
                CpEntry::Utf8 { string_id } => *string_id,
                _ => return None,
            },
            _ => return None,
        };
        let (name_id, descriptor_id) = match cp.get(name_and_type_index as usize)? {
            CpEntry::NameAndType {
                name_index,
                descriptor_index,
            } => {
                let name_id = match cp.get(*name_index as usize)? {
                    CpEntry::Utf8 { string_id } => *string_id,
                    _ => return None,
                };
                let descriptor_id = match cp.get(*descriptor_index as usize)? {
                    CpEntry::Utf8 { string_id } => *string_id,
                    _ => return None,
                };
                (name_id, descriptor_id)
            }
            _ => return None,
        };
        let class_id = self.get_class_id_by_internal_name(self.get_string(class_name_id))?;
        Some((class_id, name_id, descriptor_id))
    }

    /// Get inline cache entry by class_id and CP index. O(1) lookup.
    #[inline]
    pub fn get_inline_cache(&self, class_id: u32, index: u16) -> CachedCpEntry {
        self.classes
            .get(class_id as usize)
            .and_then(|opt| opt.as_ref())
            .and_then(|c| c.inline_cache.get(index as usize).copied())
            .unwrap_or(CachedCpEntry::Empty)
    }

    /// Set inline cache entry by class_id and CP index.
    #[inline]
    pub fn set_inline_cache(&mut self, class_id: u32, index: u16, entry: CachedCpEntry) {
        if let Some(c) = self
            .classes
            .get_mut(class_id as usize)
            .and_then(|opt| opt.as_mut())
        {
            if (index as usize) < c.inline_cache.len() {
                c.inline_cache[index as usize] = entry;
            }
        }
    }

    /// Clear all `LdcString` inline cache entries (heap addresses become stale after snapshot restore).
    pub fn clear_ldc_string_caches(&mut self) {
        for slot in self.classes.iter_mut() {
            if let Some(class) = slot.as_mut() {
                for entry in class.inline_cache.iter_mut() {
                    if matches!(entry, CachedCpEntry::LdcString { .. }) {
                        *entry = CachedCpEntry::Empty;
                    }
                }
            }
        }
    }

    /// Capture class initialization flags that are part of VM dynamic state.
    ///
    /// The class repository itself is shared across save/load, but initialized
    /// flags are not static metadata: running code after a save point may
    /// initialize additional classes and allocate their static fields.  On
    /// load_state we must rewind those flags together with `StaticFields`.
    pub fn initialized_class_ids(&self) -> Vec<u32> {
        self.classes
            .iter()
            .filter_map(|slot| {
                let class = slot.as_ref()?;
                class.initialized.then_some(class.class_id)
            })
            .collect()
    }

    pub fn restore_initialized_class_ids(&mut self, initialized_class_ids: &[u32]) {
        let initialized: std::collections::HashSet<u32> =
            initialized_class_ids.iter().copied().collect();
        for slot in self.classes.iter_mut() {
            if let Some(class) = slot.as_mut() {
                class.initialized = initialized.contains(&class.class_id);
            }
        }
    }

    pub fn get_ref_bitmap(&self, class_name: &str) -> Option<&[u8]> {
        self.get_class_by_name_internal(class_name)
            .map(|c| c.ref_bitmap.as_slice())
    }

    pub fn get_static_ref_bitmap(&self, class_name: &str) -> Option<&[u8]> {
        self.get_class_by_name_internal(class_name)
            .map(|c| c.static_ref_bitmap.as_slice())
    }

    pub fn get_ref_bitmap_by_id(&self, class_id: u32) -> &[u8] {
        self.classes
            .get(class_id as usize)
            .and_then(|opt| opt.as_ref())
            .map(|c| c.ref_bitmap.as_slice())
            .unwrap_or(&[])
    }

    // ============ ID-based methods (Phase 2) ============

    /// 沿继承链查找方法，使用 class_id 遍历（零字符串查找）
    pub fn find_method_in_hierarchy_by_id(
        &self,
        class_id: u32,
        name: &str,
        desc: &str,
    ) -> Option<u32> {
        let mut current_id = Some(class_id);
        while let Some(id) = current_id {
            if let Some(class) = self.classes.get(id as usize).and_then(|opt| opt.as_ref()) {
                if let Some(method_id) = class.methods.iter().copied().find(|&method_id| {
                    self.method_arena
                        .get(method_id as usize)
                        .is_some_and(|method| {
                            self.get_string(method.name_id) == name
                                && self.get_string(method.descriptor_id) == desc
                        })
                }) {
                    return Some(method_id);
                }
                current_id = if class.super_class_id > 0 {
                    Some(class.super_class_id)
                } else {
                    None
                };
            } else {
                break;
            }
        }
        None
    }

    /// Hierarchy lookup using interned name/descriptor IDs.
    pub fn find_method_in_hierarchy_by_ids(
        &self,
        class_id: u32,
        name_id: u32,
        descriptor_id: u32,
    ) -> Option<u32> {
        let mut current_id = Some(class_id);
        while let Some(id) = current_id {
            let class = self.classes.get(id as usize)?.as_ref()?;
            if let Some(method_id) = class.methods.iter().copied().find(|&method_id| {
                self.method_arena
                    .get(method_id as usize)
                    .is_some_and(|method| {
                        method.name_id == name_id && method.descriptor_id == descriptor_id
                    })
            }) {
                return Some(method_id);
            }
            current_id = (class.super_class_id > 0).then_some(class.super_class_id);
        }

        // Interface method references may name an interface which inherits the
        // declaration from a parent interface (for example Player.getControl is
        // declared by Controllable). The old string fallback covered this only
        // indirectly through the receiver class. Resolve it explicitly by the
        // precomputed transitive interface IDs so the call site remains ID-only.
        let class = self.classes.get(class_id as usize)?.as_ref()?;
        for &interface_id in &class.all_interface_ids {
            let interface = self.classes.get(interface_id as usize)?.as_ref()?;
            if let Some(method_id) = interface.methods.iter().copied().find(|&method_id| {
                self.method_arena
                    .get(method_id as usize)
                    .is_some_and(|method| {
                        method.name_id == name_id && method.descriptor_id == descriptor_id
                    })
            }) {
                return Some(method_id);
            }
        }
        None
    }

    pub fn find_method_by_ids(
        &self,
        class_id: u32,
        name_id: u32,
        descriptor_id: u32,
    ) -> Option<u32> {
        let class = self.classes.get(class_id as usize)?.as_ref()?;
        class.methods.iter().copied().find(|&method_id| {
            self.method_arena
                .get(method_id as usize)
                .is_some_and(|method| {
                    method.name_id == name_id && method.descriptor_id == descriptor_id
                })
        })
    }

    /// Resolve a symbolic virtual call against both declared methods and the
    /// class's synthesized vtable entries (used by abstract classes that inherit
    /// an interface contract without redeclaring the method).
    pub fn find_virtual_method_by_ids(
        &self,
        class_id: u32,
        name_id: u32,
        descriptor_id: u32,
    ) -> Option<(u32, u32)> {
        let class = self.classes.get(class_id as usize)?.as_ref()?;
        if let Some(result) = class
            .vtable
            .iter()
            .enumerate()
            .find_map(|(index, &method_id)| {
                let method = self.method_arena.get(method_id as usize)?;
                (method.name_id == name_id && method.descriptor_id == descriptor_id)
                    .then_some((method_id, index as u32))
            })
        {
            return Some(result);
        }
        let method_id = self.find_method_in_hierarchy_by_ids(class_id, name_id, descriptor_id)?;
        let vtable_index = self.get_method(method_id)?.vtable_index;
        Some((method_id, vtable_index))
    }

    /// 沿继承链查找字段，返回 (field, owner_class_id)
    pub fn find_field_in_hierarchy_by_id(
        &self,
        class_id: u32,
        name_id: u32,
        descriptor_id: u32,
    ) -> Option<(&RuntimeField, u32)> {
        let mut current_id = Some(class_id);
        while let Some(id) = current_id {
            if let Some(class) = self.classes.get(id as usize).and_then(|opt| opt.as_ref()) {
                if let Some(f) = class
                    .fields
                    .iter()
                    .find(|f| f.name_id == name_id && f.descriptor_id == descriptor_id)
                {
                    return Some((f, class.class_id));
                }
                // Also search interfaces (by ID)
                for &iface_id in &class.interface_ids {
                    if let Some(result) =
                        self.find_field_in_hierarchy_by_id(iface_id, name_id, descriptor_id)
                    {
                        return Some(result);
                    }
                }
                current_id = if class.super_class_id > 0 {
                    Some(class.super_class_id)
                } else {
                    None
                };
            } else {
                break;
            }
        }
        None
    }

    /// O(1) vtable lookup by class_id
    pub fn vtable_lookup_by_id(&self, class_id: u32, vtable_index: u32) -> Option<u32> {
        let class = self.get_class_by_id(class_id)?;
        class.vtable.get(vtable_index as usize).copied()
    }

    /// O(1) itable lookup by class_id and interned method signature IDs.
    pub fn itable_lookup_by_ids(
        &self,
        class_id: u32,
        name_id: u32,
        descriptor_id: u32,
    ) -> Option<u32> {
        let class = self.get_class_by_id(class_id)?;
        let imethod_id = *self.imethod_key_to_id.get(&(name_id, descriptor_id))?;
        let method_id = *class.itable.get(imethod_id as usize)?;
        if method_id == u32::MAX {
            return None;
        }
        Some(method_id)
    }

    /// O(1) itable lookup by class_id and imethod_id — zero string hashing.
    pub fn itable_lookup_by_imethod_id(&self, class_id: u32, imethod_id: u32) -> Option<u32> {
        let class = self.get_class_by_id(class_id)?;
        let method_id = *class.itable.get(imethod_id as usize)?;
        if method_id == u32::MAX {
            return None;
        }
        Some(method_id)
    }

    /// 按 ID 检查类型兼容性（公开接口）
    pub fn is_assignable_by_id(&self, sub_id: u32, sup_id: u32) -> bool {
        self.is_assignable_by_id_internal(sub_id, sup_id)
    }

    /// 查找字段偏移量，使用 class_id（不分配字符串）
    pub fn find_field_offset_by_id(&self, class_id: u32, field_name: &str) -> Option<u32> {
        // 先在当前类中查找
        if let Some(class) = self
            .classes
            .get(class_id as usize)
            .and_then(|opt| opt.as_ref())
        {
            if let Some(f) = class
                .fields
                .iter()
                .find(|f| self.get_string(f.name_id) == field_name && !f.is_static())
            {
                return Some(f.byte_offset);
            }
            // 沿继承链查找
            let mut current_id = if class.super_class_id > 0 {
                Some(class.super_class_id)
            } else {
                None
            };
            while let Some(id) = current_id {
                if let Some(c) = self.classes.get(id as usize).and_then(|opt| opt.as_ref()) {
                    if let Some(f) = c
                        .fields
                        .iter()
                        .find(|f| self.get_string(f.name_id) == field_name && !f.is_static())
                    {
                        return Some(f.byte_offset);
                    }
                    current_id = if c.super_class_id > 0 {
                        Some(c.super_class_id)
                    } else {
                        None
                    };
                } else {
                    break;
                }
            }
        }
        None
    }
}

/// Build a bitmap where bit i = 1 means the 4-byte slot at OBJ_HDR_SIZE + i*4 contains a reference.
fn build_ref_bitmap(fields: &[RuntimeField], is_static_filter: bool) -> Vec<u8> {
    let max_slot = fields
        .iter()
        .filter(|f| f.is_static() == is_static_filter)
        .filter(|f| !f.is_wide())
        .filter(|f| f.is_reference())
        .map(|f| ((f.byte_offset - OBJ_HDR_SIZE) / 4) as usize + 1)
        .max()
        .unwrap_or(0);
    if max_slot == 0 {
        return Vec::new();
    }
    let mut bitmap = vec![0u8; (max_slot + 7) / 8];
    for field in fields
        .iter()
        .filter(|f| f.is_static() == is_static_filter && f.is_reference())
    {
        let slot_index = ((field.byte_offset - OBJ_HDR_SIZE) / 4) as usize;
        bitmap[slot_index / 8] |= 1 << (slot_index % 8);
    }
    bitmap
}

/// Merge parent bitmap into child bitmap (OR operation)
fn merge_ref_bitmaps(parent: &[u8], child: &[u8]) -> Vec<u8> {
    let len = parent.len().max(child.len());
    let mut result = vec![0u8; len];
    for (i, byte) in result.iter_mut().enumerate() {
        let p = parent.get(i).copied().unwrap_or(0);
        let c = child.get(i).copied().unwrap_or(0);
        *byte = p | c;
    }
    result
}

impl Default for ClassRepository {
    fn default() -> Self {
        Self::new()
    }
}
