UPDATE plans
SET feedback = LEFT(feedback, 100)
WHERE feedback IS NOT NULL AND CHAR_LENGTH(feedback) > 100;

ALTER TABLE plans
    MODIFY COLUMN feedback VARCHAR(100) NULL;
