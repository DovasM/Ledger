use uniffi_ledger::{open_database, LedgerDb};
use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

static COUNTER: AtomicU32 = AtomicU32::new(0);

/// A real SQLite file, migrated from empty exactly the way a new install is.
///
/// A file rather than `:memory:` on purpose — the pool opens several connections and an in-memory
/// database is per-connection, so half the migrations would land in a database the next query
/// cannot see. Using the same `open_database` the app calls also means these tests exercise the
/// migration chain, which is where a schema mistake would actually surface.
pub struct TestDb {
    pub db: Arc<LedgerDb>,
    path: PathBuf,
}

impl TestDb {
    pub fn new() -> Self {
        let n = COUNTER.fetch_add(1, Ordering::SeqCst);
        let path = std::env::temp_dir().join(format!("ledger_test_{}_{}.db", std::process::id(), n));
        let _ = std::fs::remove_file(&path);
        let db = open_database(path.to_string_lossy().to_string());
        TestDb { db, path }
    }

    /// A wallet plus the expense category, which is all most tests need.
    pub fn with_wallet(&self) -> String {
        self.db
            .create_wallet("Checking".into(), String::new(), "EUR".into(), 0.0, false)
            .expect("create wallet")
            .id
    }
}

impl Drop for TestDb {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.path);
        let _ = std::fs::remove_file(self.path.with_extension("db-wal"));
        let _ = std::fs::remove_file(self.path.with_extension("db-shm"));
    }
}

/// Midnight UTC on a given day — the exact shape `AddTransactionScreen` sends, which is what makes
/// same-day transactions tie in the first place.
pub fn day(iso_date: &str) -> Option<String> {
    Some(format!("{iso_date}T00:00:00Z"))
}
