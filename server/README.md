# بستهٔ API موبایل برای مخزن وب (`jametulhoda`)

این پوشه، پیاده‌سازی کامل **API احراز هویت موبایل + لایک + محتوا** برای
وب‌سایت (`hajiahmadandishmand41-code/jametulhoda`) را به‌صورت یک commit آماده
تحویل می‌دهد. اپلیکیشن اندروید (همین مخزن) با همین قرارداد کار می‌کند.

## چرا patch؟

محیط ساخت این مخزن فقط دسترسیِ نوشتن روی `jametulhoda2` را دارد (push به
`jametulhoda` با ۴۰۳ رد می‌شود). بنابراین تغییرات مخزن وب اینجا نگهداری و
تحویل داده می‌شوند تا با یک فرمان روی مخزن وب اعمال شوند.

## اعمال روی مخزن وب

```sh
git clone https://github.com/hajiahmadandishmand41-code/jametulhoda.git
cd jametulhoda
git checkout -b feature/mobile-api
git am /path/to/server/mobile-api.patch
git push -u origin feature/mobile-api
# سپس PR به main و merge؛ استقرار Vercel خودکار انجام می‌شود.
```

## محتوای commit

- `api/mobile/` — endpointهای JSON (خانه، فهرست‌ها، جزئیات، کتاب/درس/موضوع/رسانه، جست‌وجو، دسته‌ها، درباره/تماس)
- `api/mobile/auth.php` — ثبت‌نام/ورود/پروفایل/تغییر رمز/خروج با توکن Bearer (hash در `api_tokens`)
- `api/mobile/likes.php` — لایک idempotent با قید یکتایی `(user_id, ref_type, ref_id)` در `content_likes`
- `database/*.sql` + `tests/fixtures/schema.sqlite.sql` — جدول‌های جدید فقط افزودنی/idempotent
- `docs/MOBILE-API.md` — قرارداد کامل API
- `tests/mobile-api.mjs` — ۶۷ آزمون قرارداد (بدون وابستگی به Playwright)
- `.github/workflows/mobile-api.yml` — اجرای آزمون‌ها در CI مخزن وب

## پس از اعمال

```sh
php bin/migrate.php          # جدول‌های api_tokens/content_likes (idempotent؛ بدون حذف داده)
node tests/mobile-api.mjs    # با TEST_BASE_URL سایت/محلی
```

قرارداد کامل: `docs/MOBILE-API.md` در همان مخزن (داخل patch).
