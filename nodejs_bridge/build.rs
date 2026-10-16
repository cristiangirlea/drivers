const LINKER_MAP_ENV: &str = "NODEJS_BRIDGE_LINKER_MAP";

fn main() {
    napi_build::setup();
    emit_linker_map_arg();
}

// Windows CI only: asks the MSVC linker to write a map file for this DLL.
//
// The `fips_artifacts` job in `.github/workflows/test-rust-core.yml` sets
// `NODEJS_BRIDGE_LINKER_MAP`. `ci/fips/check_linked_crypto.py` reads the map
// to check that no non-FIPS crypto code is statically linked into the DLL.
// A Windows DLL does not list these symbols itself, so the map is needed.
//
// Why this lives in the build script:
// - `/MAP` must reach only the final link of this crate. In `RUSTFLAGS` it
//   would reach every crate and rebuild all of them.
// - napi always runs `cargo build`, which cannot pass arguments to one crate.
//   `cargo:rustc-cdylib-link-arg` passes the flag to this crate's link only.
//
// CI uses a new map path on every run. A changed value reruns this script,
// so Cargo builds and links this crate again and writes a fresh map, even
// when all dependencies come from the cache.
//
// Without the variable, the build does not change.
fn emit_linker_map_arg() {
    println!("cargo:rerun-if-env-changed={LINKER_MAP_ENV}");
    let Some(map_path) = std::env::var_os(LINKER_MAP_ENV) else {
        return;
    };
    if std::env::var("CARGO_CFG_TARGET_ENV").as_deref() != Ok("msvc") {
        panic!("{LINKER_MAP_ENV} is only supported for MSVC targets");
    }
    println!(
        "cargo:rustc-cdylib-link-arg=/MAP:{}",
        map_path.to_string_lossy()
    );
}
