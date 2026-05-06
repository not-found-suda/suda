INSERT INTO learn (
  category_id,
  difficulty,
  word,
  display_text,
  pronunciation_text,
  image_url,
  audio_url,
  active,
  sort_order
)
SELECT
  id,
  'EASY',
  '곰',
  '곰',
  '곰',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/bear.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/bear.mp3',
  true,
  2
FROM learn_categories
WHERE name = '동물';

INSERT INTO learn (
  category_id,
  difficulty,
  word,
  display_text,
  pronunciation_text,
  image_url,
  audio_url,
  active,
  sort_order
)
SELECT
  id,
  'EASY',
  '소',
  '소',
  '소',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/cow.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/cow.mp3',
  true,
  3
FROM learn_categories
WHERE name = '동물';

INSERT INTO learn (
  category_id,
  difficulty,
  word,
  display_text,
  pronunciation_text,
  image_url,
  audio_url,
  active,
  sort_order
)
SELECT
  id,
  'EASY',
  '말',
  '말',
  '말',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/horse.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/horse.mp3',
  true,
  4
FROM learn_categories
WHERE name = '동물';

INSERT INTO learn (
  category_id,
  difficulty,
  word,
  display_text,
  pronunciation_text,
  image_url,
  audio_url,
  active,
  sort_order
)
SELECT
  id,
  'EASY',
  '새',
  '새',
  '새',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/bird.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/bird.mp3',
  true,
  5
FROM learn_categories
WHERE name = '동물';
