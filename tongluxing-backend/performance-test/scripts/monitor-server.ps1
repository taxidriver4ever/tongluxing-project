param([string]$SshKey = 'C:\Users\A2571\.ssh\tlx-server.pem', [string]$HostName = '43.138.233.211')
$remote = @'
cd /opt/tongluxing
echo '=== docker stats ==='
sudo -n docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}'
echo '=== mysql threads ==='
sudo -n docker compose exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW GLOBAL STATUS WHERE Variable_name IN (\"Threads_connected\",\"Threads_running\",\"Slow_queries\",\"Questions\");"'
echo '=== redis ==='
sudo -n docker compose exec -T redis redis-cli INFO stats | grep -E 'instantaneous_ops_per_sec|keyspace_hits|keyspace_misses|rejected_connections'
echo '=== backend warnings ==='
sudo -n docker compose logs --since=2m backend | grep -E 'HikariPool|OutOfMemory|ERROR' | tail -30 || true
'@
ssh -i $SshKey "ubuntu@$HostName" $remote
