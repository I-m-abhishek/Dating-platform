-- =====================================================================
-- Reference data seed.
--
-- Interests and qualities are not decoration: the compatibility scorer
-- computes set overlap over interests and affinity over qualities, so
-- these rows are effectively part of the matching algorithm.
--
-- affinity_weight on a quality means:
--    +1.0  pairs best with the same trait (shared values)
--     0.0  neutral
--    -1.0  pairs best with its opposite (complementary temperament)
-- =====================================================================

INSERT INTO interests (id, slug, label, category, emoji, active, created_at, updated_at, version) VALUES
 (gen_random_uuid(), 'hiking',        'Hiking',         'Outdoors',   '🥾', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'running',       'Running',        'Fitness',    '🏃', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'yoga',          'Yoga',           'Fitness',    '🧘', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'climbing',      'Climbing',       'Outdoors',   '🧗', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'cycling',       'Cycling',        'Outdoors',   '🚴', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'live-music',    'Live music',     'Music',      '🎤', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'vinyl',         'Vinyl',          'Music',      '🎧', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'playing-music', 'Playing music',  'Music',      '🎸', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'cooking',       'Cooking',        'Food',       '🍳', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'baking',        'Baking',         'Food',       '🧁', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'coffee',        'Coffee',         'Food',       '☕', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'wine',          'Wine',           'Food',       '🍷', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'street-food',   'Street food',    'Food',       '🌮', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'reading',       'Reading',        'Culture',    '📚', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'film',          'Film',           'Culture',    '🎬', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'theatre',       'Theatre',        'Culture',    '🎭', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'art-galleries', 'Art galleries',  'Culture',    '🖼️', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'photography',   'Photography',    'Creative',   '📷', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'painting',      'Painting',       'Creative',   '🎨', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'writing',       'Writing',        'Creative',   '✍️', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'board-games',   'Board games',    'Play',       '🎲', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'video-games',   'Video games',    'Play',       '🎮', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'travel',        'Travel',         'Lifestyle',  '✈️', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'camping',       'Camping',        'Outdoors',   '⛺', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'dogs',          'Dogs',           'Animals',    '🐕', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'cats',          'Cats',           'Animals',    '🐈', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'volunteering',  'Volunteering',   'Community',  '🤝', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'startups',      'Startups',       'Work',       '🚀', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'podcasts',      'Podcasts',       'Culture',    '🎙️', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'dancing',       'Dancing',        'Play',       '💃', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'football',      'Football',       'Sport',      '⚽', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'cricket',       'Cricket',        'Sport',      '🏏', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'swimming',      'Swimming',       'Sport',      '🏊', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'meditation',    'Meditation',     'Wellbeing',  '🕯️', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'gardening',     'Gardening',      'Lifestyle',  '🌱', TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'astronomy',     'Astronomy',      'Science',    '🔭', TRUE, NOW(), NOW(), 0);

-- Qualities are grouped by "dimension": the scorer compares the two people's picks
-- within the same dimension, so dimensions are what make the comparison meaningful.
INSERT INTO qualities (id, slug, label, dimension, affinity_weight, active, created_at, updated_at, version) VALUES
 (gen_random_uuid(), 'ambitious',      'Ambitious',       'drive',        0.8,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'easygoing',      'Easygoing',       'drive',        0.6,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'spontaneous',    'Spontaneous',     'planning',    -0.4,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'organised',      'Organised',       'planning',    -0.4,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'introverted',    'Introverted',     'energy',      -0.2,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'extroverted',    'Extroverted',     'energy',      -0.2,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'empathetic',     'Empathetic',      'warmth',       1.0,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'direct',         'Direct',          'warmth',       0.5,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'funny',          'Funny',           'humour',       0.9,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'dry-humour',     'Dry humour',      'humour',       0.7,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'curious',        'Curious',         'openness',     1.0,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'grounded',       'Grounded',        'openness',     0.6,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'adventurous',    'Adventurous',     'risk',         0.9,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'homebody',       'Homebody',        'risk',         0.9,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'loyal',          'Loyal',           'commitment',   1.0,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'independent',    'Independent',     'commitment',   0.4,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'creative',       'Creative',        'mindset',      0.7,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'analytical',     'Analytical',      'mindset',      0.3,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'optimistic',     'Optimistic',      'outlook',      0.9,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'realistic',      'Realistic',       'outlook',      0.6,  TRUE, NOW(), NOW(), 0);

INSERT INTO prompts (id, slug, text, category, display_order, active, created_at, updated_at, version) VALUES
 (gen_random_uuid(), 'life-goal',        'A life goal of mine',                          'About me',   1,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'weekend',          'My perfect Sunday looks like',                 'About me',   2,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'irrationally',     'I am irrationally competitive about',          'Fun',        3,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'together-we',      'Together we could',                            'Dating',     4,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'first-round',      'The first round is on me if',                  'Dating',     5,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'never-shut-up',    'I will never shut up about',                   'Fun',        6,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'green-flag',       'A green flag I look for',                      'Dating',     7,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'best-travel',      'The best trip I have taken',                   'Travel',     8,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'teach-me',         'Teach me something about',                     'Fun',        9,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'unusual-skill',    'An unusual skill I have',                      'About me',  10,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'change-my-mind',   'Change my mind about',                         'Fun',       11,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'love-language',    'I show I care by',                             'Dating',    12,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'weekly-ritual',    'A weekly ritual I never skip',                 'About me',  13,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'made-me-laugh',    'The last thing that made me laugh out loud',   'Fun',       14,  TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'looking-for',      'What I am actually looking for',               'Dating',    15,  TRUE, NOW(), NOW(), 0);

-- Plans. Prices are in minor units (cents). The tier column is what every
-- entitlement check reads - features are derived from it, never stored per plan.
INSERT INTO plans (id, code, name, description, tier, price_minor, currency, billing_period_months, display_order, active, created_at, updated_at, version) VALUES
 (gen_random_uuid(), 'plus-monthly',     'Plus',            'See who likes you, more comments, rewind and advanced filters.', 'PLUS',     999,  'USD', 1,  1, TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'plus-quarterly',   'Plus (3 months)', 'Everything in Plus, billed quarterly.',                          'PLUS',     2399, 'USD', 3,  2, TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'premium-monthly',  'Premium',         'A new auto-match every day, unlimited likes, global and incognito mode.', 'PREMIUM', 1999, 'USD', 1,  3, TRUE, NOW(), NOW(), 0),
 (gen_random_uuid(), 'premium-annual',   'Premium (yearly)','Everything in Premium, billed yearly.',                          'PREMIUM',  17999,'USD', 12, 4, TRUE, NOW(), NOW(), 0);
