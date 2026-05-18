INSERT INTO learn_categories (name, description, sort_order)
VALUES
  ('동물', '동물 단어를 학습해요', 1),
  ('음식', '음식 단어를 학습해요', 2),
  ('가족', '가족 단어를 학습해요', 3),
  ('색깔', '색깔 단어를 학습해요', 4)
  ON CONFLICT (name) DO NOTHING;
