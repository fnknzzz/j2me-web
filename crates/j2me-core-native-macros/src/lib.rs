use proc_macro::TokenStream;
use quote::quote;
use syn::{
    parenthesized, parse::Parse, parse::ParseStream, parse_macro_input, Error, Ident, LitStr,
    Result, Token, Type,
};

#[derive(Clone, Copy)]
enum InvocationKind {
    Static,
    Instance,
}

struct Argument {
    name: Ident,
    ty: Type,
}

struct BindingInput {
    is_async: bool,
    is_yielding: bool,
    kind: InvocationKind,
    key: LitStr,
    handler: Ident,
    args: Vec<Argument>,
    return_ty: Type,
}

impl Parse for BindingInput {
    fn parse(input: ParseStream<'_>) -> Result<Self> {
        let is_async = if input.peek(Token![async]) {
            input.parse::<Token![async]>()?;
            true
        } else {
            false
        };
        let is_yielding = if input.peek(Ident) {
            let lookahead = input.fork();
            let word: Ident = lookahead.parse()?;
            if word == "yielding" {
                input.parse::<Ident>()?;
                true
            } else {
                false
            }
        } else {
            false
        };
        let kind = if input.peek(Token![static]) {
            input.parse::<Token![static]>()?;
            InvocationKind::Static
        } else if input.peek(Ident) && input.peek2(LitStr) {
            let word: Ident = input.parse()?;
            if word != "instance" {
                return Err(Error::new(word.span(), "expected `static` or `instance`"));
            }
            InvocationKind::Instance
        } else {
            return Err(input.error("expected `static` or `instance`"));
        };
        let key: LitStr = input.parse()?;
        input.parse::<Token![=>]>()?;
        let handler: Ident = input.parse()?;
        let content;
        parenthesized!(content in input);
        let args = content
            .parse_terminated(
                |input: ParseStream<'_>| {
                    Ok(Argument {
                        name: input.parse()?,
                        ty: {
                            input.parse::<Token![:]>()?;
                            input.parse()?
                        },
                    })
                },
                Token![,],
            )?
            .into_iter()
            .collect();
        input.parse::<Token![->]>()?;
        Ok(Self {
            is_async,
            is_yielding,
            kind,
            key,
            handler,
            args,
            return_ty: input.parse()?,
        })
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum JavaType {
    Bool,
    Int,
    Long,
    Float,
    Double,
    Ref,
    Void,
}

fn parse_type(bytes: &[u8], cursor: &mut usize, allow_void: bool) -> Result<JavaType> {
    let Some(&tag) = bytes.get(*cursor) else {
        return Err(Error::new(
            proc_macro2::Span::call_site(),
            "truncated JVM descriptor",
        ));
    };
    *cursor += 1;
    match tag {
        b'Z' => Ok(JavaType::Bool),
        b'B' | b'C' | b'S' | b'I' => Ok(JavaType::Int),
        b'J' => Ok(JavaType::Long),
        b'F' => Ok(JavaType::Float),
        b'D' => Ok(JavaType::Double),
        b'V' if allow_void => Ok(JavaType::Void),
        b'L' => {
            while let Some(&ch) = bytes.get(*cursor) {
                *cursor += 1;
                if ch == b';' {
                    return Ok(JavaType::Ref);
                }
            }
            Err(Error::new(
                proc_macro2::Span::call_site(),
                "unterminated object descriptor",
            ))
        }
        b'[' => {
            while matches!(bytes.get(*cursor), Some(b'[')) {
                *cursor += 1;
            }
            parse_type(bytes, cursor, false).map(|_| JavaType::Ref)
        }
        _ => Err(Error::new(
            proc_macro2::Span::call_site(),
            "invalid JVM descriptor type",
        )),
    }
}

fn parse_descriptor(key: &LitStr) -> Result<(Vec<JavaType>, JavaType)> {
    let key_value = key.value();
    let desc_start = key_value
        .find('(')
        .ok_or_else(|| Error::new(key.span(), "native key must include a JVM descriptor"))?;
    let bytes = key_value.as_bytes();
    let mut cursor = desc_start + 1;
    let mut args = Vec::new();
    while bytes.get(cursor) != Some(&b')') {
        args.push(parse_type(bytes, &mut cursor, false)?);
    }
    cursor += 1;
    let result = parse_type(bytes, &mut cursor, true)?;
    if cursor != bytes.len() {
        return Err(Error::new(key.span(), "trailing data after JVM descriptor"));
    }
    Ok((args, result))
}

fn rust_type_name(ty: &Type) -> Option<String> {
    match ty {
        Type::Tuple(tuple) if tuple.elems.is_empty() => Some("()".into()),
        Type::Path(path) if path.qself.is_none() && path.path.segments.len() == 1 => {
            Some(path.path.segments[0].ident.to_string())
        }
        _ => None,
    }
}

fn expected_rust_type(java_type: JavaType) -> &'static str {
    match java_type {
        JavaType::Bool => "bool",
        JavaType::Int => "i32",
        JavaType::Long => "i64",
        JavaType::Float => "f32",
        JavaType::Double => "f64",
        JavaType::Ref => "u32",
        JavaType::Void => "()",
    }
}

fn slot_count(java_type: JavaType) -> usize {
    match java_type {
        JavaType::Long | JavaType::Double => 2,
        JavaType::Void => 0,
        _ => 1,
    }
}

fn ensure_type(ty: &Type, java_type: JavaType, label: &str) -> Result<()> {
    let actual = rust_type_name(ty).unwrap_or_default();
    let expected = expected_rust_type(java_type);
    if actual != expected {
        return Err(Error::new_spanned(
            ty,
            format!("{label} must use Rust type `{expected}` for this JVM descriptor"),
        ));
    }
    Ok(())
}

#[proc_macro]
pub fn native_bind(input: TokenStream) -> TokenStream {
    match expand_native_bind(parse_macro_input!(input as BindingInput)) {
        Ok(output) => output.into(),
        Err(error) => error.into_compile_error().into(),
    }
}

fn expand_native_bind(input: BindingInput) -> Result<proc_macro2::TokenStream> {
    let (descriptor_args, descriptor_return) = parse_descriptor(&input.key)?;
    let receiver = matches!(input.kind, InvocationKind::Instance);
    let expected_args = descriptor_args.len() + usize::from(receiver);
    if input.args.len() != expected_args {
        return Err(Error::new_spanned(
            input.handler,
            format!("binding expects {expected_args} Rust arguments including receiver"),
        ));
    }
    let mut arg_offset = 0;
    if receiver {
        ensure_type(&input.args[0].ty, JavaType::Ref, "instance receiver")?;
        arg_offset = 1;
    }
    for (arg, java_type) in input.args[arg_offset..].iter().zip(&descriptor_args) {
        ensure_type(&arg.ty, *java_type, "native argument")?;
    }
    ensure_type(&input.return_ty, descriptor_return, "native return value")?;

    let mut decode = Vec::new();
    let mut peek_depth = 0usize;
    for (arg, java_type) in input.args[arg_offset..].iter().zip(&descriptor_args).rev() {
        let name = &arg.name;
        let depth = peek_depth;
        peek_depth += slot_count(*java_type);
        decode.push(if input.is_async { match java_type {
            JavaType::Bool => quote! { let #name = stack.peek_at(#depth) != 0; },
            JavaType::Int => quote! { let #name = stack.peek_at(#depth) as i32; },
            JavaType::Long => quote! {
                let __hi = stack.peek_at(#depth);
                let __lo = stack.peek_at(#depth + 1);
                let #name = ((__hi as i64) << 32) | (__lo as u64 as i64);
            },
            JavaType::Float => quote! { let #name = f32::from_bits(stack.peek_at(#depth)); },
            JavaType::Double => quote! {
                let __hi = stack.peek_at(#depth);
                let __lo = stack.peek_at(#depth + 1);
                let #name = f64::from_bits(((__hi as u64) << 32) | __lo as u64);
            },
            JavaType::Ref => quote! { let #name = stack.peek_at(#depth); },
            JavaType::Void => unreachable!(),
        }} else { match java_type {
            JavaType::Bool => quote! { let #name = stack.pop() != 0; },
            JavaType::Int => quote! { let #name = stack.pop() as i32; },
            JavaType::Long => quote! { let __hi = stack.pop(); let __lo = stack.pop(); let #name = ((__hi as i64) << 32) | (__lo as u64 as i64); },
            JavaType::Float => quote! { let #name = f32::from_bits(stack.pop()); },
            JavaType::Double => quote! { let __hi = stack.pop(); let __lo = stack.pop(); let #name = f64::from_bits(((__hi as u64) << 32) | __lo as u64); },
            JavaType::Ref => quote! { let #name = stack.pop(); },
            JavaType::Void => unreachable!(),
        }});
    }
    let receiver_decode = if receiver {
        let name = &input.args[0].name;
        if input.is_async {
            quote! { let #name = stack.peek_at(#peek_depth); }
        } else {
            quote! { let #name = stack.pop(); }
        }
    } else {
        quote! {}
    };
    let call_args = input.args.iter().map(|arg| &arg.name);
    let handler = input.handler;
    let encode = match descriptor_return {
        JavaType::Void => quote! {},
        JavaType::Bool => quote! { stack.push(__result as u32); },
        JavaType::Int => quote! { stack.push(__result as u32); },
        JavaType::Long => quote! {
            stack.push(__result as u32);
            stack.push((__result >> 32) as u32);
        },
        JavaType::Float => quote! { stack.push(__result.to_bits()); },
        JavaType::Double => quote! {
            let __bits = __result.to_bits();
            stack.push(__bits as u32);
            stack.push((__bits >> 32) as u32);
        },
        JavaType::Ref => quote! { stack.push(__result); },
    };
    let parameter_slots = descriptor_args
        .iter()
        .copied()
        .map(slot_count)
        .sum::<usize>()
        + usize::from(receiver);
    let return_slots = slot_count(descriptor_return);
    let kind = match input.kind {
        InvocationKind::Static => quote! { Static },
        InvocationKind::Instance => quote! { Instance },
    };
    let key = input.key;
    let async_call = if input.is_async {
        quote! {
            match #handler(&mut __ctx, #(#call_args),*) {
                crate::interpreter::native::registry::NativeResult::Ready(__result) => {
                    for _ in 0..#parameter_slots { stack.pop(); }
                    #encode
                    debug_assert_eq!(stack.sp, __before_sp - #parameter_slots + #return_slots, "typed native ABI imbalance");
                    None
                }
                crate::interpreter::native::registry::NativeResult::Yield(__yield_reason) => Some(__yield_reason),
            }
        }
    } else if input.is_yielding {
        quote! {
            match #handler(&mut __ctx, #(#call_args),*) {
                crate::interpreter::native::registry::NativeResult::Ready(__result) => {
                    #encode
                    debug_assert_eq!(stack.sp, __before_sp - #parameter_slots + #return_slots, "typed native ABI imbalance");
                    None
                }
                crate::interpreter::native::registry::NativeResult::Yield(__yield_reason) => Some(__yield_reason),
            }
        }
    } else {
        quote! {
            let __result = #handler(&mut __ctx, #(#call_args),*);
            #encode
            debug_assert_eq!(stack.sp, __before_sp - #parameter_slots + #return_slots, "typed native ABI imbalance");
            None
        }
    };
    Ok(quote! {{
        fn __native_wrapper(
            stack: &mut crate::scheduler::ThreadStack,
            vm: &mut crate::vm::SharedVm,
            thread_id: u32,
        ) -> Option<crate::scheduler::virtual_thread::YieldReason> {
            let __before_sp = stack.sp;
            #(#decode)*
            #receiver_decode
            let mut __ctx = crate::interpreter::native::registry::NativeContext { vm, thread_id, stack: &*stack };
            #async_call
        }
        crate::interpreter::native::registry::NativeBinding {
            key: #key,
            invocation_kind: crate::interpreter::native::registry::InvocationKind::#kind,
            handler: __native_wrapper,
        }
    }})
}
