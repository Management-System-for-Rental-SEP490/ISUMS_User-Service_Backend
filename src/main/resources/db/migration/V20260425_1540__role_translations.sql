-- Phase 5d i18n: per-locale role descriptions, seeded for the 4 platform roles.
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS description_translations TEXT;

COMMENT ON COLUMN roles.description_translations IS
    'JSON map of locale -> translated role description. Reserved keys: _source, _auto.';

-- Seed translations for the four canonical roles. Idempotent — uses code lookup.
UPDATE roles
SET description_translations = jsonb_build_object(
    'vi', 'Chủ nhà — Người sở hữu hệ thống cho thuê, có toàn quyền cấu hình.',
    'en', 'Landlord — owns the rental property and has full configuration access.',
    'ja', '家主 — 賃貸物件の所有者で、すべての設定権限を持ちます。',
    '_source', 'vi'
)::text
WHERE code = 'ROLE_LANDLORD' AND description_translations IS NULL;

UPDATE roles
SET description_translations = jsonb_build_object(
    'vi', 'Quản lý — Phụ trách một hoặc nhiều khu vực được giao.',
    'en', 'Manager — responsible for one or more assigned regions.',
    'ja', 'マネージャー — 割り当てられた地域を担当します。',
    '_source', 'vi'
)::text
WHERE code = 'ROLE_MANAGER' AND description_translations IS NULL;

UPDATE roles
SET description_translations = jsonb_build_object(
    'vi', 'Nhân viên kỹ thuật — Xử lý sự cố và bảo trì cho khu vực được phân công.',
    'en', 'Technical staff — handles issues and maintenance for assigned regions.',
    'ja', '技術スタッフ — 担当地域の問題対応と保守を行います。',
    '_source', 'vi'
)::text
WHERE code = 'ROLE_TECH_STAFF' AND description_translations IS NULL;

UPDATE roles
SET description_translations = jsonb_build_object(
    'vi', 'Khách thuê — Người ở trong nhà thuê, có thể báo sự cố và xem hoá đơn.',
    'en', 'Tenant — resident of a rental unit, can report issues and view invoices.',
    'ja', 'テナント — 賃貸住宅の入居者。問題報告や請求確認ができます。',
    '_source', 'vi'
)::text
WHERE code = 'ROLE_TENANT' AND description_translations IS NULL;
