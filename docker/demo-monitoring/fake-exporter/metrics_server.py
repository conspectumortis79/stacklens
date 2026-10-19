"""
StackLens Demo Metrics Exporter
Exposes simulated Prometheus metrics for CPU, Memory, Disk, Network etc.
Metrics update every 5 seconds with realistic random walks.

Load-cycle simulation (repeats every ~2 minutes, 36 ticks × 3 s):
  Ticks  0-11  ( 36 s) – NORMAL   : low load, all metrics in safe zone
  Ticks 12-17  ( 18 s) – RISING   : load climbs, metrics approach thresholds
  Ticks 18-27  ( 30 s) – INCIDENT : CPU/Memory breach alert thresholds (>80 %)
  Ticks 28-35  ( 24 s) – RECOVERY : metrics drop back to safe levels

Alert rules to demo:
  CPU average > 80 %   (fires during INCIDENT phase)
  Memory %    > 75 %   (fires during INCIDENT phase)
  CPU temp    > 80 °C  (fires during INCIDENT phase)
"""
import math
import time
import random
from prometheus_client import start_http_server, Gauge

# ── Metric definitions ────────────────────────────────────────────────────────

cpu_usage        = Gauge('demo_cpu_usage_percent',          'CPU usage per core (%)',               ['core'])
mem_usage_pct    = Gauge('demo_memory_usage_percent',       'Memory usage (%)')
mem_used         = Gauge('demo_memory_used_bytes',          'Memory used (bytes)')
mem_total_metric = Gauge('demo_memory_total_bytes',         'Memory total (bytes)')
disk_read        = Gauge('demo_disk_read_bytes_per_sec',    'Disk read throughput (bytes/s)',        ['device'])
disk_write       = Gauge('demo_disk_write_bytes_per_sec',   'Disk write throughput (bytes/s)',       ['device'])
disk_used        = Gauge('demo_disk_used_bytes',            'Disk space used (bytes)',               ['mountpoint'])
disk_total       = Gauge('demo_disk_total_bytes',           'Disk space total (bytes)',              ['mountpoint'])
net_rx           = Gauge('demo_network_receive_bytes_per_sec',  'Network receive throughput (bytes/s)',  ['iface'])
net_tx           = Gauge('demo_network_transmit_bytes_per_sec', 'Network transmit throughput (bytes/s)', ['iface'])
load1            = Gauge('demo_system_load1',               'System load average 1 min')
load5            = Gauge('demo_system_load5',               'System load average 5 min')
load15           = Gauge('demo_system_load15',              'System load average 15 min')
tcp_connections  = Gauge('demo_tcp_connections_active',     'Active TCP connections')
cpu_temp         = Gauge('demo_cpu_temperature_celsius',    'CPU temperature (°C)')

# ── Load-cycle phases ─────────────────────────────────────────────────────────
# Each phase is defined as (duration_in_ticks, cpu_base, mem_base, temp_base,
#                           net_rx_base, conn_base, load_base)
# All "base" values are the target midpoints; random noise is added on top.

CYCLE_TICKS = 36          # total ticks per full cycle (36 × 5 s = 180 s ≈ 3 min)

# (end_tick, cpu_base, mem_base, temp_base, net_rx_base_MB, conn_base, load_base)
PHASES = [
    (12, 28.0,  45.0, 48.0,  8.0,  45.0, 1.0),   # NORMAL    tick  0-11
    (18, 55.0,  62.0, 62.0, 30.0,  90.0, 2.5),   # RISING    tick 12-17
    (28, 88.0,  83.0, 85.0, 90.0, 180.0, 6.5),   # INCIDENT  tick 18-27
    (36, 35.0,  50.0, 52.0, 12.0,  55.0, 1.2),   # RECOVERY  tick 28-35
]

# ── Random-walk state ─────────────────────────────────────────────────────────

class Walk:
    """Clamped random walk that can be nudged toward a target each tick."""
    def __init__(self, start, lo, hi, step):
        self.v, self.lo, self.hi, self.step = start, lo, hi, step

    def next(self, extra=0.0, target=None, pull=0.18):
        """Advance one step.  If *target* is given, gently pull toward it."""
        nudge = 0.0
        if target is not None:
            nudge = (target - self.v) * pull
        self.v = max(self.lo, min(self.hi, self.v + random.uniform(-self.step, self.step) + extra + nudge))
        return self.v

NUM_CORES = 4

cores      = [Walk(random.uniform(15, 40), 2.0,  99.0,  6.0)  for _ in range(NUM_CORES)]
mem_w      = Walk(random.uniform(40, 55),  18.0, 97.0,  2.5)
d_read_w   = Walk(random.uniform(5e6, 50e6),  1e5, 200e6, 12e6)
d_write_w  = Walk(random.uniform(1e6, 20e6),  5e4, 100e6,  6e6)
n_rx_w     = Walk(random.uniform(5e6, 20e6),  1e4, 200e6, 10e6)
n_tx_w     = Walk(random.uniform(5e5, 10e6),  1e4,  80e6,  5e6)
load_w     = Walk(random.uniform(0.8, 2.0),  0.05,  16.0,  0.20)
conn_w     = Walk(random.uniform(20, 60),     3.0, 500.0,  6.0)
temp_w     = Walk(random.uniform(42, 55),    28.0,  98.0,  1.5)

DISK1_TOTAL  = 500  * 1024**3   # 500 GB
DISK2_TOTAL  = 2048 * 1024**3   # 2 TB
MEM_TOTAL    = 16   * 1024**3   # 16 GB

disk1_used = random.uniform(0.35, 0.65) * DISK1_TOTAL
disk2_used = random.uniform(0.50, 0.75) * DISK2_TOTAL

tick = 0  # global tick counter

# ── Phase helper ──────────────────────────────────────────────────────────────

def current_phase_targets():
    """Return the base targets for the current tick's phase."""
    pos = tick % CYCLE_TICKS
    for end_tick, cpu_b, mem_b, temp_b, net_rx_b, conn_b, load_b in PHASES:
        if pos < end_tick:
            return cpu_b, mem_b, temp_b, net_rx_b * 1e6, conn_b, load_b
    # fallback (should not happen)
    return PHASES[0][1:]

# ── Update loop ───────────────────────────────────────────────────────────────

def update():
    global disk1_used, disk2_used, tick

    cpu_b, mem_b, temp_b, net_rx_b, conn_b, load_b = current_phase_targets()

    # ── CPU ──────────────────────────────────────────────────────────────────
    # Each core is pulled toward cpu_b with jitter; core3 always runs slightly hotter
    offsets = [0.0, -4.0, 3.0, 8.0]
    for i, core in enumerate(cores):
        v = core.next(target=cpu_b + offsets[i])
        cpu_usage.labels(core=f'core{i}').set(round(v, 2))

    # ── Memory ───────────────────────────────────────────────────────────────
    mp = mem_w.next(target=mem_b)
    mem_usage_pct.set(round(mp, 2))
    mem_used.set(round(mp / 100 * MEM_TOTAL))
    mem_total_metric.set(MEM_TOTAL)

    # ── Disk I/O ─────────────────────────────────────────────────────────────
    # Scale disk I/O with CPU load
    io_scale = max(0.2, cpu_b / 60.0)
    disk_read.labels(device='sda').set(round(d_read_w.next(target=40e6 * io_scale)))
    disk_write.labels(device='sda').set(round(d_write_w.next(target=15e6 * io_scale)))

    # Disk space – slowly grows regardless of phase
    if random.random() < 0.20:
        disk1_used += random.uniform(100_000, 500_000)
    if random.random() < 0.10:
        disk2_used += random.uniform(500_000, 2_000_000)
    disk_used.labels(mountpoint='/').set(round(disk1_used))
    disk_used.labels(mountpoint='/data').set(round(disk2_used))
    disk_total.labels(mountpoint='/').set(DISK1_TOTAL)
    disk_total.labels(mountpoint='/data').set(DISK2_TOTAL)

    # ── Network ───────────────────────────────────────────────────────────────
    net_rx.labels(iface='eth0').set(round(n_rx_w.next(target=net_rx_b)))
    net_tx.labels(iface='eth0').set(round(n_tx_w.next(target=net_rx_b * 0.25)))

    # ── System load (correlated with cpu_b) ───────────────────────────────────
    l1 = load_w.next(target=load_b)
    load1.set(round(l1, 2))
    load5.set(round(max(0.05, l1 + random.uniform(-0.40, 0.40)), 2))
    load15.set(round(max(0.05, l1 + random.uniform(-0.70, 0.70)), 2))

    # ── TCP connections ───────────────────────────────────────────────────────
    conn_w.next(target=conn_b)
    tcp_connections.set(round(conn_w.v))

    # ── CPU temperature ───────────────────────────────────────────────────────
    cpu_temp.set(round(temp_w.next(target=temp_b), 1))

    tick += 1


def phase_name():
    pos = tick % CYCLE_TICKS
    for end_tick, *_ in PHASES:
        if pos < end_tick:
            idx = PHASES.index(next(p for p in PHASES if p[0] == end_tick))
            return ['NORMAL', 'RISING', 'INCIDENT', 'RECOVERY'][idx]
    return 'NORMAL'


if __name__ == '__main__':
    print('StackLens Demo Metrics Exporter – http://0.0.0.0:9100/metrics')
    print(f'Load cycle: {CYCLE_TICKS * 3}s per cycle  '
          f'| INCIDENT at tick 18-27 (CPU ~88%, MEM ~83%, TEMP ~85°C)')
    start_http_server(9100)
    update()
    while True:
        time.sleep(3)
        pos = tick % CYCLE_TICKS
        if pos == 0:
            print(f'[tick {tick:4d}] New cycle start')
        elif pos == 18:
            avg = sum(c.v for c in cores) / NUM_CORES
            print(f'[tick {tick:4d}] INCIDENT phase  cpu_avg={avg:.1f}%  mem={mem_w.v:.1f}%  temp={temp_w.v:.1f}°C')
        update()
