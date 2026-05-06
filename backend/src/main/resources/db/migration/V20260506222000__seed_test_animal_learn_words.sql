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
  '강아지',
  '강아지',
  '강아지',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/dog.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/easy/dog.mp3',
  true,
  1
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
  'MEDIUM',
  '토끼',
  '토끼',
  '토끼',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/medium/rabit.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/medium/rabit.mp3',
  true,
  1
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
  'HARD',
  '기린',
  '기린',
  '기린',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/hard/giraffe.png',
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/learn/words/animals/hard/giraffe.mp3',
  true,
  1
FROM learn_categories
WHERE name = '동물';
