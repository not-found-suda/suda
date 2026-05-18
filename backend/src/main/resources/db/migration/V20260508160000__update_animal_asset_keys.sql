-- 기존 토끼 데이터: 잘못된 medium 경로를 normal 경로로 수정
UPDATE learn
SET
  image_url = 'learn/words/animals/normal/rabit.png',
  audio_url = 'learn/words/animals/normal/rabit.mp3',
  updated_at = CURRENT_TIMESTAMP
WHERE category_id = 1
  AND difficulty = 'NORMAL'
  AND word = '토끼';


-- NORMAL 난이도 동물 단어 추가
-- 기존 동물 NORMAL 데이터의 마지막 sort_order 뒤에 이어서 추가
WITH normal_base AS (
  SELECT COALESCE(MAX(sort_order), 0) AS base_sort_order
  FROM learn
  WHERE category_id = 1
    AND difficulty = 'NORMAL'
),
     normal_words AS (
       SELECT *
       FROM (
              VALUES
                (1, 'NORMAL', '오리', '오리', 'learn/words/animals/normal/duck.png', 'learn/words/animals/normal/duck.mp3', '오리', 1),
                (1, 'NORMAL', '사자', '사자', 'learn/words/animals/normal/lion.png', 'learn/words/animals/normal/lion.mp3', '사자', 2),
                (1, 'NORMAL', '하마', '하마', 'learn/words/animals/normal/hippo.png', 'learn/words/animals/normal/hippo.mp3', '하마', 3),
                (1, 'NORMAL', '악어', '악어', 'learn/words/animals/normal/crocodile.png', 'learn/words/animals/normal/crocodile.mp3', '악어', 4)
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
  nw.category_id,
  nw.difficulty,
  nw.word,
  nw.display_text,
  nw.image_url,
  nw.audio_url,
  nw.pronunciation_text,
  true,
  nb.base_sort_order + nw.order_offset,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM normal_words nw
       CROSS JOIN normal_base nb
WHERE NOT EXISTS (
  SELECT 1
  FROM learn l
  WHERE l.category_id = nw.category_id
    AND l.difficulty = nw.difficulty
    AND l.word = nw.word
);


-- HARD 난이도 동물 단어 추가
-- 기존 동물 HARD 데이터의 마지막 sort_order 뒤에 이어서 추가
WITH hard_base AS (
  SELECT COALESCE(MAX(sort_order), 0) AS base_sort_order
  FROM learn
  WHERE category_id = 1
    AND difficulty = 'HARD'
),
     hard_words AS (
       SELECT *
       FROM (
              VALUES
                (1, 'HARD', '원숭이', '원숭이', 'learn/words/animals/hard/monkey.png', 'learn/words/animals/hard/monkey.mp3', '원숭이', 1),
                (1, 'HARD', '코뿔소', '코뿔소', 'learn/words/animals/hard/rhinoceros.png', 'learn/words/animals/hard/rhinoceros.mp3', '코뿔소', 2),
                (1, 'HARD', '캥거루', '캥거루', 'learn/words/animals/hard/kangaroo.png', 'learn/words/animals/hard/kangaroo.mp3', '캥거루', 3),
                (1, 'HARD', '다람쥐', '다람쥐', 'learn/words/animals/hard/squirrel.png', 'learn/words/animals/hard/squirrel.mp3', '다람쥐', 4)
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
  hw.category_id,
  hw.difficulty,
  hw.word,
  hw.display_text,
  hw.image_url,
  hw.audio_url,
  hw.pronunciation_text,
  true,
  hb.base_sort_order + hw.order_offset,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM hard_words hw
       CROSS JOIN hard_base hb
WHERE NOT EXISTS (
  SELECT 1
  FROM learn l
  WHERE l.category_id = hw.category_id
    AND l.difficulty = hw.difficulty
    AND l.word = hw.word
);
