#!/bin/bash

# Oddiya System Performance Monitoring Script
# This script monitors system resources during performance tests
# Usage: ./system-monitoring.sh [duration_in_seconds] [output_directory]

set -euo pipefail

# Default configuration
DEFAULT_DURATION=3600  # 1 hour
DEFAULT_OUTPUT_DIR="results/monitoring"
DEFAULT_INTERVAL=5     # 5 seconds

# Parse command line arguments
DURATION=${1:-$DEFAULT_DURATION}
OUTPUT_DIR=${2:-$DEFAULT_OUTPUT_DIR}
INTERVAL=${3:-$DEFAULT_INTERVAL}

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Timestamp for unique file names
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Output files
CPU_FILE="$OUTPUT_DIR/cpu_usage_$TIMESTAMP.csv"
MEMORY_FILE="$OUTPUT_DIR/memory_usage_$TIMESTAMP.csv"
DISK_FILE="$OUTPUT_DIR/disk_io_$TIMESTAMP.csv"
NETWORK_FILE="$OUTPUT_DIR/network_io_$TIMESTAMP.csv"
PROCESS_FILE="$OUTPUT_DIR/process_stats_$TIMESTAMP.csv"
JAVA_FILE="$OUTPUT_DIR/java_process_$TIMESTAMP.csv"
SYSTEM_FILE="$OUTPUT_DIR/system_stats_$TIMESTAMP.csv"
LOG_FILE="$OUTPUT_DIR/monitoring_log_$TIMESTAMP.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')] ERROR:${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
    echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')] WARNING:${NC} $1" | tee -a "$LOG_FILE"
}

# Check if required tools are available
check_dependencies() {
    local missing_tools=()
    
    if ! command -v vmstat &> /dev/null; then
        missing_tools+=("vmstat")
    fi
    
    if ! command -v iostat &> /dev/null; then
        missing_tools+=("iostat (sysstat)")
    fi
    
    if ! command -v sar &> /dev/null; then
        missing_tools+=("sar (sysstat)")
    fi
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log_error "Please install missing tools before running this script"
        
        # Provide installation instructions based on OS
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            log "Ubuntu/Debian: sudo apt-get install sysstat"
            log "CentOS/RHEL: sudo yum install sysstat"
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            log "macOS: brew install sysstat"
        fi
        
        exit 1
    fi
}

# Initialize CSV files with headers
initialize_csv_files() {
    # CPU usage header
    echo "timestamp,user,nice,system,iowait,steal,idle,load_1m,load_5m,load_15m" > "$CPU_FILE"
    
    # Memory usage header  
    echo "timestamp,total_mb,used_mb,free_mb,shared_mb,buff_cache_mb,available_mb,usage_percent,swap_total_mb,swap_used_mb,swap_free_mb" > "$MEMORY_FILE"
    
    # Disk I/O header
    echo "timestamp,device,reads_per_sec,writes_per_sec,kb_read_per_sec,kb_written_per_sec,avg_queue_size,avg_wait_time,util_percent" > "$DISK_FILE"
    
    # Network I/O header
    echo "timestamp,interface,rx_bytes_per_sec,tx_bytes_per_sec,rx_packets_per_sec,tx_packets_per_sec,rx_errors,tx_errors" > "$NETWORK_FILE"
    
    # Process stats header
    echo "timestamp,total_processes,running_processes,sleeping_processes,stopped_processes,zombie_processes" > "$PROCESS_FILE"
    
    # Java process header
    echo "timestamp,pid,cpu_percent,memory_percent,memory_mb,threads,gc_time,heap_used_mb,heap_max_mb,non_heap_used_mb" > "$JAVA_FILE"
    
    # System stats header
    echo "timestamp,uptime_hours,context_switches,interrupts,page_faults,tcp_connections" > "$SYSTEM_FILE"
}

# Monitor CPU usage
monitor_cpu() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Get CPU usage from vmstat
    local cpu_data=$(vmstat 1 2 | tail -1)
    local user=$(echo $cpu_data | awk '{print $13}')
    local nice=$(echo $cpu_data | awk '{print $14}')
    local system=$(echo $cpu_data | awk '{print $15}')
    local iowait=$(echo $cpu_data | awk '{print $16}')
    local steal=$(echo $cpu_data | awk '{print $17}')
    local idle=$(echo $cpu_data | awk '{print $18}')
    
    # Get load averages
    local load_avg=$(uptime | awk -F'load average:' '{print $2}' | tr -d ' ')
    local load_1m=$(echo $load_avg | cut -d',' -f1)
    local load_5m=$(echo $load_avg | cut -d',' -f2)
    local load_15m=$(echo $load_avg | cut -d',' -f3)
    
    echo "$timestamp,$user,$nice,$system,$iowait,$steal,$idle,$load_1m,$load_5m,$load_15m" >> "$CPU_FILE"
}

# Monitor memory usage
monitor_memory() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Parse /proc/meminfo for detailed memory stats
    local mem_total=$(grep MemTotal /proc/meminfo | awk '{print int($2/1024)}')
    local mem_free=$(grep MemFree /proc/meminfo | awk '{print int($2/1024)}')
    local mem_available=$(grep MemAvailable /proc/meminfo | awk '{print int($2/1024)}')
    local mem_shared=$(grep Shmem /proc/meminfo | awk '{print int($2/1024)}')
    local mem_buff_cache=$(($(grep Buffers /proc/meminfo | awk '{print int($2/1024)}') + $(grep Cached /proc/meminfo | awk '{print int($2/1024)}')))
    local mem_used=$((mem_total - mem_free - mem_buff_cache))
    local mem_usage_percent=$((mem_used * 100 / mem_total))
    
    # Swap information
    local swap_total=$(grep SwapTotal /proc/meminfo | awk '{print int($2/1024)}')
    local swap_free=$(grep SwapFree /proc/meminfo | awk '{print int($2/1024)}')
    local swap_used=$((swap_total - swap_free))
    
    echo "$timestamp,$mem_total,$mem_used,$mem_free,$mem_shared,$mem_buff_cache,$mem_available,$mem_usage_percent,$swap_total,$swap_used,$swap_free" >> "$MEMORY_FILE"
}

# Monitor disk I/O
monitor_disk_io() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Use iostat to get disk I/O statistics
    iostat -dx 1 2 | grep -E '^(sd|nvme|xvd)' | tail -n +2 | while read line; do
        local device=$(echo $line | awk '{print $1}')
        local reads_per_sec=$(echo $line | awk '{print $4}')
        local writes_per_sec=$(echo $line | awk '{print $5}')
        local kb_read_per_sec=$(echo $line | awk '{print $6}')
        local kb_written_per_sec=$(echo $line | awk '{print $7}')
        local avg_queue_size=$(echo $line | awk '{print $9}')
        local avg_wait_time=$(echo $line | awk '{print $10}')
        local util_percent=$(echo $line | awk '{print $14}')
        
        echo "$timestamp,$device,$reads_per_sec,$writes_per_sec,$kb_read_per_sec,$kb_written_per_sec,$avg_queue_size,$avg_wait_time,$util_percent" >> "$DISK_FILE"
    done
}

# Monitor network I/O
monitor_network() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Get network interface statistics
    for interface in $(ls /sys/class/net/ | grep -E '^(eth|ens|enp|wlan)'); do
        local rx_bytes_before=$(cat /sys/class/net/$interface/statistics/rx_bytes)
        local tx_bytes_before=$(cat /sys/class/net/$interface/statistics/tx_bytes)
        local rx_packets_before=$(cat /sys/class/net/$interface/statistics/rx_packets)
        local tx_packets_before=$(cat /sys/class/net/$interface/statistics/tx_packets)
        local rx_errors=$(cat /sys/class/net/$interface/statistics/rx_errors)
        local tx_errors=$(cat /sys/class/net/$interface/statistics/tx_errors)
        
        sleep 1
        
        local rx_bytes_after=$(cat /sys/class/net/$interface/statistics/rx_bytes)
        local tx_bytes_after=$(cat /sys/class/net/$interface/statistics/tx_bytes)
        local rx_packets_after=$(cat /sys/class/net/$interface/statistics/rx_packets)
        local tx_packets_after=$(cat /sys/class/net/$interface/statistics/tx_packets)
        
        local rx_bytes_per_sec=$((rx_bytes_after - rx_bytes_before))
        local tx_bytes_per_sec=$((tx_bytes_after - tx_bytes_before))
        local rx_packets_per_sec=$((rx_packets_after - rx_packets_before))
        local tx_packets_per_sec=$((tx_packets_after - tx_packets_before))
        
        echo "$timestamp,$interface,$rx_bytes_per_sec,$tx_bytes_per_sec,$rx_packets_per_sec,$tx_packets_per_sec,$rx_errors,$tx_errors" >> "$NETWORK_FILE"
    done
}

# Monitor process statistics
monitor_processes() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Get process counts by state
    local total_processes=$(ps aux | wc -l)
    local running_processes=$(ps -eo stat | grep -c '^R')
    local sleeping_processes=$(ps -eo stat | grep -c '^S')
    local stopped_processes=$(ps -eo stat | grep -c '^T')
    local zombie_processes=$(ps -eo stat | grep -c '^Z')
    
    echo "$timestamp,$total_processes,$running_processes,$sleeping_processes,$stopped_processes,$zombie_processes" >> "$PROCESS_FILE"
}

# Monitor Java processes (Oddiya application)
monitor_java_process() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Find Java processes related to Oddiya
    local java_pids=$(pgrep -f "java.*oddiya" || echo "")
    
    if [[ -n "$java_pids" ]]; then
        for pid in $java_pids; do
            # Get process CPU and memory usage
            local process_stats=$(ps -p $pid -o pid,pcpu,pmem,rss,nlwp --no-headers 2>/dev/null || echo "")
            
            if [[ -n "$process_stats" ]]; then
                local cpu_percent=$(echo $process_stats | awk '{print $2}')
                local memory_percent=$(echo $process_stats | awk '{print $3}')
                local memory_mb=$(echo $process_stats | awk '{print int($4/1024)}')
                local threads=$(echo $process_stats | awk '{print $5}')
                
                # Try to get JVM statistics if jstat is available
                local gc_time="N/A"
                local heap_used_mb="N/A"
                local heap_max_mb="N/A"
                local non_heap_used_mb="N/A"
                
                if command -v jstat &> /dev/null; then
                    local gc_stats=$(jstat -gc $pid 2>/dev/null || echo "")
                    if [[ -n "$gc_stats" ]]; then
                        # Parse jstat output (this is a simplified version)
                        heap_used_mb=$(echo $gc_stats | tail -1 | awk '{print int(($3+$4+$6+$8)/1024)}')
                        gc_time=$(echo $gc_stats | tail -1 | awk '{print $17}')
                    fi
                    
                    local heap_stats=$(jstat -gccapacity $pid 2>/dev/null || echo "")
                    if [[ -n "$heap_stats" ]]; then
                        heap_max_mb=$(echo $heap_stats | tail -1 | awk '{print int(($2+$8)/1024)}')
                    fi
                fi
                
                echo "$timestamp,$pid,$cpu_percent,$memory_percent,$memory_mb,$threads,$gc_time,$heap_used_mb,$heap_max_mb,$non_heap_used_mb" >> "$JAVA_FILE"
            fi
        done
    else
        # No Java process found
        echo "$timestamp,N/A,0,0,0,0,N/A,N/A,N/A,N/A" >> "$JAVA_FILE"
    fi
}

# Monitor system statistics
monitor_system() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # System uptime in hours
    local uptime_hours=$(uptime | awk '{print $3}' | tr -d ',')
    
    # Context switches and interrupts from /proc/stat
    local context_switches=$(grep ctxt /proc/stat | awk '{print $2}')
    local interrupts=$(grep intr /proc/stat | awk '{print $2}')
    
    # Page faults from /proc/vmstat
    local page_faults=$(grep pgfault /proc/vmstat | awk '{print $2}')
    
    # TCP connections
    local tcp_connections=$(netstat -tn 2>/dev/null | grep -c ESTABLISHED || echo "0")
    
    echo "$timestamp,$uptime_hours,$context_switches,$interrupts,$page_faults,$tcp_connections" >> "$SYSTEM_FILE"
}

# Signal handler for cleanup
cleanup() {
    log "Monitoring stopped by user signal"
    log "Generating summary report..."
    generate_summary
    exit 0
}

# Generate summary report
generate_summary() {
    local summary_file="$OUTPUT_DIR/monitoring_summary_$TIMESTAMP.txt"
    
    {
        echo "==============================================="
        echo "Oddiya Performance Monitoring Summary"
        echo "==============================================="
        echo "Monitoring Duration: $DURATION seconds"
        echo "Monitoring Interval: $INTERVAL seconds"
        echo "Start Time: $(head -2 "$CPU_FILE" | tail -1 | cut -d',' -f1)"
        echo "End Time: $(tail -1 "$CPU_FILE" | cut -d',' -f1)"
        echo ""
        
        # CPU Summary
        echo "CPU Usage Summary:"
        echo "-----------------"
        local avg_cpu=$(awk -F',' 'NR>1 {sum+=(100-$6)} END {print sum/(NR-1)}' "$CPU_FILE")
        local max_cpu=$(awk -F',' 'NR>1 {cpu=100-$6; if(cpu>max) max=cpu} END {print max}' "$CPU_FILE")
        echo "Average CPU Usage: ${avg_cpu}%"
        echo "Maximum CPU Usage: ${max_cpu}%"
        echo ""
        
        # Memory Summary
        echo "Memory Usage Summary:"
        echo "--------------------"
        local avg_memory=$(awk -F',' 'NR>1 {sum+=$7} END {print sum/(NR-1)}' "$MEMORY_FILE")
        local max_memory=$(awk -F',' 'NR>1 {if($7>max) max=$7} END {print max}' "$MEMORY_FILE")
        echo "Average Memory Usage: ${avg_memory}%"
        echo "Maximum Memory Usage: ${max_memory}%"
        echo ""
        
        # Load Average Summary
        echo "Load Average Summary:"
        echo "--------------------"
        local avg_load_1m=$(awk -F',' 'NR>1 {sum+=$7} END {print sum/(NR-1)}' "$CPU_FILE")
        local max_load_1m=$(awk -F',' 'NR>1 {if($7>max) max=$7} END {print max}' "$CPU_FILE")
        echo "Average 1-minute Load: $avg_load_1m"
        echo "Maximum 1-minute Load: $max_load_1m"
        echo ""
        
        # Files Generated
        echo "Generated Files:"
        echo "---------------"
        ls -la "$OUTPUT_DIR"/*_$TIMESTAMP.* | while read line; do
            echo "$line"
        done
        
    } > "$summary_file"
    
    log "Summary report generated: $summary_file"
}

# Main monitoring loop
run_monitoring() {
    local end_time=$((SECONDS + DURATION))
    local iteration=0
    
    log "Starting system monitoring for $DURATION seconds with $INTERVAL second intervals"
    log "Output directory: $OUTPUT_DIR"
    log "Press Ctrl+C to stop monitoring early"
    
    while [[ $SECONDS -lt $end_time ]]; do
        iteration=$((iteration + 1))
        
        # Run all monitoring functions
        monitor_cpu &
        monitor_memory &
        monitor_processes &
        monitor_java_process &
        monitor_system &
        
        # Only run disk and network monitoring every 3 iterations to reduce overhead
        if [[ $((iteration % 3)) -eq 0 ]]; then
            monitor_disk_io &
            monitor_network &
        fi
        
        # Wait for all background processes to complete
        wait
        
        # Progress indicator
        local progress=$((((SECONDS * 100) / DURATION)))
        printf "\r${BLUE}Monitoring progress: %3d%% [%s] Iteration: %d${NC}" \
            $progress \
            "$(printf '%*s' $((progress/5)) '' | tr ' ' '=')" \
            $iteration
        
        # Sleep for the remaining interval time
        sleep $INTERVAL
    done
    
    echo # New line after progress indicator
    log "Monitoring completed successfully"
    generate_summary
}

# Alert function for threshold violations
check_alerts() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Check CPU usage threshold (80%)
    local current_cpu=$(vmstat 1 2 | tail -1 | awk '{print 100-$15}')
    if [[ $current_cpu -gt 80 ]]; then
        log_warning "High CPU usage detected: ${current_cpu}%"
    fi
    
    # Check memory usage threshold (85%)
    local current_memory=$(free | grep Mem | awk '{printf "%.0f", $3/$2 * 100.0}')
    if [[ $current_memory -gt 85 ]]; then
        log_warning "High memory usage detected: ${current_memory}%"
    fi
    
    # Check disk usage threshold (90%)
    df -h | grep -E '^/dev' | while read line; do
        local usage=$(echo $line | awk '{print $5}' | tr -d '%')
        local mount=$(echo $line | awk '{print $6}')
        if [[ $usage -gt 90 ]]; then
            log_warning "High disk usage detected: ${usage}% on $mount"
        fi
    done
}

# Print usage information
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Monitor system resources during Oddiya performance tests.

OPTIONS:
    -d, --duration SECONDS    Monitoring duration in seconds (default: $DEFAULT_DURATION)
    -o, --output-dir DIR     Output directory for monitoring files (default: $DEFAULT_OUTPUT_DIR)
    -i, --interval SECONDS   Monitoring interval in seconds (default: $DEFAULT_INTERVAL)
    -h, --help              Show this help message

EXAMPLES:
    $0                              # Monitor for 1 hour with default settings
    $0 -d 1800 -i 10               # Monitor for 30 minutes with 10-second intervals
    $0 --duration 3600 --output-dir /tmp/monitoring  # Custom duration and output directory

NOTES:
    - Requires sysstat package (vmstat, iostat, sar)
    - Generates CSV files for each monitored metric
    - Use Ctrl+C to stop monitoring early
    - Summary report generated at the end
EOF
}

# Parse command line options
parse_options() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -d|--duration)
                DURATION="$2"
                shift 2
                ;;
            -o|--output-dir)
                OUTPUT_DIR="$2"
                shift 2
                ;;
            -i|--interval)
                INTERVAL="$2"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
    done
}

# Main execution
main() {
    # Parse command line options if provided
    if [[ $# -gt 0 ]]; then
        parse_options "$@"
    fi
    
    # Setup signal handlers
    trap cleanup SIGINT SIGTERM
    
    # Check dependencies
    check_dependencies
    
    # Initialize monitoring
    initialize_csv_files
    
    # Start monitoring
    run_monitoring
    
    log "System monitoring completed successfully"
    log "All monitoring files saved in: $OUTPUT_DIR"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi