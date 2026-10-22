use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::UNIX_EPOCH;

fn emit_rerun_if_changed_for_directory(directory: &Path) {
    for entry in fs::read_dir(directory).expect("Failed to read source directory") {
        let path = entry.expect("Failed to read source directory entry").path();
        if path.is_dir() {
            emit_rerun_if_changed_for_directory(&path);
        } else {
            println!("cargo:rerun-if-changed={}", path.display());
        }
    }
}

fn append_file_fingerprint(fingerprint: &mut String, path: &Path) {
    let metadata = fs::metadata(path).expect("Failed to read Java build input metadata");
    let modified = metadata
        .modified()
        .expect("Failed to read Java build input modification time")
        .duration_since(UNIX_EPOCH)
        .expect("Java build input modification time predates UNIX epoch");
    fingerprint.push_str(&format!(
        "{}:{}:{}:{}\n",
        path.display(),
        metadata.len(),
        modified.as_secs(),
        modified.subsec_nanos()
    ));
}

fn test_java_fingerprint(java_files: &[PathBuf]) -> String {
    let mut fingerprint = String::new();
    fingerprint.push_str("javac -source 1.4 -target 1.4\n");
    for java_file in java_files {
        append_file_fingerprint(&mut fingerprint, java_file);
    }
    fingerprint
}

fn build_m3gcore(manifest_dir: &str, out_dir: &Path) {
    let m3g_dir = Path::new(manifest_dir).join("src/m3gcore");
    let inc_dir = m3g_dir.join("inc");
    let src_dir = m3g_dir.join("src");
    let wire_generator = Path::new(manifest_dir).join("scripts/generate-m3g-wire.mjs");
    let wire_schema = Path::new(manifest_dir).join("src/gles1_webgpu/schema/m3g_webgpu_wire.json");
    let target = env::var("TARGET").unwrap_or_default();

    emit_rerun_if_changed_for_directory(&m3g_dir);
    for file in [
        src_dir.join("m3g_core.c"),
        src_dir.join("m3g_gles1_webgpu_shim.c"),
        src_dir.join("m3g_wasm_platform.c"),
        src_dir.join("m3g_wasm_skinning_float.c"),
        src_dir.join("m3g_rendercontext.c"),
        src_dir.join("m3g_texture.c"),
        src_dir.join("m3g_sprite.c"),
        src_dir.join("m3g_background.c"),
        src_dir.join("m3g_image.inl"),
        inc_dir.join("m3g_wasm_prelude.h"),
    ] {
        println!("cargo:rerun-if-changed={}", file.display());
    }
    println!("cargo:rerun-if-changed={}", wire_generator.display());
    println!("cargo:rerun-if-changed={}", wire_schema.display());
    let generated_wire_header = out_dir.join("m3g_webgpu_wire.h");
    let wire_generate = Command::new("node")
        .arg(&wire_generator)
        .arg("--c-output")
        .arg(&generated_wire_header)
        .current_dir(manifest_dir)
        .output()
        .expect("Failed to run M3G wire schema generator");
    if !wire_generate.status.success() {
        panic!(
            "Failed to generate M3G wire header.\n{}",
            String::from_utf8_lossy(&wire_generate.stderr)
        );
    }

    let mut build = cc::Build::new();
    if target.contains("wasm") {
        let llvm_clang = Path::new("/opt/homebrew/opt/llvm/bin/clang");
        if llvm_clang.exists() {
            build.compiler(llvm_clang);
        }
        build.define("M3G_SOFT_FLOAT", None);
    }

    build
        .file(src_dir.join("m3g_core.c"))
        .file(src_dir.join("m3g_gles1_webgpu_shim.c"))
        .file(src_dir.join("m3g_wasm_platform.c"))
        .include(out_dir)
        .include(&inc_dir)
        .include(inc_dir.join("M3G"))
        .include(&src_dir)
        .define("M3G_TARGET_GENERIC", None)
        .define("M3G_BUILD_GCC", None)
        .define("M3G_NO_COMPILER_DETECTION", None)
        .flag("-include")
        .flag(inc_dir.join("m3g_wasm_prelude.h").to_str().unwrap())
        .flag("-Wno-implicit-function-declaration")
        .flag("-Wno-int-conversion")
        .flag("-Wno-incompatible-pointer-types")
        .flag("-Wno-incompatible-function-pointer-types")
        .flag("-Wno-incompatible-library-redeclaration")
        .warnings(false)
        .flag("-std=gnu89");

    // This is a Clang-only diagnostic option. The boot image generator builds
    // the same bridge for the host target, where GitHub Actions defaults to
    // GCC on Linux.
    if build.get_compiler().is_like_clang() {
        build.flag("-ferror-limit=0");
    }

    build.compile("m3gcore");
}

fn build_boot_image(manifest_dir: &str, out_dir: &Path, runtime_jar: &Path) {
    let cargo = env::var_os("CARGO").unwrap_or_else(|| "cargo".into());
    let output_path = out_dir.join("classes.boot");
    let generator_target = out_dir.join("boot-generator-target");
    let output = Command::new(cargo)
        .arg("run")
        .arg("--quiet")
        .arg("--features")
        .arg("boot-generator")
        .arg("--bin")
        .arg("generate_boot_image")
        .arg("--target-dir")
        .arg(generator_target)
        .arg("--")
        .arg(runtime_jar)
        .arg(&output_path)
        // The outer WASM build may set target-specific linker flags through
        // RUSTFLAGS. The boot-image generator is a native host executable,
        // so it must not inherit wasm-ld flags such as --allow-undefined.
        .env_remove("RUSTFLAGS")
        .env_remove("CARGO_ENCODED_RUSTFLAGS")
        .env("JSJVM_BOOT_GENERATOR_CHILD", "1")
        .current_dir(manifest_dir)
        .output()
        .expect("Failed to run compact boot image generator");
    if !output.status.success() {
        panic!(
            "compact boot image generation failed:\n{}\n{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
}

fn main() {
    let manifest_dir = env::var("CARGO_MANIFEST_DIR").unwrap();
    let out_dir = env::var("OUT_DIR").unwrap();
    let out_dir_path = Path::new(&out_dir);
    if env::var_os("JSJVM_BOOT_GENERATOR_CHILD").is_some() {
        // The generator links the full crate, including the M3G native bridge.
        // Build only that native dependency here, while skipping the runtime JAR,
        // boot image recursion, and Java test classes below.
        build_m3gcore(&manifest_dir, out_dir_path);
        return;
    }

    build_m3gcore(&manifest_dir, out_dir_path);

    // Build j2me-rt JAR based on build profile
    let j2me_rt_dir = Path::new(&manifest_dir).join("j2me-rt");
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("Makefile").display()
    );
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("cldc1.1.1").display()
    );
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("midp").display()
    );
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("custom").display()
    );
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("vm").display()
    );
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("jsr-184").display()
    );
    println!(
        "cargo:rerun-if-changed={}",
        j2me_rt_dir.join("l10n").display()
    );

    let is_release = env::var("PROFILE").unwrap_or_default() == "release";
    let mode = if is_release { "prod" } else { "debug" };

    let build_rt = Command::new("make")
        .arg("-C")
        .arg(&j2me_rt_dir)
        .arg(format!("MODE={}", mode))
        .arg("classes.jar")
        .output()
        .expect("Failed to run make for j2me-rt. Is make installed?");
    if !build_rt.status.success() {
        panic!(
            "j2me-rt {} build failed:\n{}",
            mode,
            String::from_utf8_lossy(&build_rt.stderr)
        );
    }

    let runtime_jar = j2me_rt_dir.join(format!("classes.{mode}.jar"));
    if env::var("TARGET").is_ok_and(|target| target.starts_with("wasm32")) {
        emit_rerun_if_changed_for_directory(&Path::new(&manifest_dir).join("src"));
        build_boot_image(&manifest_dir, out_dir_path, &runtime_jar);
    }

    // Build test Java classes
    let java_src_dir = Path::new(&manifest_dir).join("tests/java");
    let java_out_dir = Path::new(&out_dir).join("test_classes");
    let java_stamp_path = Path::new(&out_dir).join("test_classes.stamp");

    fs::create_dir_all(&java_out_dir).unwrap();

    // Rerun when files are added/removed from the java source directory
    println!("cargo:rerun-if-changed={}", java_src_dir.display());

    // Collect .java files that don't already have a precompiled .class alongside them.
    // Files like AsyncInitMidlet.java require a J2ME classpath to compile and ship
    // a committed .class instead; build.rs must not attempt to recompile them.
    let mut java_files: Vec<_> = fs::read_dir(&java_src_dir)
        .unwrap()
        .filter_map(|e| e.ok())
        .filter(|e| {
            let path = e.path();
            if path.extension().map_or(true, |ext| ext != "java") {
                return false;
            }
            // Skip if a .class with the same stem already exists next to the .java.
            let class_path = path.with_extension("class");
            !class_path.exists()
        })
        .map(|e| e.path())
        .collect();

    if java_files.is_empty() {
        return;
    }

    java_files.sort();

    // Rerun if any .java file changes
    for f in &java_files {
        println!("cargo:rerun-if-changed={}", f.display());
    }

    let fingerprint = test_java_fingerprint(&java_files);
    if fs::read_to_string(&java_stamp_path)
        .map(|stamp| stamp == fingerprint)
        .unwrap_or(false)
    {
        return;
    }

    // Compile all .java files at once
    let mut cmd = Command::new("javac");
    cmd.arg("-source")
        .arg("1.4")
        .arg("-target")
        .arg("1.4")
        .arg("-classpath")
        .arg(&runtime_jar)
        .arg("-d")
        .arg(&java_out_dir);
    for f in &java_files {
        cmd.arg(f);
    }

    let output = cmd
        .output()
        .expect("Failed to run javac. Is Java installed?");
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        let stdout = String::from_utf8_lossy(&output.stdout);
        panic!("javac failed:\n{}\n{}", stderr, stdout);
    }
    fs::write(&java_stamp_path, fingerprint).expect("Failed to write Java build cache stamp");
}
