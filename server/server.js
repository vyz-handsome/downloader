"use strict";
/**
 * Server lokal untuk app Downloader.
 * Jalan di Termux, hanya mendengarkan 127.0.0.1.
 *
 *   GET  /ping            -> "ok"; app memanggil ini tiap beberapa detik (detak)
 *   GET  /resolve?url=... -> link download langsung hasil scrapr
 *   POST /stop            -> server berhenti
 *
 * Kalau detak dari app berhenti (app ditutup / dihapus dari recent),
 * server mati sendiri supaya HP tidak berat.
 */
const http = require("http");
const { URL } = require("url");

const PORT = parseInt(process.env.PORT || "8787", 10);
const HOST = "127.0.0.1";
const PING_TIMEOUT_MS = 20000; // tanpa detak selama ini -> mati
const STARTUP_GRACE_MS = 90000; // waktu tunggu detak pertama setelah start
const SCRAPER_TIMEOUT_MS = 90000;

let scrapr = null;
try {
  scrapr = require("scrapr");
} catch (e) {
  console.error("[server] gagal memuat scrapr:", e.message);
}

// Hanya scraper ringan (tanpa Chrome/Puppeteer/Playwright).
// Urutan = urutan fallback.
const CHAINS = {
  tiktok: [["tiktok", "tiktokio"], ["tiktok", "snaptik"], ["tiktok", "ssstik"]],
  instagram: [["instagram", "downreels"], ["instagram", "indown"]],
  threads: [["threads", "threadster"]],
  facebook: [["facebook", "snapsave"]],
  youtube: [["youtube", "ytmp3"]],
  twitter: [["twitter", "tvd"], ["twitter", "tweeload"]],
  pinterest: [["pinterest", "pindown"]],
  spotify: [["spotify", "spotmate"], ["spotify", "spotidown"]],
  soundcloud: [["soundcloud", "klickaud"]],
  applemusic: [["applemusic", "aplmate"]],
  bandcamp: [["bandcamp", "bandcampdownloader"]],
  bilibili: [["bilibili", "snapwc"]],
  douyin: [["douyin", "direct"]],
};

const HOSTS = [
  [/(^|\.)tiktok\.com$|(^|\.)vt\.tiktok\.com$|(^|\.)vm\.tiktok\.com$/, "tiktok"],
  [/(^|\.)instagram\.com$|(^|\.)instagr\.am$/, "instagram"],
  [/(^|\.)threads\.(net|com)$/, "threads"],
  [/(^|\.)facebook\.com$|(^|\.)fb\.watch$|(^|\.)fb\.com$/, "facebook"],
  [/(^|\.)youtube\.com$|(^|\.)youtu\.be$/, "youtube"],
  [/(^|\.)twitter\.com$|(^|\.)x\.com$|(^|\.)t\.co$/, "twitter"],
  [/(^|\.)pinterest\.[a-z.]+$|(^|\.)pin\.it$/, "pinterest"],
  [/(^|\.)spotify\.com$|(^|\.)spotify\.link$/, "spotify"],
  [/(^|\.)soundcloud\.com$|(^|\.)on\.soundcloud\.com$/, "soundcloud"],
  [/(^|\.)music\.apple\.com$/, "applemusic"],
  [/(^|\.)bandcamp\.com$/, "bandcamp"],
  [/(^|\.)bilibili\.(com|tv)$|(^|\.)b23\.tv$/, "bilibili"],
  [/(^|\.)douyin\.com$|(^|\.)iesdouyin\.com$/, "douyin"],
];

function platformOf(rawUrl) {
  let host;
  try {
    host = new URL(rawUrl).hostname.toLowerCase();
  } catch {
    return null;
  }
  for (const [re, name] of HOSTS) if (re.test(host)) return name;
  return null;
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error("timeout")), ms);
    promise.then(
      (v) => {
        clearTimeout(t);
        resolve(v);
      },
      (e) => {
        clearTimeout(t);
        reject(e);
      }
    );
  });
}

function normalize(r) {
  const out = {
    title: r.title || "",
    thumbnail: r.thumbnail || "",
    type: r.type || "video",
    items: [],
  };
  const seen = new Set();
  const push = (d, extra) => {
    if (!d || typeof d.url !== "string" || !/^https?:\/\//.test(d.url)) return;
    if (seen.has(d.url)) return;
    seen.add(d.url);
    out.items.push({
      url: d.url,
      type: d.type || out.type,
      quality: String(d.quality || d.label || ""),
      title: (extra && extra.title) || "",
    });
  };
  if (Array.isArray(r.downloads)) r.downloads.forEach((d) => push(d));
  if (Array.isArray(r.tracks)) {
    r.tracks.forEach((t) => {
      const list = Array.isArray(t.downloads) ? t.downloads : t.url ? [t] : [];
      list.forEach((d) => push(d, { title: t.title || "" }));
    });
  }
  return out;
}

async function resolve(url) {
  if (!scrapr) return { ok: false, message: "scrapr belum terpasang. Jalankan ulang setup di Termux." };
  const platform = platformOf(url);
  if (!platform) return { ok: false, message: "Link ini belum didukung." };

  const tried = [];
  for (const [mod, fn] of CHAINS[platform]) {
    const name = mod + "." + fn;
    try {
      const f = scrapr[mod] && scrapr[mod][fn];
      if (typeof f !== "function") {
        tried.push(name + ": tidak ditemukan");
        continue;
      }
      const res = await withTimeout(Promise.resolve(f(url)), SCRAPER_TIMEOUT_MS);
      if (res && res.status && res.result) {
        const result = normalize(res.result);
        if (result.items.length > 0) return { ok: true, platform, scraper: name, result };
        tried.push(name + ": tidak ada link download");
      } else {
        tried.push(name + ": " + ((res && res.message) || "gagal"));
      }
    } catch (e) {
      tried.push(name + ": " + (e && e.message ? e.message : String(e)));
    }
  }
  return { ok: false, platform, message: "Semua scraper gagal untuk link ini.", tried };
}

let lastPing = Date.now();
let gotPing = false;

function send(res, code, body, type) {
  const data = typeof body === "string" ? body : JSON.stringify(body);
  res.writeHead(code, {
    "Content-Type": type || "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  res.end(data);
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, "http://localhost");
  if (u.pathname === "/ping") {
    lastPing = Date.now();
    gotPing = true;
    return send(res, 200, "ok", "text/plain");
  }
  if (u.pathname === "/resolve") {
    lastPing = Date.now();
    const target = u.searchParams.get("url") || "";
    if (!/^https?:\/\//i.test(target)) return send(res, 400, { ok: false, message: "Link tidak valid." });
    try {
      return send(res, 200, await resolve(target));
    } catch (e) {
      return send(res, 500, { ok: false, message: String((e && e.message) || e) });
    }
  }
  if (u.pathname === "/stop") {
    send(res, 200, "bye", "text/plain");
    setTimeout(() => process.exit(0), 100);
    return;
  }
  send(res, 404, { ok: false, message: "not found" });
});

server.on("error", (e) => {
  if (e.code === "EADDRINUSE") {
    // server sudah jalan, biarkan yang lama
    process.exit(0);
  }
  console.error("[server] error:", e.message);
  process.exit(1);
});

server.listen(PORT, HOST, () => {
  console.log("[server] siap di http://" + HOST + ":" + PORT);
});

setInterval(() => {
  const limit = gotPing ? PING_TIMEOUT_MS : STARTUP_GRACE_MS;
  if (Date.now() - lastPing > limit) {
    console.log("[server] tidak ada detak dari app, berhenti.");
    process.exit(0);
  }
}, 3000);
