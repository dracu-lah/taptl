# TapTL website

Plain static files: no build step, no framework. Deploy this folder as-is.

## Cloudflare Pages

- Framework preset: **None**. Build command: *(empty)*. Build output directory: `site`.
- `_headers` sets security headers (including a strict CSP) and caching.
- `_redirects` gives short links: `/apk` (latest APK), `/download`, `/sponsor`, `/coffee`, `/github`.
- `404.html` is served automatically for unknown paths.

## Domain

The site is served at `https://taptl.nevil.dev`. Canonical URLs, Open Graph, JSON-LD, the sitemap and
robots.txt all use it. To move to another domain, replace it everywhere:

```bash
grep -rl 'taptl.nevil.dev' site | xargs sed -i 's#taptl.nevil.dev#new.domain#g'
```

## Notes

- The CSP in `_headers` allows the inline theme script by its sha256. Edit that script and the hash
  has to change too.
- Images and videos are copied from `docs/` unchanged. `og.png` is a 1200x630 headless Chromium capture of a card built from the site styles.
