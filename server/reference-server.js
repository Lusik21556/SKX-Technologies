// run:  npm i express && node reference-server.js

import express from 'express';
import { readFileSync, writeFileSync, existsSync } from 'fs';

const app = express();
app.use(express.json());
app.set('trust proxy', true);

const KEYS = './keys.json';
const HASHES = './skript-hashes.json';

const load = (f, fallback) => existsSync(f) ? JSON.parse(readFileSync(f, 'utf8')) : fallback;
let keys = load(KEYS, {});
let skriptHashes = load(HASHES, []);
const saveKeys = () => writeFileSync(KEYS, JSON.stringify(keys, null, 2));

function ip(req) {
    let a = req.headers['cf-connecting-ip'] || req.headers['x-forwarded-for'] || req.ip || '0.0.0.0';
    return String(a).split(',')[0].trim().replace('::ffff:', '');
}

// the loader asks for the AES material by license. this is the one that matters.
app.get('/api/loader/key', (req, res) => {
    const entry = keys[req.query.license];
    if (!entry) return res.status(404).send('unknown license');
    if (entry.enabled === false) return res.status(403).send('disabled');
    if (entry.expires && Date.now() > entry.expires) return res.status(403).send('expired');

    // optional server-slot limit by ip. drop it if you dont care.
    if (entry.slots) {
        entry.seen = entry.seen || {};
        for (const k in entry.seen) if (Date.now() - entry.seen[k] > 3 * 60000) delete entry.seen[k];
        const here = ip(req);
        if (!entry.seen[here] && Object.keys(entry.seen).length >= entry.slots) {
            return res.status(403).send('slot limit reached');
        }
        entry.seen[here] = Date.now();
        saveKeys();
    }

    res.json({ aesKey: entry.aesKey, iv: entry.iv, checksum: entry.checksum });
});

// heartbeat + shutdown
app.post('/api/loader/heartbeat', (req, res) => res.json({ ok: true }));
app.post('/api/loader/shutdown', (req, res) => res.json({ ok: true }));

// the loader refuses to start unless the running Skript.jar hashes to one of these.
app.get('/api/loader/skript-hashes', (req, res) => res.json(skriptHashes));

// remote kill switch.
app.get('/:product/status.txt', (req, res) => res.type('text/plain').send('1'));

app.listen(3000, () => console.log('skx server on :3000'));
