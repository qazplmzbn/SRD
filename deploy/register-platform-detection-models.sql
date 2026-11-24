-- Run after deploying and verifying the matching precomputed features and weights.
-- Preserve any existing registrations and their administrator-controlled status.
INSERT INTO models
    (model_name, model_code, model_category, model_type, detection_type, supported_datasets, is_builtin)
SELECT seed.model_name, seed.model_code, 'detection', seed.model_type,
       seed.model_type, JSON_ARRAY(seed.dataset_key), 1
FROM (
    SELECT 'Emocc-Weibo' AS model_name, 'emocc-weibo' AS model_code, 'emoji' AS model_type, 'weibo' AS dataset_key
    UNION ALL SELECT 'Emocc-Reddit', 'emocc-reddit', 'emoji', 'reddit'
    UNION ALL SELECT 'Emocc-Bigdata', 'emocc-bigdata', 'emoji', 'bigdata'
    UNION ALL SELECT 'Emocc-SIGIR', 'emocc-sigir', 'emoji', 'sigir'
    UNION ALL SELECT 'FeaLearner-Weibo', 'fealearner-weibo', 'fealearner', 'weibo'
    UNION ALL SELECT 'FeaLearner-Reddit', 'fealearner-reddit', 'fealearner', 'reddit'
    UNION ALL SELECT 'FeaLearner-Bigdata', 'fealearner-bigdata', 'fealearner', 'bigdata'
    UNION ALL SELECT 'FeaLearner-SIGIR', 'fealearner-sigir', 'fealearner', 'sigir'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM models WHERE model_code = seed.model_code);