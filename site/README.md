# TapTL website

Plain static files: no build step, no framework. Deploy this folder as-is.

## Cloudflare Pages

Live at https://taptl.pages.dev, deployed from this repo on every push to `main`.
Settings: framework preset **None**, empty build command, build output directory `site`.

- `_headers` sets security headers (including a strict CSP) and caching.
- `_redirects` gives short links: `/apk` (latest APK), `/download`, `/sponsor`, `/coffee`, `/github`.
- `404.html` is served for unknown paths.

## Domain

Canonical URLs, Open Graph, JSON-LD, the sitemap and robots.txt all use `https://taptl.pages.dev`.
If you add a custom domain later, replace it everywhere (and re-capture `og.png`, which shows the URL):

```bash
grep -rl 'taptl.pages.dev' site | xargs sed -i 's#taptl.pages.dev#new.domain#g'
```

## Notes

- The CSP in `_headers` allows the inline theme script by its sha256. Edit that script and the hash
  has to change too.
- Images and videos are copied from `docs/` unchanged. `og.png` is a 1200x630 headless Chromium capture of a card built from the site styles.
