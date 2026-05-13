SPRINT 1 — Core Performance (Red cluster DB) 1.1.1 (DONE)
├── Cache virtual key balance in-memory
├── Batch virtual key operations (mass open)
└── Async reward delivery

SPRINT 2 — Animation & Sound (Red cluster hardcoded) 1.2.0 (DONE)
├── Speed Animation (Gui & Particle dipisah)
├── Custom sound opening per crate (tick & win sound)
└── Auto-key ID sync on new crate creation

SPRINT 3 — Safety & Limits (Red) 1.3.0
├── Rate limit crate open per detik
├── Check & Update create loc and del loc to be more easier
└── Crate limit per player lifetime

SPRINT 4 — Public API + More Placeholder 1.4.0
├── QuantumCratesAPI class
├── Custom Bukkit events (CrateOpenEvent, CrateRewardEvent, PityTriggerEvent, KeyGivenEvent, and more (related to our plugin))
├── More Placeholder for User
└── API Javadoc

SPRINT 5 — Integrations (Red) 1.5.0
├── Vault economy integration
└── mcMMO XP reward type

SPRINT 6 — Dashboard Features (Red + Yellow) 1.6.0
├── Metrics dashboard (TPS impact, AnimationManager memory)
└── Crate changelog dengan Opsi A (magic link identity)

SPRINT 7 — Yellow cluster A 1.7.0
├── Migration tool ExcellentCrates
├── Migration tool CrazyCrates
└── Export / Import ZIP

SPRINT 8 — Yellow cluster B 1.8.0
├── Crate bundles
└── Reward preview filter by rarity

SPRINT 9 — Particle A B 1.9.0
├── Support All Particles from Bukkit Particle for 1.21+ and 1.20+
└── More Gui Animation from another plugin like Phoenix Crates, Excellent Crates, etc

SPRINT 10 — Yellow cluster C (Scale) 2.0.0
├── Multi Server Dashboard Support (connect hostname, port and etc from Bungeecord/Proxy/Velocity)
└── Redis support

Red:

    Cache virtual key balance di memory supaya tidak query DB setiap cek (saat ini getVirtualBalance blocking main thread dengan get(1, SECONDS))
    /qc delloc interactive — kalau crate punya multiple location, tampilkan list dulu baru pilih index
    Animation speed configurable per crate — sekarang hardcoded di SPIN_STEPS
    Custom opening sound per crate — sekarang hardcoded
    Crate limit — max berapa kali player bisa buka crate seumur hidup
    Integration : Vault, McMMO
    Async reward delivery — sekarang deliver reward di main thread, bisa dioptimasi
    Batch virtual key operations — kalau mass open 64x, sekarang 64x query DB, harusnya 1x
    Metrics dashboard — grafik TPS impact saat mass open, memory usage AnimationManager

Yellow:

    Migration tool dari ExcellentCrates dan CrazyCrates
    Crate bundles — satu key buka multiple crate sekaligus / bisa dibilang 1 key for multiple crates gitu kan
    Reward preview per rarity — di preview GUI bisa filter by rarity
    Firework Animation on open — bukan reward type tapi type animasi
    Export / Import — backup semua crate config as ZIP, restore dari ZIP
    Redis support — key balance dan pity data di Redis untuk multi-instance server (BungeeCord/Velocity setup)

    Rate limit crate open per detik
    Crate changelog di dashboard ( Butuh Input nama ketika buka web? tapi bisa di abuse pake nama random/nama orang lain )
    API untuk developer lain (apa saja kira kira?)



CrateListener.getCrateAtBlock() — setiap block click, dia iterate semua crates dan semua locations pake stream. Dengan 50+ crates ini O(n*m) per click.
PlayerDataManager.mutateData() — kalau player tidak ada di cache, dia load dari DB tapi tidak masukin ke cache, langsung save. Jadi player bisa load-save berkali2 tanpa pernah cached.
KeyManager.getVirtualBalance() — dipanggil di main thread dengan .get(1, SECONDS) — ini blocking main thread dan bisa cause lag spike kalau DB lambat.
massOpen BukkitRunnable — pake WeakReference<Player> tapi tidak cancel task kalau player disconnect mid-mass-open, task jalan terus sampai remaining = 0.
PreviewGUI.getKeyBalance() untuk VIRTUAL — memanggil getVirtualBalance() yang blocking, dipanggil dari main thread saat buka GUI.
LogManager.flushQueue() — ada double-check logQueue.isEmpty() tapi synchronized(flushLock) block bisa lama kalau ada ribuan log pending.