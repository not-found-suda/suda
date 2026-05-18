-- 음식 단어 이미지/오디오 asset key 반영
-- category_id = 2: 음식

-- 1. 이미 존재하는 음식 단어가 있다면 image_url/audio_url/display_text/pronunciation_text 갱신
WITH food_words AS (
  SELECT *
  FROM (
         VALUES
           -- EASY
           (2, 'EASY', '사과', '사과', 'learn/words/food/easy/apple.png', 'learn/words/food/easy/apple.mp3', '사과', 1),
           (2, 'EASY', '우유', '우유', 'learn/words/food/easy/milk.png', 'learn/words/food/easy/milk.mp3', '우유', 2),
           (2, 'EASY', '밥', '밥', 'learn/words/food/easy/rice.png', 'learn/words/food/easy/rice.mp3', '밥', 3),
           (2, 'EASY', '빵', '빵', 'learn/words/food/easy/bread.png', 'learn/words/food/easy/bread.mp3', '빵', 4),
           (2, 'EASY', '딸기', '딸기', 'learn/words/food/easy/strawberry.png', 'learn/words/food/easy/strawberry.mp3', '딸기', 5),

           -- NORMAL
           (2, 'NORMAL', '당근', '당근', 'learn/words/food/normal/carrot.png', 'learn/words/food/normal/carrot.mp3', '당근', 1),
           (2, 'NORMAL', '치즈', '치즈', 'learn/words/food/normal/cheeze.png', 'learn/words/food/normal/cheeze.mp3', '치즈', 2),
           (2, 'NORMAL', '계란', '계란', 'learn/words/food/normal/egg.png', 'learn/words/food/normal/egg.mp3', '계란', 3),
           (2, 'NORMAL', '고기', '고기', 'learn/words/food/normal/meat.png', 'learn/words/food/normal/meat.mp3', '고기', 4),
           (2, 'NORMAL', '포도', '포도', 'learn/words/food/normal/grape.png', 'learn/words/food/normal/grape.mp3', '포도', 5),

           -- HARD
           (2, 'HARD', '옥수수', '옥수수', 'learn/words/food/hard/corner.png', 'learn/words/food/hard/corner.mp3', '옥수수', 1),
           (2, 'HARD', '요구르트', '요구르트', 'learn/words/food/hard/yogurt.png', 'learn/words/food/hard/yogurt.mp3', '요구르트', 2),
           (2, 'HARD', '샌드위치', '샌드위치', 'learn/words/food/hard/sandwich.png', 'learn/words/food/hard/sandwich.mp3', '샌드위치', 3),
           (2, 'HARD', '스파게티', '스파게티', 'learn/words/food/hard/pasta.png', 'learn/words/food/hard/pasta.mp3', '스파게티', 4),
           (2, 'HARD', '브로콜리', '브로콜리', 'learn/words/food/hard/broccoli.png', 'learn/words/food/hard/broccoli.mp3', '브로콜리', 5)
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
  FROM food_words fw
WHERE l.category_id = fw.category_id
  AND l.difficulty = fw.difficulty
  AND l.word = fw.word;


-- 2. 아직 없는 음식 단어는 새로 INSERT
-- 각 난이도별 기존 MAX(sort_order) 뒤에 이어서 자동 추가
WITH food_words AS (
  SELECT *
  FROM (
         VALUES
           -- EASY
           (2, 'EASY', '사과', '사과', 'learn/words/food/easy/apple.png', 'learn/words/food/easy/apple.mp3', '사과', 1),
           (2, 'EASY', '우유', '우유', 'learn/words/food/easy/milk.png', 'learn/words/food/easy/milk.mp3', '우유', 2),
           (2, 'EASY', '밥', '밥', 'learn/words/food/easy/rice.png', 'learn/words/food/easy/rice.mp3', '밥', 3),
           (2, 'EASY', '빵', '빵', 'learn/words/food/easy/bread.png', 'learn/words/food/easy/bread.mp3', '빵', 4),
           (2, 'EASY', '딸기', '딸기', 'learn/words/food/easy/strawberry.png', 'learn/words/food/easy/strawberry.mp3', '딸기', 5),

           -- NORMAL
           (2, 'NORMAL', '당근', '당근', 'learn/words/food/normal/carrot.png', 'learn/words/food/normal/carrot.mp3', '당근', 1),
           (2, 'NORMAL', '치즈', '치즈', 'learn/words/food/normal/cheeze.png', 'learn/words/food/normal/cheeze.mp3', '치즈', 2),
           (2, 'NORMAL', '계란', '계란', 'learn/words/food/normal/egg.png', 'learn/words/food/normal/egg.mp3', '계란', 3),
           (2, 'NORMAL', '고기', '고기', 'learn/words/food/normal/meat.png', 'learn/words/food/normal/meat.mp3', '고기', 4),
           (2, 'NORMAL', '포도', '포도', 'learn/words/food/normal/grape.png', 'learn/words/food/normal/grape.mp3', '포도', 5),

           -- HARD
           (2, 'HARD', '옥수수', '옥수수', 'learn/words/food/hard/corner.png', 'learn/words/food/hard/corner.mp3', '옥수수', 1),
           (2, 'HARD', '요구르트', '요구르트', 'learn/words/food/hard/yogurt.png', 'learn/words/food/hard/yogurt.mp3', '요구르트', 2),
           (2, 'HARD', '샌드위치', '샌드위치', 'learn/words/food/hard/sandwich.png', 'learn/words/food/hard/sandwich.mp3', '샌드위치', 3),
           (2, 'HARD', '스파게티', '스파게티', 'learn/words/food/hard/pasta.png', 'learn/words/food/hard/pasta.mp3', '스파게티', 4),
           (2, 'HARD', '브로콜리', '브로콜리', 'learn/words/food/hard/broccoli.png', 'learn/words/food/hard/broccoli.mp3', '브로콜리', 5)
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
       FROM food_words fw
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
ORDER BY mw.difficulty, mw.insert_order;
