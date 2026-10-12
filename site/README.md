# TapTL website

Plain static files: no build step, no framework. Deploy this folder as-is.

## Cloudflare

Deployed as a Worker with static assets: `wrangler.jsonc` at the repo root points at `site/`.
In the Git setup use path `/`, an empty build command and `npx wrangler deploy` as the deploy command.
(Cloudflare Pages also works: preset None, empty build command, output directory `site`.)

- `_headers` sets security headers (including a strict CSP) and caching.
- `_redirects` gives short links: `/apk` (latest APK), `/download`, `/sponsor`, `/coffee`, `/github`.
- `404.html` is served for unknown paths.

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
