-- 색깔 단어 이미지/오디오 asset key 반영
-- category_id = 4: 색깔
-- difficulty = EASY / NORMAL / HARD

-- 1. 이미 존재하는 색깔 단어가 있다면 image_url/audio_url/display_text/pronunciation_text 갱신
WITH color_words AS (
  SELECT *
  FROM (
         VALUES
           -- EASY
           (4, 'EASY', '검정', '검정', 'learn/words/color/easy/black.png', 'learn/words/color/easy/black.mp3', '검정', 1),
           (4, 'EASY', '하양', '하양', 'learn/words/color/easy/white.png', 'learn/words/color/easy/white.mp3', '하양', 2),
           (4, 'EASY', '파랑', '파랑', 'learn/words/color/easy/blue.png', 'learn/words/color/easy/blue.mp3', '파랑', 3),
           (4, 'EASY', '노랑', '노랑', 'learn/words/color/easy/yellow.png', 'learn/words/color/easy/yellow.mp3', '노랑', 4),
           (4, 'EASY', '빨강', '빨강', 'learn/words/color/easy/red.png', 'learn/words/color/easy/red.mp3', '빨강', 5),

           -- NORMAL
           (4, 'NORMAL', '회색', '회색', 'learn/words/color/normal/gray.png', 'learn/words/color/normal/gray.mp3', '회색', 1),
           (4, 'NORMAL', '남색', '남색', 'learn/words/color/normal/blue2.png', 'learn/words/color/normal/blue2.mp3', '남색', 2),
           (4, 'NORMAL', '주황', '주황', 'learn/words/color/normal/orange.png', 'learn/words/color/normal/orange.mp3', '주황', 3),
           (4, 'NORMAL', '초록', '초록', 'learn/words/color/normal/green.png', 'learn/words/color/normal/green.mp3', '초록', 4),
           (4, 'NORMAL', '보라', '보라', 'learn/words/color/normal/purple.png', 'learn/words/color/normal/purple.mp3', '보라', 5),

           -- HARD
           (4, 'HARD', '금색', '금색', 'learn/words/color/hard/gold.png', 'learn/words/color/hard/gold.mp3', '금색', 1),
           (4, 'HARD', '하늘색', '하늘색', 'learn/words/color/hard/skyblue.png', 'learn/words/color/hard/skyblue.mp3', '하늘색', 2),
           (4, 'HARD', '연두색', '연두색', 'learn/words/color/hard/lightgreen.png', 'learn/words/color/hard/lightgreen.mp3', '연두색', 3),
           (4, 'HARD', '무지개색', '무지개색', 'learn/words/color/hard/rainbow.png', 'learn/words/color/hard/rainbow.mp3', '무지개색', 4),
           (4, 'HARD', '갈색', '갈색', 'learn/words/color/hard/brown.png', 'learn/words/color/hard/brown.mp3', '갈색', 5)
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
  display_text = cw.display_text,
  image_url = cw.image_url,
  audio_url = cw.audio_url,
  pronunciation_text = cw.pronunciation_text,
  active = true,
  updated_at = CURRENT_TIMESTAMP
  FROM color_words cw
WHERE l.category_id = cw.category_id
  AND l.difficulty = cw.difficulty
  AND l.word = cw.word;


-- 2. 아직 없는 색깔 단어는 새로 INSERT
-- 각 난이도별 기존 MAX(sort_order) 뒤에 이어서 자동 추가
WITH color_words AS (
  SELECT *
  FROM (
         VALUES
           -- EASY
           (4, 'EASY', '검정', '검정', 'learn/words/color/easy/black.png', 'learn/words/color/easy/black.mp3', '검정', 1),
           (4, 'EASY', '하양', '하양', 'learn/words/color/easy/white.png', 'learn/words/color/easy/white.mp3', '하양', 2),
           (4, 'EASY', '파랑', '파랑', 'learn/words/color/easy/blue.png', 'learn/words/color/easy/blue.mp3', '파랑', 3),
           (4, 'EASY', '노랑', '노랑', 'learn/words/color/easy/yellow.png', 'learn/words/color/easy/yellow.mp3', '노랑', 4),
           (4, 'EASY', '빨강', '빨강', 'learn/words/color/easy/red.png', 'learn/words/color/easy/red.mp3', '빨강', 5),

           -- NORMAL
           (4, 'NORMAL', '회색', '회색', 'learn/words/color/normal/gray.png', 'learn/words/color/normal/gray.mp3', '회색', 1),
           (4, 'NORMAL', '남색', '남색', 'learn/words/color/normal/blue2.png', 'learn/words/color/normal/blue2.mp3', '남색', 2),
           (4, 'NORMAL', '주황', '주황', 'learn/words/color/normal/orange.png', 'learn/words/color/normal/orange.mp3', '주황', 3),
           (4, 'NORMAL', '초록', '초록', 'learn/words/color/normal/green.png', 'learn/words/color/normal/green.mp3', '초록', 4),
           (4, 'NORMAL', '보라', '보라', 'learn/words/color/normal/purple.png', 'learn/words/color/normal/purple.mp3', '보라', 5),

           -- HARD
           (4, 'HARD', '금색', '금색', 'learn/words/color/hard/gold.png', 'learn/words/color/hard/gold.mp3', '금색', 1),
           (4, 'HARD', '하늘색', '하늘색', 'learn/words/color/hard/skyblue.png', 'learn/words/color/hard/skyblue.mp3', '하늘색', 2),
           (4, 'HARD', '연두색', '연두색', 'learn/words/color/hard/lightgreen.png', 'learn/words/color/hard/lightgreen.mp3', '연두색', 3),
           (4, 'HARD', '무지개색', '무지개색', 'learn/words/color/hard/rainbow.png', 'learn/words/color/hard/rainbow.mp3', '무지개색', 4),
           (4, 'HARD', '갈색', '갈색', 'learn/words/color/hard/brown.png', 'learn/words/color/hard/brown.mp3', '갈색', 5)
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
         cw.*,
         ROW_NUMBER() OVER (
            PARTITION BY cw.category_id, cw.difficulty
            ORDER BY cw.order_offset
        ) AS insert_order
       FROM color_words cw
       WHERE NOT EXISTS (
         SELECT 1
         FROM learn l
         WHERE l.category_id = cw.category_id
           AND l.difficulty = cw.difficulty
           AND l.word = cw.word
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
ORDER BY mw.difficulty, mw.insert_order;
