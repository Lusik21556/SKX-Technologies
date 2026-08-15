Encrypts Skript files so people can run them without reading the source.

- `skx-obfuscator` - CLI turns a `.sk` into an encrypted `.skx`
- `skx-loader` - Paper plugin, decrypts `.skx` at runtime and hands it to Skript
- `server` - a small key server so the loader can fetch AES keys per license

The key is never inside the `.skx` the Loader grabs it from the server, decrypts in memory, wipes it. Disable a license server-side and it stops loading on the next rotation (every 30 min) or on restart.

The `server/` folder is just a reference so people can self host. It only does the 4 endpoints the loader calls nothing else. Swap in your own backend.

## The `# --- SKX ---` marker

Stuff above the marker stays plain text (options, config the buyer edits) Everything below gets encrypted.
Example:
```
options:
    prefix: "&8[&bShop&8]"
# --- SKX ---
# encrypted
```

if no marker the whole file is encrypted.

## Obfuscator Usage

```
java -jar skx-obfuscator.jar encrypt shop.sk --plugin-id Shop --version 1.2.0
java -jar skx-obfuscator.jar decrypt shop.skx
java -jar skx-obfuscator.jar decrypt shop.skx --key <aesKey>
java -jar skx-obfuscator.jar keys list
java -jar skx-obfuscator.jar keys show <license>
java -jar skx-obfuscator.jar keys remove <license>
```

`encrypt` prints the license key plus the aeskey, iv and checksum. Those three go into your key server under that license. Keys are also cached locally in `~/.skx/keys.json` so `decrypt` works without the server.

## Loader (buyer side)

Needs Skript, `plugin.yml` already depends on it. Drop both jars in and the script in Skript's folder:

```
plugins/Skript.jar
plugins/SkxLoader.jar
plugins/Skript/scripts/shop.skx
```

Commands:

```
/skxload [file.skx]      load everything, or one file
/skxreload <file.skx>    reload one
```

Two things to change before you build it:

- URLs in `SkxLoader.java` (and the one in `SkriptJarVerifier.java`) point them at your host
- `PINS` in `PinnedTrustManager.java` SHA-256 of your cert's public key. Leave the second slot for your renewal cert or it'll break when the cert rotates.

Getting the pin:

```
openssl s_client -connect your.host:443 </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | openssl enc -base64
```

## Key server

The loader talks to 4 endpoints:

```
GET  /api/loader/key?license=<key>
POST /api/loader/heartbeat
POST /api/loader/shutdown
GET  /api/loader/skript-hashes
```

Plus `GET /:product/status.txt` returning `1`/`0` if you want a kill switch.

Reference server:
```
cd server
npm install express
cp keys.example.json keys.json
echo '["<sha256 of your Skript.jar>"]' > skript-hashes.json
node reference-server.js
```
`keys.json` is license -> `{aesKey, iv, checksum, enabled, slots, expires}` `slots` is an optional server limit. Put the thing behind HTTPS (nginx/Cloudflare) Skript hash is just `sha256sum Skript.jar`.

## Notice

The buyer runs this on their own box so the key and the decrypted skript both land on a machine you don't control. Anyone holding a valid license can hit `/api/loader/key`, get the AES key and decrypt the file. Since the loader is open source they don't even have to reverse anything, the endpoint is right there in `SkxLoader.java` The agent/listener/pin checks make dumping the running script annoying, not impossible.

Make sure to obfuscate your skxloader build and modify it so an skxloader from here won't pull your keys

AI was used for grammar and wording in this README and in code comments, if you got a problem with this i can also write in german and good luck translating that shit
