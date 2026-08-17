-- 执行前设置：SET @candidate_count := 50/200/500/1000；然后 source 本文件。
SET @candidate_count := COALESCE(@candidate_count, 50);
UPDATE trip SET deleted=IF(id < 930000000000001000+@candidate_count,0,1),updated_at=NOW()
WHERE id BETWEEN 930000000000001000 AND 930000000000001999 AND remark='PERFORMANCE_TEST';
UPDATE trip_route r JOIN trip t ON t.id=r.trip_id SET r.deleted=t.deleted,r.updated_at=NOW()
WHERE t.id BETWEEN 930000000000001000 AND 930000000000001999 AND r.provider_type='PERFORMANCE_TEST';
UPDATE team m JOIN trip t ON t.id=m.trip_id SET m.deleted=t.deleted,m.updated_at=NOW()
WHERE t.id BETWEEN 930000000000001000 AND 930000000000001999 AND m.notice='PERFORMANCE_TEST';
SELECT @candidate_count AS requested,COUNT(*) AS active_candidates FROM trip
WHERE id BETWEEN 930000000000001000 AND 930000000000001999 AND deleted=0;
