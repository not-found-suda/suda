-- 탈것 단어 이미지/오디오 asset key 반영
-- category_id = 5: 탈것
-- difficulty = EASY / NORMAL / HARD

-- 1. 이미 존재하는 탈것 단어가 있다면 image_url/audio_url/display_text/pronunciation_text 갱신
WITH vehicle_words AS (
  SELECT *
  FROM (
         VALUES
           -- EASY
           (5, 'EASY', '자동차', '자동차', 'learn/words/vehicle/easy/car.png', 'learn/words/vehicle/easy/car.mp3', '자동차', 1),
           (5, 'EASY', '택시', '택시', 'learn/words/vehicle/easy/taxi.png', 'learn/words/vehicle/easy/taxi.mp3', '택시', 2),
           (5, 'EASY', '버스', '버스', 'learn/words/vehicle/easy/bus.png', 'learn/words/vehicle/easy/bus.mp3', '버스', 3),
           (5, 'EASY', '기차', '기차', 'learn/words/vehicle/easy/train.png', 'learn/words/vehicle/easy/train.mp3', '기차', 4),
           (5, 'EASY', '배', '배', 'learn/words/vehicle/easy/ship.png', 'learn/words/vehicle/easy/ship.mp3', '배', 5),

           -- NORMAL
           (5, 'NORMAL', '경찰차', '경찰차', 'learn/words/vehicle/normal/policecar.png', 'learn/words/vehicle/normal/policecar.mp3', '경찰차', 1),
           (5, 'NORMAL', '구급차', '구급차', 'learn/words/vehicle/normal/ambulance.png', 'learn/words/vehicle/normal/ambulance.mp3', '구급차', 2),
           (5, 'NORMAL', '비행기', '비행기', 'learn/words/vehicle/normal/airplane.png', 'learn/words/vehicle/normal/airplane.mp3', '비행기', 3),
           (5, 'NORMAL', '소방차', '소방차', 'learn/words/vehicle/normal/fireengine.png', 'learn/words/vehicle/normal/fireengine.mp3', '소방차', 4),
           (5, 'NORMAL', '자전거', '자전거', 'learn/words/vehicle/normal/bicycle.png', 'learn/words/vehicle/normal/bicycle.mp3', '자전거', 5),

           -- HARD
           (5, 'HARD', '포크레인', '포크레인', 'learn/words/vehicle/hard/excavator.png', 'learn/words/vehicle/hard/excavator.mp3', '포크레인', 1),
           (5, 'HARD', '우주선', '우주선', 'learn/words/vehicle/hard/spaceship.png', 'learn/words/vehicle/hard/spaceship.mp3', '우주선', 2),
           (5, 'HARD', '잠수함', '잠수함', 'learn/words/vehicle/hard/submarine.png', 'learn/words/vehicle/hard/submarine.mp3', '잠수함', 3),
           (5, 'HARD', '헬리콥터', '헬리콥터', 'learn/words/vehicle/hard/helicopter.png', 'learn/words/vehicle/hard/helicopter.mp3', '헬리콥터', 4),
           (5, 'HARD', '오토바이', '오토바이', 'learn/words/vehicle/hard/motorcycle.png', 'learn/words/vehicle/hard/motorcycle.mp3', '오토바이', 5)
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
  display_text = vw.display_text,
  image_url = vw.image_url,
  audio_url = vw.audio_url,
  pronunciation_text = vw.pronunciation_text,
  active = true,
  updated_at = CURRENT_TIMESTAMP
  FROM vehicle_words vw
WHERE l.category_id = vw.category_id
  AND l.difficulty = vw.difficulty
  AND l.word = vw.word;


-- 2. 아직 없는 탈것 단어는 새로 INSERT
-- 각 난이도별 기존 MAX(sort_order) 뒤에 이어서 자동 추가
WITH vehicle_words AS (
  SELECT *
  FROM (
         VALUES
           -- EASY
           (5, 'EASY', '자동차', '자동차', 'learn/words/vehicle/easy/car.png', 'learn/words/vehicle/easy/car.mp3', '자동차', 1),
           (5, 'EASY', '택시', '택시', 'learn/words/vehicle/easy/taxi.png', 'learn/words/vehicle/easy/taxi.mp3', '택시', 2),
           (5, 'EASY', '버스', '버스', 'learn/words/vehicle/easy/bus.png', 'learn/words/vehicle/easy/bus.mp3', '버스', 3),
           (5, 'EASY', '기차', '기차', 'learn/words/vehicle/easy/train.png', 'learn/words/vehicle/easy/train.mp3', '기차', 4),
           (5, 'EASY', '배', '배', 'learn/words/vehicle/easy/ship.png', 'learn/words/vehicle/easy/ship.mp3', '배', 5),

           -- NORMAL
           (5, 'NORMAL', '경찰차', '경찰차', 'learn/words/vehicle/normal/policecar.png', 'learn/words/vehicle/normal/policecar.mp3', '경찰차', 1),
           (5, 'NORMAL', '구급차', '구급차', 'learn/words/vehicle/normal/ambulance.png', 'learn/words/vehicle/normal/ambulance.mp3', '구급차', 2),
           (5, 'NORMAL', '비행기', '비행기', 'learn/words/vehicle/normal/airplane.png', 'learn/words/vehicle/normal/airplane.mp3', '비행기', 3),
           (5, 'NORMAL', '소방차', '소방차', 'learn/words/vehicle/normal/fireengine.png', 'learn/words/vehicle/normal/fireengine.mp3', '소방차', 4),
           (5, 'NORMAL', '자전거', '자전거', 'learn/words/vehicle/normal/bicycle.png', 'learn/words/vehicle/normal/bicycle.mp3', '자전거', 5),

           -- HARD
           (5, 'HARD', '포크레인', '포크레인', 'learn/words/vehicle/hard/excavator.png', 'learn/words/vehicle/hard/excavator.mp3', '포크레인', 1),
           (5, 'HARD', '우주선', '우주선', 'learn/words/vehicle/hard/spaceship.png', 'learn/words/vehicle/hard/spaceship.mp3', '우주선', 2),
           (5, 'HARD', '잠수함', '잠수함', 'learn/words/vehicle/hard/submarine.png', 'learn/words/vehicle/hard/submarine.mp3', '잠수함', 3),
           (5, 'HARD', '헬리콥터', '헬리콥터', 'learn/words/vehicle/hard/helicopter.png', 'learn/words/vehicle/hard/helicopter.mp3', '헬리콥터', 4),
           (5, 'HARD', '오토바이', '오토바이', 'learn/words/vehicle/hard/motorcycle.png', 'learn/words/vehicle/hard/motorcycle.mp3', '오토바이', 5)
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
         vw.*,
         ROW_NUMBER() OVER (
            PARTITION BY vw.category_id, vw.difficulty
            ORDER BY vw.order_offset
        ) AS insert_order
       FROM vehicle_words vw
       WHERE NOT EXISTS (
         SELECT 1
         FROM learn l
         WHERE l.category_id = vw.category_id
           AND l.difficulty = vw.difficulty
           AND l.word = vw.word
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
