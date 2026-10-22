use serde::{Deserialize, Serialize};

pub type MethodId = u32;

pub type Slot = u32;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClassFile {
    pub minor_version: u16,
    pub major_version: u16,
    pub constant_pool: Vec<CpEntry>,
    pub access_flags: u16,
    pub this_class: u16,
    pub super_class: u16,
    pub interfaces: Vec<u16>,
    pub fields: Vec<FieldInfo>,
    pub methods: Vec<MethodInfo>,
    pub attributes: Vec<AttributeInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum CpEntry {
    Empty,
    /// Utf8 string stored as index into ClassRepository::string_table.
    /// sizeof reduced from 24 bytes (String) to 4 bytes (u32).
    Utf8 {
        string_id: u32,
    },
    Integer(i32),
    Float(f32),
    Long(i64),
    Double(f64),
    Class {
        name_index: u16,
    },
    String {
        string_index: u16,
    },
    Fieldref {
        class_index: u16,
        name_and_type_index: u16,
    },
    Methodref {
        class_index: u16,
        name_and_type_index: u16,
    },
    InterfaceMethodref {
        class_index: u16,
        name_and_type_index: u16,
    },
    NameAndType {
        name_index: u16,
        descriptor_index: u16,
    },
}

/// Fully-resolved inline cache entry: populated lazily on first access.
/// Once resolved, the interpreter can skip hierarchy traversal entirely.
#[derive(Debug, Clone, Copy)]
pub enum CachedCpEntry {
    /// Not yet resolved / not applicable
    Empty,
    /// Resolved field: direct byte_offset + metadata
    Field {
        owner_class_id: u32,
        byte_offset: u32,
        is_wide: bool,
        is_static: bool,
    },
    /// Resolved method: direct method_id + dispatch info
    Method {
        method_id: u32,
        vtable_index: u32, // u32::MAX if not virtual
        imethod_id: u32,   // u32::MAX if not interface method
    },
    /// Resolved class: direct class_id
    Class { class_id: u32 },
    /// Resolved LDC int/float constant (cached value bits)
    LdcInt { value: u32 },
    /// Resolved LDC string constant (heap address of interned String)
    LdcString { heap_addr: u32 },
    /// Resolved LDC2 long/double constant (low and high 32 bits)
    Ldc2Wide { lo: u32, hi: u32 },
}

impl Default for CachedCpEntry {
    fn default() -> Self {
        CachedCpEntry::Empty
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FieldInfo {
    pub access_flags: u16,
    pub name_index: u16,
    pub descriptor_index: u16,
    pub attributes: Vec<AttributeInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MethodInfo {
    pub access_flags: u16,
    pub name_index: u16,
    pub descriptor_index: u16,
    pub attributes: Vec<AttributeInfo>,
    pub code: Option<CodeAttribute>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AttributeInfo {
    pub attribute_name_index: u16,
    pub info: Vec<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CodeAttribute {
    pub max_stack: u16,
    pub max_locals: u16,
    pub code: Vec<u8>,
    pub exception_table: Vec<ExceptionTableEntry>,
    pub attributes: Vec<AttributeInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExceptionTableEntry {
    pub start_pc: u16,
    pub end_pc: u16,
    pub handler_pc: u16,
    pub catch_type: u16,
}

pub const ACC_PUBLIC: u16 = 0x0001;
pub const ACC_PRIVATE: u16 = 0x0002;
pub const ACC_PROTECTED: u16 = 0x0004;
pub const ACC_STATIC: u16 = 0x0008;
pub const ACC_FINAL: u16 = 0x0010;
pub const ACC_SYNCHRONIZED: u16 = 0x0020;
pub const ACC_SUPER: u16 = 0x0020;
pub const ACC_VOLATILE: u16 = 0x0040;
pub const ACC_BRIDGE: u16 = 0x0040;
pub const ACC_TRANSIENT: u16 = 0x0080;
pub const ACC_VARARGS: u16 = 0x0080;
pub const ACC_NATIVE: u16 = 0x0100;
pub const ACC_INTERFACE: u16 = 0x0200;
pub const ACC_ABSTRACT: u16 = 0x0400;
pub const ACC_STRICT: u16 = 0x0800;
pub const ACC_SYNTHETIC: u16 = 0x1000;
pub const ACC_ANNOTATION: u16 = 0x2000;
pub const ACC_ENUM: u16 = 0x4000;

impl ClassFile {
    /// Resolve a Utf8 CP entry using a string resolver function.
    /// During parsing: pass raw_strings lookup. After interning: pass ClassRepository::get_string.
    pub fn get_utf8_with<'a, F: Fn(u32) -> &'a str>(
        &self,
        index: u16,
        resolve: &F,
    ) -> Option<&'a str> {
        match self.constant_pool.get(index as usize) {
            Some(CpEntry::Utf8 { string_id }) => Some(resolve(*string_id)),
            _ => None,
        }
    }

    pub fn get_class_name_with<'a, F: Fn(u32) -> &'a str>(
        &self,
        index: u16,
        resolve: &F,
    ) -> Option<&'a str> {
        match self.constant_pool.get(index as usize) {
            Some(CpEntry::Class { name_index }) => self.get_utf8_with(*name_index, resolve),
            _ => None,
        }
    }

    pub fn this_class_name_with<'a, F: Fn(u32) -> &'a str>(&self, resolve: &F) -> &'a str {
        self.get_class_name_with(self.this_class, resolve)
            .unwrap_or("")
    }

    pub fn super_class_name_with<'a, F: Fn(u32) -> &'a str>(&self, resolve: &F) -> Option<&'a str> {
        if self.super_class == 0 {
            None
        } else {
            self.get_class_name_with(self.super_class, resolve)
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeClass {
    pub name: String,
    pub super_class_id: u32,     // 0 表示无父类 (java.lang.Object)
    pub interface_ids: Vec<u32>, // 直接接口的 class_id 列表
    /// Transitive closure of all implemented interface IDs (direct + inherited).
    /// Used for O(1) interface subtype check via binary search or contains.
    #[serde(default)]
    pub all_interface_ids: Vec<u32>,
    pub access_flags: u16,
    pub constant_pool: Vec<CpEntry>,
    /// Declared methods in classfile order. Metadata lives once in the
    /// repository-wide method arena and is referenced by stable integer ID.
    pub methods: Vec<MethodId>,
    pub fields: Vec<RuntimeField>,
    pub class_id: u32,
    pub size_of_fields: u32,
    pub size_of_static_fields: u32,
    pub initialized: bool,
    /// Depth in class hierarchy (java.lang.Object = 0)
    pub depth: u16,
    /// Cohen's display: display[i] = class_id of ancestor at depth i. O(1) subtype check.
    #[serde(skip)]
    #[serde(default)]
    pub display: Vec<u32>,

    /// Virtual method table: vtable_index → method_id.
    /// Built at class load time by inheriting from super + overriding.
    /// INVOKEVIRTUAL uses array indexing: vtable[method.vtable_index] → O(1), zero alloc.
    pub vtable: Vec<u32>,
    /// Interface method table: imethod_id → method_id.
    /// Indexed by globally allocated imethod_id. Contains all interface methods this class implements.
    pub itable: Vec<u32>,
    /// Bitmap for instance reference fields: bit i=1 means slot at byte_offset = OBJ_HDR_SIZE + i*4 is a reference
    pub ref_bitmap: Vec<u8>,
    /// Bitmap for static reference fields
    pub static_ref_bitmap: Vec<u8>,
    /// Inline cache: CP index → fully resolved field/method entry.
    /// Populated lazily on first access; avoids repeated hierarchy traversal.
    #[serde(skip)]
    #[serde(default)]
    pub inline_cache: Vec<CachedCpEntry>,
}

impl RuntimeClass {
    pub fn is_interface(&self) -> bool {
        self.access_flags & ACC_INTERFACE != 0
    }
    pub fn is_abstract(&self) -> bool {
        self.access_flags & ACC_ABSTRACT != 0
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeMethod {
    /// Index into ClassRepository::string_table for method name.
    pub name_id: u32,
    /// Index into ClassRepository::string_table for method descriptor.
    pub descriptor_id: u32,
    pub access_flags: u16,
    pub code: Option<CodeAttribute>,
    pub class_id: u32,
    pub method_id: MethodId,
    /// Index into the owning class's vtable array. Assigned at class load time.
    /// INVOKEVIRTUAL uses this for O(1) array-indexed dispatch with zero allocation.
    pub vtable_index: u32,
    /// Global interface method ID. Assigned at class load time for interface methods.
    /// u32::MAX for non-interface methods.
    #[serde(default = "default_u32_max")]
    pub imethod_id: u32,
    /// Native method dispatch ID. u32::MAX if not a registered native method.
    #[serde(default = "default_u32_max")]
    pub native_method_id: u32,
    /// Precomputed parameter slot count (including 'this' for non-static methods).
    #[serde(default)]
    pub param_slots: u8,
}

fn default_u32_max() -> u32 {
    u32::MAX
}

impl RuntimeMethod {
    pub fn is_native(&self) -> bool {
        self.access_flags & ACC_NATIVE != 0
    }
    pub fn is_static(&self) -> bool {
        self.access_flags & ACC_STATIC != 0
    }
    pub fn is_synchronized(&self) -> bool {
        self.access_flags & ACC_SYNCHRONIZED != 0
    }
    pub fn is_abstract(&self) -> bool {
        self.access_flags & ACC_ABSTRACT != 0
    }
    pub fn max_locals(&self) -> u16 {
        self.code.as_ref().map_or(0, |c| c.max_locals)
    }
    pub fn max_stack(&self) -> u16 {
        self.code.as_ref().map_or(0, |c| c.max_stack)
    }
    pub fn bytecode(&self) -> &[u8] {
        self.code.as_ref().map_or(&[], |c| c.code.as_slice())
    }
    pub fn exception_table(&self) -> &[ExceptionTableEntry] {
        self.code
            .as_ref()
            .map_or(&[], |c| c.exception_table.as_slice())
    }
    #[inline(always)]
    pub fn param_slot_count(&self) -> usize {
        self.param_slots as usize
    }

    /// Compute param slot count from descriptor bytes. Called once at class load time.
    pub fn compute_param_slots(descriptor: &str, is_static: bool) -> u8 {
        let mut count: u8 = if is_static { 0 } else { 1 };
        let desc = descriptor.as_bytes();
        let mut i = 1;
        while i < desc.len() {
            match desc[i] {
                b')' => break,
                b'J' | b'D' => {
                    count += 2;
                    i += 1;
                }
                b'L' => {
                    count += 1;
                    while i < desc.len() && desc[i] != b';' {
                        i += 1;
                    }
                    i += 1;
                }
                b'[' => {
                    count += 1;
                    i += 1;
                    while i < desc.len() && desc[i] == b'[' {
                        i += 1;
                    }
                    if i < desc.len() && desc[i] == b'L' {
                        while i < desc.len() && desc[i] != b';' {
                            i += 1;
                        }
                    }
                    i += 1;
                }
                _ => {
                    count += 1;
                    i += 1;
                }
            }
        }
        count
    }
    pub fn return_descriptor_from(descriptor: &str) -> &str {
        if let Some(pos) = descriptor.rfind(')') {
            &descriptor[pos + 1..]
        } else {
            "V"
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeField {
    pub name_id: u32,
    pub descriptor_id: u32,
    pub access_flags: u16,
    pub class_id: u32,
    pub byte_offset: u32,
    pub wide: bool,
    pub reference: bool,
}

impl RuntimeField {
    pub fn is_static(&self) -> bool {
        self.access_flags & ACC_STATIC != 0
    }
    pub fn is_wide(&self) -> bool {
        self.wide
    }
    pub fn byte_size(&self) -> u32 {
        if self.is_wide() {
            8
        } else {
            4
        }
    }
    pub fn is_reference(&self) -> bool {
        self.reference
    }
}

pub fn descriptor_byte_size(desc: &str) -> u32 {
    match desc {
        "J" | "D" => 8,
        _ => 4,
    }
}
