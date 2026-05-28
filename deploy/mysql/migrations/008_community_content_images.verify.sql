-- Verification queries should return no rows. Any row indicates a failed check.
SELECT '008_missing_content_image_rows' AS check_name, COUNT(*) AS failures
FROM community_contents cc
LEFT JOIN community_content_images cci ON cci.content_id = cc.content_id
WHERE cc.image IS NOT NULL
  AND cc.image <> ''
  AND cci.content_id IS NULL
HAVING failures > 0;

SELECT '008_orphan_image_rows' AS check_name, COUNT(*) AS failures
FROM community_content_images cci
LEFT JOIN community_contents cc ON cc.content_id = cci.content_id
WHERE cc.content_id IS NULL
HAVING failures > 0;

SELECT '008_duplicate_image_order' AS check_name, COUNT(*) AS failures
FROM (
    SELECT content_id, image_order
    FROM community_content_images
    GROUP BY content_id, image_order
    HAVING COUNT(*) > 1
) duplicate_orders
HAVING failures > 0;
