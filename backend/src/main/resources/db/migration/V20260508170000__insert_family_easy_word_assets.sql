-- 가족 EASY 단어 이미지/오디오 asset key 반영
-- category_id = 3: 가족
-- difficulty = EASY

-- 1. 이미 존재하는 가족 EASY 단어가 있다면 image_url/audio_url/display_text/pronunciation_text 갱신
WITH family_words AS (
  SELECT *
  FROM (
         VALUES
           (3, 'EASY', '할아버지', '할아버지', 'learn/words/family/easy/grandfa.png', 'learn/words/family/easy/grandfa.mp3', '할아버지', 1),
           (3, 'EASY', '할머니', '할머니', 'learn/words/family/easy/grandma.png', 'learn/words/family/easy/grandma.mp3', '할머니', 2),
           (3, 'EASY', '나', '나', 'learn/words/family/easy/me.png', 'learn/words/family/easy/me.mp3', '나', 3),
           (3, 'EASY', '엄마', '엄마', 'learn/words/family/easy/mom.png', 'learn/words/family/easy/mom.mp3', '엄마', 4),
           (3, 'EASY', '아빠', '아빠', 'learn/words/family/easy/dad.png', 'learn/words/family/easy/dad.mp3', '아빠', 5)
       ) AS v (
               category_id,
               difficulty,
               word,
               display_text,
               image_url,
               audio_url,
               pronunciation_text,
               order_offset
    )
)
UPDATE learn l
SET
  display_text = fw.display_text,
  image_url = fw.image_url,
  audio_url = fw.audio_url,
  pronunciation_text = fw.pronunciation_text,
  active = true,
  updated_at = CURRENT_TIMESTAMP
  FROM family_words fw
WHERE l.category_id = fw.category_id
  AND l.difficulty = fw.difficulty
  AND l.word = fw.word;


-- 2. 아직 없는 가족 EASY 단어는 새로 INSERT
-- 기존 가족 EASY 데이터의 MAX(sort_order) 뒤에 이어서 자동 추가
WITH family_words AS (
  SELECT *
  FROM (
         VALUES
           (3, 'EASY', '할아버지', '할아버지', 'learn/words/family/easy/grandfa.png', 'learn/words/family/easy/grandfa.mp3', '할아버지', 1),
           (3, 'EASY', '할머니', '할머니', 'learn/words/family/easy/grandma.png', 'learn/words/family/easy/grandma.mp3', '할머니', 2),
           (3, 'EASY', '나', '나', 'learn/words/family/easy/me.png', 'learn/words/family/easy/me.mp3', '나', 3),
           (3, 'EASY', '엄마', '엄마', 'learn/words/family/easy/mom.png', 'learn/words/family/easy/mom.mp3', '엄마', 4),
           (3, 'EASY', '아빠', '아빠', 'learn/words/family/easy/dad.png', 'learn/words/family/easy/dad.mp3', '아빠', 5)
       ) AS v (
               category_id,
               difficulty,
               word,
               display_text,
               image_url,
               audio_url,
               pronunciation_text,
               order_offset
    )
),
     missing_words AS (
       SELECT
         fw.*,
         ROW_NUMBER() OVER (
            PARTITION BY fw.category_id, fw.difficulty
            ORDER BY fw.order_offset
        ) AS insert_order
       FROM family_words fw
       WHERE NOT EXISTS (
         SELECT 1
         FROM learn l
         WHERE l.category_id = fw.category_id
           AND l.difficulty = fw.difficulty
           AND l.word = fw.word
       )
     ),
     sort_base AS (
       SELECT
         mw.category_id,
         mw.difficulty,
         COALESCE(MAX(l.sort_order), 0) AS base_sort_order
       FROM missing_words mw
              LEFT JOIN learn l
                        ON l.category_id = mw.category_id
                          AND l.difficulty = mw.difficulty
       GROUP BY mw.category_id, mw.difficulty
     )
INSERT INTO learn (
    category_id,
    difficulty,
    word,
    display_text,
    image_url,
    audio_url,
    pronunciation_text,
    active,
    sort_order,
    created_at,
    updated_at
)
SELECT
  mw.category_id,
  mw.difficulty,
  mw.word,
  mw.display_text,
  mw.image_url,
  mw.audio_url,
  mw.pronunciation_text,
  true,
  sb.base_sort_order + mw.insert_order,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM missing_words mw
       JOIN sort_base sb
            ON sb.category_id = mw.category_id
              AND sb.difficulty = mw.difficulty
ORDER BY mw.insert_order;
