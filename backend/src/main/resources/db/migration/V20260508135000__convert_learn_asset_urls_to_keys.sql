-- learn_categories.thumbnail_url: S3 URL -> object key
UPDATE learn_categories
SET thumbnail_url = REPLACE(
  thumbnail_url,
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/',
  ''
                    )
WHERE thumbnail_url LIKE 'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/%';

-- learn_categories.thumbnail_url: CloudFront URL -> object key
UPDATE learn_categories
SET thumbnail_url = REPLACE(
  thumbnail_url,
  'https://d14zyw67949hvk.cloudfront.net/',
  ''
                    )
WHERE thumbnail_url LIKE 'https://d14zyw67949hvk.cloudfront.net/%';


-- learn.image_url: S3 URL -> object key
UPDATE learn
SET image_url = REPLACE(
  image_url,
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/',
  ''
                )
WHERE image_url LIKE 'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/%';

-- learn.image_url: CloudFront URL -> object key
UPDATE learn
SET image_url = REPLACE(
  image_url,
  'https://d14zyw67949hvk.cloudfront.net/',
  ''
                )
WHERE image_url LIKE 'https://d14zyw67949hvk.cloudfront.net/%';


-- learn.audio_url: S3 URL -> object key
UPDATE learn
SET audio_url = REPLACE(
  audio_url,
  'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/',
  ''
                )
WHERE audio_url LIKE 'https://suda-s3-bucket-570515227038-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/%';

-- learn.audio_url: CloudFront URL -> object key
UPDATE learn
SET audio_url = REPLACE(
  audio_url,
  'https://d14zyw67949hvk.cloudfront.net/',
  ''
                )
WHERE audio_url LIKE 'https://d14zyw67949hvk.cloudfront.net/%';
