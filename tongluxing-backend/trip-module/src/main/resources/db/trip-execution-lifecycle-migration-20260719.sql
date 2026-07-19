-- 行程执行闭环状态迁移；可重复执行。
-- 旧状态兼容映射：ONGOING -> RUNNING，ENDED -> FINISHED。
update trip set status = 'RUNNING' where status = 'ONGOING' and deleted = 0;
update trip set status = 'FINISHED' where status = 'ENDED' and deleted = 0;
