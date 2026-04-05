fn main() {
    uniffi::generate_scaffolding("src/ledger.udl").unwrap();
}
