ALTER TABLE learning_contents RENAME TO learn;

ALTER INDEX ix_learning_contents_category_difficulty
RENAME TO ix_learn_category_difficulty;

ALTER INDEX ux_learning_contents_unique_word
RENAME TO ux_learn_unique_word;
