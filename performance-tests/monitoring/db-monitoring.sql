-- Oddiya Database Performance Monitoring and Query Analysis
-- This file contains SQL queries for monitoring database performance
-- and analyzing query execution patterns during performance tests

-- =============================================================================
-- CONNECTION AND SESSION MONITORING
-- =============================================================================

-- Monitor current database connections
SELECT 
    pid,
    usename,
    application_name,
    client_addr,
    client_hostname,
    state,
    query_start,
    state_change,
    waiting,
    query
FROM pg_stat_activity 
WHERE state != 'idle' 
ORDER BY query_start DESC;

-- Connection statistics by database
SELECT 
    datname,
    numbackends as active_connections,
    xact_commit as transactions_committed,
    xact_rollback as transactions_rolled_back,
    blks_read as blocks_read,
    blks_hit as blocks_hit,
    tup_returned as tuples_returned,
    tup_fetched as tuples_fetched,
    tup_inserted as tuples_inserted,
    tup_updated as tuples_updated,
    tup_deleted as tuples_deleted,
    conflicts,
    temp_files,
    temp_bytes,
    deadlocks,
    blk_read_time,
    blk_write_time
FROM pg_stat_database 
WHERE datname = 'oddiya';

-- Connection pool utilization (if using connection pooling)
SELECT 
    COUNT(*) as total_connections,
    COUNT(*) FILTER (WHERE state = 'active') as active_connections,
    COUNT(*) FILTER (WHERE state = 'idle') as idle_connections,
    COUNT(*) FILTER (WHERE state = 'idle in transaction') as idle_in_transaction,
    COUNT(*) FILTER (WHERE waiting = 't') as waiting_connections
FROM pg_stat_activity;

-- =============================================================================
-- QUERY PERFORMANCE MONITORING
-- =============================================================================

-- Top 20 slowest queries by mean execution time
SELECT 
    query,
    calls,
    total_time,
    mean_time,
    min_time,
    max_time,
    stddev_time,
    rows,
    100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
FROM pg_stat_statements 
ORDER BY mean_time DESC 
LIMIT 20;

-- Most frequently executed queries
SELECT 
    query,
    calls,
    total_time,
    mean_time,
    100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
FROM pg_stat_statements 
ORDER BY calls DESC 
LIMIT 20;

-- Queries with highest total execution time
SELECT 
    query,
    calls,
    total_time,
    mean_time,
    (total_time/sum(total_time) OVER()) * 100 AS percentage_of_total
FROM pg_stat_statements 
ORDER BY total_time DESC 
LIMIT 20;

-- Queries with poor cache hit ratios
SELECT 
    query,
    calls,
    shared_blks_hit,
    shared_blks_read,
    shared_blks_hit + shared_blks_read as total_blks,
    CASE 
        WHEN shared_blks_hit + shared_blks_read = 0 THEN 0
        ELSE 100.0 * shared_blks_hit / (shared_blks_hit + shared_blks_read)
    END AS hit_ratio
FROM pg_stat_statements
WHERE shared_blks_read > 0
ORDER BY hit_ratio ASC
LIMIT 20;

-- =============================================================================
-- TABLE AND INDEX PERFORMANCE
-- =============================================================================

-- Table usage statistics for Oddiya tables
SELECT 
    schemaname,
    tablename,
    seq_scan as sequential_scans,
    seq_tup_read as seq_tuples_read,
    idx_scan as index_scans,
    idx_tup_fetch as idx_tuples_fetched,
    n_tup_ins as tuples_inserted,
    n_tup_upd as tuples_updated,
    n_tup_del as tuples_deleted,
    n_tup_hot_upd as hot_updates,
    n_live_tup as live_tuples,
    n_dead_tup as dead_tuples,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables 
WHERE schemaname = 'public'
ORDER BY seq_scan + idx_scan DESC;

-- Index usage statistics
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan as index_scans,
    idx_tup_read as index_tuples_read,
    idx_tup_fetch as index_tuples_fetched
FROM pg_stat_user_indexes 
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

-- Unused or rarely used indexes
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public' AND idx_scan < 100
ORDER BY pg_relation_size(indexrelid) DESC;

-- Tables with high sequential scan ratios (potential missing indexes)
SELECT 
    schemaname,
    tablename,
    seq_scan,
    seq_tup_read,
    idx_scan,
    CASE 
        WHEN seq_scan + idx_scan = 0 THEN 0
        ELSE 100.0 * seq_scan / (seq_scan + idx_scan)
    END as seq_scan_ratio
FROM pg_stat_user_tables
WHERE schemaname = 'public' 
    AND seq_scan + idx_scan > 0
ORDER BY seq_scan_ratio DESC;

-- =============================================================================
-- SPECIFIC ODDIYA TABLE PERFORMANCE QUERIES
-- =============================================================================

-- User table performance metrics
SELECT 
    'users' as table_name,
    n_tup_ins as inserts,
    n_tup_upd as updates,
    n_tup_del as deletes,
    seq_scan as seq_scans,
    idx_scan as idx_scans,
    n_live_tup as live_tuples,
    n_dead_tup as dead_tuples,
    pg_size_pretty(pg_total_relation_size('users')) as total_size
FROM pg_stat_user_tables 
WHERE tablename = 'users';

-- Places table performance metrics
SELECT 
    'places' as table_name,
    n_tup_ins as inserts,
    n_tup_upd as updates,
    n_tup_del as deletes,
    seq_scan as seq_scans,
    idx_scan as idx_scans,
    n_live_tup as live_tuples,
    n_dead_tup as dead_tuples,
    pg_size_pretty(pg_total_relation_size('places')) as total_size
FROM pg_stat_user_tables 
WHERE tablename = 'places';

-- Travel Plans table performance metrics
SELECT 
    'travel_plans' as table_name,
    n_tup_ins as inserts,
    n_tup_upd as updates,
    n_tup_del as deletes,
    seq_scan as seq_scans,
    idx_scan as idx_scans,
    n_live_tup as live_tuples,
    n_dead_tup as dead_tuples,
    pg_size_pretty(pg_total_relation_size('travel_plans')) as total_size
FROM pg_stat_user_tables 
WHERE tablename = 'travel_plans';

-- =============================================================================
-- CACHE AND BUFFER PERFORMANCE
-- =============================================================================

-- Database cache hit ratio
SELECT 
    'cache_hit_ratio' as metric,
    CASE 
        WHEN blks_hit + blks_read = 0 THEN 100.0
        ELSE 100.0 * blks_hit / (blks_hit + blks_read)
    END as percentage
FROM pg_stat_database 
WHERE datname = 'oddiya';

-- Buffer cache usage by table
SELECT 
    c.relname as table_name,
    pg_size_pretty(pg_total_relation_size(c.oid)) as size,
    CASE 
        WHEN pg_relation_size(c.oid) = 0 THEN 0
        ELSE (pg_relation_size(c.oid) / 8192.0)
    END as pages,
    COALESCE(b.buffers, 0) as cached_pages,
    CASE 
        WHEN pg_relation_size(c.oid) = 0 THEN 0
        ELSE 100.0 * COALESCE(b.buffers, 0) / (pg_relation_size(c.oid) / 8192.0)
    END as cache_percentage
FROM pg_class c
LEFT JOIN (
    SELECT 
        c.oid,
        COUNT(*) as buffers
    FROM pg_class c
    INNER JOIN pg_buffercache b ON b.relfilenode = c.relfilenode
    INNER JOIN pg_database d ON (b.reldatabase = d.oid AND d.datname = current_database())
    GROUP BY c.oid
) b ON c.oid = b.oid
WHERE c.relkind IN ('r', 'i')
    AND c.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public')
ORDER BY COALESCE(b.buffers, 0) DESC;

-- =============================================================================
-- LOCK MONITORING
-- =============================================================================

-- Current locks and blocking queries
SELECT 
    blocked_locks.pid AS blocked_pid,
    blocked_activity.usename AS blocked_user,
    blocked_activity.query AS blocked_statement,
    blocked_activity.query_start,
    blocking_locks.pid AS blocking_pid,
    blocking_activity.usename AS blocking_user,
    blocking_activity.query AS blocking_statement,
    blocking_activity.query_start,
    blocked_locks.mode AS blocked_mode,
    blocking_locks.mode AS blocking_mode
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity 
    ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks 
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.DATABASE IS NOT DISTINCT FROM blocked_locks.DATABASE
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
    AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
    AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
    AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
    AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
    AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
    AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
    AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.GRANTED;

-- Lock summary by type
SELECT 
    locktype,
    mode,
    COUNT(*) as count
FROM pg_locks 
GROUP BY locktype, mode 
ORDER BY count DESC;

-- =============================================================================
-- VACUUM AND MAINTENANCE MONITORING
-- =============================================================================

-- Tables that need vacuuming
SELECT 
    schemaname,
    tablename,
    n_dead_tup as dead_tuples,
    n_live_tup as live_tuples,
    CASE 
        WHEN n_live_tup = 0 THEN 0
        ELSE n_dead_tup::float / n_live_tup::float * 100
    END as dead_tuple_percentage,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = 'public'
    AND n_dead_tup > 1000
ORDER BY dead_tuple_percentage DESC;

-- Tables that need analyzing
SELECT 
    schemaname,
    tablename,
    n_mod_since_analyze as modifications_since_analyze,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = 'public'
    AND n_mod_since_analyze > 1000
ORDER BY n_mod_since_analyze DESC;

-- =============================================================================
-- DISK I/O MONITORING
-- =============================================================================

-- I/O statistics by table
SELECT 
    schemaname,
    tablename,
    heap_blks_read as heap_blocks_read,
    heap_blks_hit as heap_blocks_hit,
    CASE 
        WHEN heap_blks_hit + heap_blks_read = 0 THEN 0
        ELSE 100.0 * heap_blks_hit / (heap_blks_hit + heap_blks_read)
    END as heap_hit_ratio,
    idx_blks_read as index_blocks_read,
    idx_blks_hit as index_blocks_hit,
    CASE 
        WHEN idx_blks_hit + idx_blks_read = 0 THEN 0
        ELSE 100.0 * idx_blks_hit / (idx_blks_hit + idx_blks_read)
    END as index_hit_ratio
FROM pg_statio_user_tables
WHERE schemaname = 'public'
ORDER BY heap_blks_read + idx_blks_read DESC;

-- =============================================================================
-- PERFORMANCE TEST SPECIFIC QUERIES
-- =============================================================================

-- Real-time query monitoring during performance tests
-- Run this query during tests to see active queries
SELECT 
    NOW() as current_time,
    pid,
    usename,
    application_name,
    client_addr,
    state,
    query_start,
    NOW() - query_start as query_duration,
    waiting,
    LEFT(query, 100) as query_preview
FROM pg_stat_activity 
WHERE state = 'active' 
    AND query NOT ILIKE '%pg_stat_activity%'
ORDER BY query_start ASC;

-- Database size growth monitoring
SELECT 
    pg_database.datname,
    pg_size_pretty(pg_database_size(pg_database.datname)) as size,
    pg_database_size(pg_database.datname) as size_bytes
FROM pg_database
WHERE datname = 'oddiya';

-- Connection count by application (performance test identification)
SELECT 
    application_name,
    client_addr,
    COUNT(*) as connection_count,
    COUNT(*) FILTER (WHERE state = 'active') as active_connections,
    COUNT(*) FILTER (WHERE state = 'idle') as idle_connections
FROM pg_stat_activity
WHERE application_name IS NOT NULL
GROUP BY application_name, client_addr
ORDER BY connection_count DESC;

-- =============================================================================
-- RESET STATISTICS (USE WITH CAUTION)
-- =============================================================================

-- Reset query statistics (run before performance tests for clean metrics)
-- SELECT pg_stat_reset();

-- Reset specific table statistics
-- SELECT pg_stat_reset_single_table_counters('users'::regclass);
-- SELECT pg_stat_reset_single_table_counters('places'::regclass);
-- SELECT pg_stat_reset_single_table_counters('travel_plans'::regclass);

-- =============================================================================
-- PERFORMANCE BASELINE QUERIES
-- =============================================================================

-- Create a baseline snapshot before performance tests
CREATE TABLE IF NOT EXISTS performance_baseline (
    snapshot_time TIMESTAMP DEFAULT NOW(),
    metric_name VARCHAR(100),
    metric_value NUMERIC,
    notes TEXT
);

-- Insert baseline metrics
INSERT INTO performance_baseline (metric_name, metric_value, notes)
SELECT 
    'total_connections',
    COUNT(*),
    'Total database connections at baseline'
FROM pg_stat_activity

UNION ALL

SELECT 
    'cache_hit_ratio',
    CASE 
        WHEN blks_hit + blks_read = 0 THEN 100.0
        ELSE 100.0 * blks_hit / (blks_hit + blks_read)
    END,
    'Database cache hit ratio at baseline'
FROM pg_stat_database 
WHERE datname = 'oddiya'

UNION ALL

SELECT 
    'total_queries',
    SUM(calls),
    'Total query count at baseline'
FROM pg_stat_statements

UNION ALL

SELECT 
    'avg_query_time',
    AVG(mean_time),
    'Average query execution time at baseline'
FROM pg_stat_statements;

-- View baseline data
SELECT * FROM performance_baseline 
ORDER BY snapshot_time DESC, metric_name;

-- =============================================================================
-- PERFORMANCE MONITORING VIEWS
-- =============================================================================

-- Create a view for easy performance monitoring during tests
CREATE OR REPLACE VIEW performance_monitor AS
SELECT 
    NOW() as check_time,
    'connections' as metric_type,
    COUNT(*) as current_value,
    COUNT(*) FILTER (WHERE state = 'active') as active_value,
    'Total and active connections' as description
FROM pg_stat_activity

UNION ALL

SELECT 
    NOW(),
    'cache_hit_ratio',
    CASE 
        WHEN blks_hit + blks_read = 0 THEN 100.0
        ELSE 100.0 * blks_hit / (blks_hit + blks_read)
    END,
    NULL,
    'Database cache hit percentage'
FROM pg_stat_database 
WHERE datname = 'oddiya'

UNION ALL

SELECT 
    NOW(),
    'locks_count',
    COUNT(*),
    COUNT(*) FILTER (WHERE NOT granted),
    'Total locks and blocking locks'
FROM pg_locks

UNION ALL

SELECT 
    NOW(),
    'slow_queries',
    COUNT(*),
    NULL,
    'Queries taking longer than 1 second'
FROM pg_stat_activity
WHERE state = 'active' 
    AND NOW() - query_start > INTERVAL '1 second'
    AND query NOT ILIKE '%pg_stat_activity%';

-- Query the performance monitor view
SELECT * FROM performance_monitor 
ORDER BY metric_type;