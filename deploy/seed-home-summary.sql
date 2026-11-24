-- MySQL: ensure the rows refreshed by HomeService exist. Preserve existing rows.
-- A stale timestamp makes the next homepage request recompute the values.
INSERT INTO homepage_summary_stats
    (stat_key, stat_label, stat_category, stat_type, stat_order, updated_at)
VALUES
    ('knowledge_base_docs', '知识文档数', 'core_stats', 'count', 1, '2000-01-01'),
    ('total_archives', '档案用户数', 'core_stats', 'count', 2, '2000-01-01'),
    ('total_posts', '研究贴文数', 'core_stats', 'count', 3, '2000-01-01'),
    ('total_scales', '已完成测评数', 'core_stats', 'count', 4, '2000-01-01'),
    ('reports_generated', '已生成报告数', 'core_stats', 'count', 5, '2000-01-01'),
    ('risk_low_count', '低风险档案数', 'risk_distribution', 'count', 6, '2000-01-01'),
    ('risk_low_percentage', '低风险占比', 'risk_distribution', 'percentage', 7, '2000-01-01'),
    ('risk_medium_count', '中风险档案数', 'risk_distribution', 'count', 8, '2000-01-01'),
    ('risk_medium_percentage', '中风险占比', 'risk_distribution', 'percentage', 9, '2000-01-01'),
    ('risk_high_count', '高风险档案数', 'risk_distribution', 'count', 10, '2000-01-01'),
    ('risk_high_percentage', '高风险占比', 'risk_distribution', 'percentage', 11, '2000-01-01')
ON DUPLICATE KEY UPDATE stat_key = homepage_summary_stats.stat_key;