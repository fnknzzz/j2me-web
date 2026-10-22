use super::types::*;
use byteorder::{BigEndian, ReadBytesExt};
use cesu8::from_java_cesu8;
use std::io::Cursor;

/// Result of parsing: ClassFile + temporary string storage.
/// CpEntry::Utf8 { string_id } entries reference indices into raw_strings.
/// Call `ClassRepository::intern_cp_from_parsed` to convert to final string_ids.
pub struct ParsedClassFile {
    pub class_file: ClassFile,
    /// Temporary string storage for CP Utf8 entries.
    /// string_id in CpEntry::Utf8 is an index into this Vec.
    pub raw_strings: Vec<String>,
}

pub fn parse_class(data: &[u8]) -> Result<ParsedClassFile, String> {
    let mut cursor = Cursor::new(data);

    let magic = read_u32(&mut cursor)?;
    if magic != 0xCAFEBABE {
        return Err(format!("invalid magic number: 0x{:08X}", magic));
    }

    let minor_version = read_u16(&mut cursor)?;
    let major_version = read_u16(&mut cursor)?;

    let (constant_pool, raw_strings) = parse_constant_pool(&mut cursor)?;

    let access_flags = read_u16(&mut cursor)?;
    let this_class = read_u16(&mut cursor)?;
    let super_class = read_u16(&mut cursor)?;

    let interfaces = parse_interfaces(&mut cursor)?;
    let fields = parse_fields(&mut cursor, &constant_pool)?;
    let methods = parse_methods(&mut cursor, &constant_pool, &raw_strings)?;
    let attributes = parse_attributes(&mut cursor)?;

    Ok(ParsedClassFile {
        class_file: ClassFile {
            minor_version,
            major_version,
            constant_pool,
            access_flags,
            this_class,
            super_class,
            interfaces,
            fields,
            methods,
            attributes,
        },
        raw_strings,
    })
}

fn read_u8(cursor: &mut Cursor<&[u8]>) -> Result<u8, String> {
    cursor
        .read_u8()
        .map_err(|e| format!("failed to read u8: {}", e))
}

fn read_u16(cursor: &mut Cursor<&[u8]>) -> Result<u16, String> {
    cursor
        .read_u16::<BigEndian>()
        .map_err(|e| format!("failed to read u16: {}", e))
}

fn read_u32(cursor: &mut Cursor<&[u8]>) -> Result<u32, String> {
    cursor
        .read_u32::<BigEndian>()
        .map_err(|e| format!("failed to read u32: {}", e))
}

fn read_i32(cursor: &mut Cursor<&[u8]>) -> Result<i32, String> {
    cursor
        .read_i32::<BigEndian>()
        .map_err(|e| format!("failed to read i32: {}", e))
}

fn read_i64(cursor: &mut Cursor<&[u8]>) -> Result<i64, String> {
    cursor
        .read_i64::<BigEndian>()
        .map_err(|e| format!("failed to read i64: {}", e))
}

fn read_f32(cursor: &mut Cursor<&[u8]>) -> Result<f32, String> {
    cursor
        .read_f32::<BigEndian>()
        .map_err(|e| format!("failed to read f32: {}", e))
}

fn read_f64(cursor: &mut Cursor<&[u8]>) -> Result<f64, String> {
    cursor
        .read_f64::<BigEndian>()
        .map_err(|e| format!("failed to read f64: {}", e))
}

fn read_bytes(cursor: &mut Cursor<&[u8]>, len: usize) -> Result<Vec<u8>, String> {
    let mut buf = vec![0u8; len];
    std::io::Read::read_exact(cursor, &mut buf)
        .map_err(|e| format!("failed to read {} bytes: {}", len, e))?;
    Ok(buf)
}

fn parse_constant_pool(cursor: &mut Cursor<&[u8]>) -> Result<(Vec<CpEntry>, Vec<String>), String> {
    let cp_count = read_u16(cursor)? as usize;
    let mut pool = Vec::with_capacity(cp_count);
    let mut raw_strings: Vec<String> = Vec::new();
    pool.push(CpEntry::Empty);

    let mut i = 1;
    while i < cp_count {
        let tag = read_u8(cursor)?;
        match tag {
            1 => {
                let length = read_u16(cursor)? as usize;
                let bytes = read_bytes(cursor, length)?;
                let s = from_java_cesu8(&bytes)
                    .map_err(|e| format!("invalid CESU-8 in constant pool entry {}: {}", i, e))?
                    .into_owned();
                let raw_idx = raw_strings.len() as u32;
                raw_strings.push(s);
                pool.push(CpEntry::Utf8 { string_id: raw_idx });
            }
            3 => {
                let value = read_i32(cursor)?;
                pool.push(CpEntry::Integer(value));
            }
            4 => {
                let value = read_f32(cursor)?;
                pool.push(CpEntry::Float(value));
            }
            5 => {
                let value = read_i64(cursor)?;
                pool.push(CpEntry::Long(value));
                i += 1;
                pool.push(CpEntry::Empty);
            }
            6 => {
                let value = read_f64(cursor)?;
                pool.push(CpEntry::Double(value));
                i += 1;
                pool.push(CpEntry::Empty);
            }
            7 => {
                let name_index = read_u16(cursor)?;
                pool.push(CpEntry::Class { name_index });
            }
            8 => {
                let string_index = read_u16(cursor)?;
                pool.push(CpEntry::String { string_index });
            }
            9 => {
                let class_index = read_u16(cursor)?;
                let name_and_type_index = read_u16(cursor)?;
                pool.push(CpEntry::Fieldref {
                    class_index,
                    name_and_type_index,
                });
            }
            10 => {
                let class_index = read_u16(cursor)?;
                let name_and_type_index = read_u16(cursor)?;
                pool.push(CpEntry::Methodref {
                    class_index,
                    name_and_type_index,
                });
            }
            11 => {
                let class_index = read_u16(cursor)?;
                let name_and_type_index = read_u16(cursor)?;
                pool.push(CpEntry::InterfaceMethodref {
                    class_index,
                    name_and_type_index,
                });
            }
            12 => {
                let name_index = read_u16(cursor)?;
                let descriptor_index = read_u16(cursor)?;
                pool.push(CpEntry::NameAndType {
                    name_index,
                    descriptor_index,
                });
            }
            15 => {
                read_u8(cursor)?;
                read_u16(cursor)?;
                pool.push(CpEntry::Empty);
            }
            16 => {
                read_u16(cursor)?;
                pool.push(CpEntry::Empty);
            }
            18 => {
                read_u16(cursor)?;
                read_u16(cursor)?;
                pool.push(CpEntry::Empty);
            }
            _ => {
                return Err(format!(
                    "unsupported constant pool tag {} at index {}",
                    tag, i
                ))
            }
        }
        i += 1;
    }

    Ok((pool, raw_strings))
}

fn parse_interfaces(cursor: &mut Cursor<&[u8]>) -> Result<Vec<u16>, String> {
    let count = read_u16(cursor)? as usize;
    let mut interfaces = Vec::with_capacity(count);
    for _ in 0..count {
        interfaces.push(read_u16(cursor)?);
    }
    Ok(interfaces)
}

fn parse_fields(
    cursor: &mut Cursor<&[u8]>,
    _constant_pool: &[CpEntry],
) -> Result<Vec<FieldInfo>, String> {
    let count = read_u16(cursor)? as usize;
    let mut fields = Vec::with_capacity(count);
    for _ in 0..count {
        let access_flags = read_u16(cursor)?;
        let name_index = read_u16(cursor)?;
        let descriptor_index = read_u16(cursor)?;
        let attributes = parse_attributes(cursor)?;
        fields.push(FieldInfo {
            access_flags,
            name_index,
            descriptor_index,
            attributes,
        });
    }
    Ok(fields)
}

fn parse_methods(
    cursor: &mut Cursor<&[u8]>,
    constant_pool: &[CpEntry],
    raw_strings: &[String],
) -> Result<Vec<MethodInfo>, String> {
    let count = read_u16(cursor)? as usize;
    let mut methods = Vec::with_capacity(count);
    for _ in 0..count {
        let access_flags = read_u16(cursor)?;
        let name_index = read_u16(cursor)?;
        let descriptor_index = read_u16(cursor)?;
        let attrs = parse_attributes(cursor)?;

        let mut code = None;
        for attr in &attrs {
            let attr_name = get_utf8(constant_pool, raw_strings, attr.attribute_name_index);
            if attr_name == Some("Code") {
                code = Some(parse_code_attribute(&attr.info)?);
                break;
            }
        }

        methods.push(MethodInfo {
            access_flags,
            name_index,
            descriptor_index,
            attributes: attrs,
            code,
        });
    }
    Ok(methods)
}

fn parse_attributes(cursor: &mut Cursor<&[u8]>) -> Result<Vec<AttributeInfo>, String> {
    let count = read_u16(cursor)? as usize;
    let mut attributes = Vec::with_capacity(count);
    for _ in 0..count {
        let attribute_name_index = read_u16(cursor)?;
        let attribute_length = read_u32(cursor)? as usize;
        let info = read_bytes(cursor, attribute_length)?;
        attributes.push(AttributeInfo {
            attribute_name_index,
            info,
        });
    }
    Ok(attributes)
}

fn parse_code_attribute(data: &[u8]) -> Result<CodeAttribute, String> {
    let mut cursor = Cursor::new(data);
    let max_stack = read_u16(&mut cursor)?;
    let max_locals = read_u16(&mut cursor)?;
    let code_length = read_u32(&mut cursor)? as usize;
    let code = read_bytes(&mut cursor, code_length)?;

    let exception_table_length = read_u16(&mut cursor)? as usize;
    let mut exception_table = Vec::with_capacity(exception_table_length);
    for _ in 0..exception_table_length {
        let start_pc = read_u16(&mut cursor)?;
        let end_pc = read_u16(&mut cursor)?;
        let handler_pc = read_u16(&mut cursor)?;
        let catch_type = read_u16(&mut cursor)?;
        exception_table.push(ExceptionTableEntry {
            start_pc,
            end_pc,
            handler_pc,
            catch_type,
        });
    }

    let attributes = parse_sub_attributes(&mut cursor)?;

    Ok(CodeAttribute {
        max_stack,
        max_locals,
        code,
        exception_table,
        attributes,
    })
}

fn parse_sub_attributes(cursor: &mut Cursor<&[u8]>) -> Result<Vec<AttributeInfo>, String> {
    let count = read_u16(cursor)? as usize;
    let mut attributes = Vec::with_capacity(count);
    for _ in 0..count {
        let attribute_name_index = read_u16(cursor)?;
        let attribute_length = read_u32(cursor)? as usize;
        let info = read_bytes(cursor, attribute_length)?;
        attributes.push(AttributeInfo {
            attribute_name_index,
            info,
        });
    }
    Ok(attributes)
}

fn get_utf8<'a>(
    constant_pool: &[CpEntry],
    raw_strings: &'a [String],
    index: u16,
) -> Option<&'a str> {
    match constant_pool.get(index as usize) {
        Some(CpEntry::Utf8 { string_id }) => {
            raw_strings.get(*string_id as usize).map(|s| s.as_str())
        }
        _ => None,
    }
}
