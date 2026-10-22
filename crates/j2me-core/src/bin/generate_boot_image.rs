use j2me_core::vm::SharedVm;
use std::cell::RefCell;
use std::rc::Rc;

fn main() -> Result<(), String> {
    let mut args = std::env::args().skip(1);
    let jar_path = args.next().ok_or("missing source JAR path")?;
    let output_path = args.next().ok_or("missing boot image output path")?;
    if args.next().is_some() {
        return Err("expected: generate_boot_image <classes.jar> <output.boot>".into());
    }

    let jar = std::fs::read(jar_path).map_err(|error| error.to_string())?;
    let mut vm = SharedVm::new(Rc::new(RefCell::new(1)), false);
    vm.load_jar(&jar)?;
    let image = vm.encode_compact_boot_image(&jar)?;
    std::fs::write(output_path, image).map_err(|error| error.to_string())
}
