package com.osproject.shell.server.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Samples {@code /proc} at a fixed rate and keeps the latest immutable snapshot.
 *
 * <p>CPU percentages (total, per core and per process) and network rates are computed
 * from the difference between two consecutive samples, so they are zero in the very
 * first sample.</p>
 */
public final class SystemSampler implements MetricsSource, AutoCloseable {

    /** Default sampling period. */
    public static final long PERIOD_MILLIS = 1000;

    private static final Logger LOG = Logger.getLogger(SystemSampler.class.getName());
    private static final long DEFAULT_CLOCK_TICKS = 100;

    private final ProcFs procFs;
    private final DiskUsageProbe diskProbe;
    private final long clockTicksPerSecond;
    private final Clock clock;
    private ScheduledExecutorService scheduler;

    private CpuStat previousCpu;
    private NetCounters previousNet;
    private long previousMillis;
    private Map<Long, ProcessTicks> previousProcesses = Map.of();
    private boolean failureLogged;

    private volatile Sample latest;

    private record ProcessTicks(long startTicks, long cpuTicks) {
    }

    private record Sample(MetricsSnapshot metrics, List<ProcessInfo> processes) {
    }

    /**
     * Creates a sampler.
     *
     * @param procFs              access to {@code /proc} and {@code /etc}
     * @param diskProbe           filesystem size lookup
     * @param clockTicksPerSecond kernel {@code USER_HZ} (see {@link #detectClockTicks()})
     * @param clock               wall clock for timestamps and rates
     */
    public SystemSampler(ProcFs procFs, DiskUsageProbe diskProbe, long clockTicksPerSecond, Clock clock) {
        this.procFs = procFs;
        this.diskProbe = diskProbe;
        this.clockTicksPerSecond = clockTicksPerSecond;
        this.clock = clock;
        this.latest = new Sample(emptySnapshot(clock.millis()), List.of());
    }

    /**
     * Creates a sampler for the running Linux system.
     *
     * @return the sampler (not started)
     */
    public static SystemSampler forSystem() {
        return new SystemSampler(ProcFs.system(), DiskUsageProbe.fileStore(), detectClockTicks(),
                Clock.systemUTC());
    }

    /**
     * Asks {@code getconf CLK_TCK} for the kernel clock tick rate.
     *
     * @return the tick rate, or 100 (the Linux default) when it cannot be determined
     */
    public static long detectClockTicks() {
        try {
            Process process = new ProcessBuilder("getconf", "CLK_TCK").redirectErrorStream(true).start();
            try (InputStream out = process.getInputStream()) {
                String text = new String(out.readAllBytes(), StandardCharsets.US_ASCII).trim();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    long ticks = Long.parseLong(text);
                    return ticks > 0 ? ticks : DEFAULT_CLOCK_TICKS;
                }
            }
            process.destroyForcibly();
        } catch (IOException | NumberFormatException e) {
            return DEFAULT_CLOCK_TICKS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return DEFAULT_CLOCK_TICKS;
    }

    /**
     * Takes a first sample immediately and then one every {@link #PERIOD_MILLIS}.
     */
    public synchronized void start() {
        if (scheduler != null) {
            throw new IllegalStateException("Sampler already started");
        }
        sample();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "system-sampler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::sampleSafely, PERIOD_MILLIS, PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public MetricsSnapshot metrics() {
        return latest.metrics();
    }

    @Override
    public List<ProcessInfo> processes() {
        return latest.processes();
    }

    private void sampleSafely() {
        try {
            sample();
        } catch (RuntimeException e) {
            if (!failureLogged) {
                LOG.log(Level.WARNING, "Resource sampling failed: " + e, e);
                failureLogged = true;
            }
        }
    }

    /**
     * Takes one sample and publishes it. Called by the scheduler; public for tests.
     */
    public synchronized void sample() {
        long now = clock.millis();
        Optional<CpuStat> cpuStat = procFs.read("proc/stat").map(CpuStat::parse);
        MemInfo memInfo = procFs.read("proc/meminfo").map(MemInfo::parse).orElse(new MemInfo(0, 0, 0, 0));
        NetCounters net = procFs.read("proc/net/dev").map(NetCounters::parse).orElse(NetCounters.ZERO);

        MetricsSnapshot.Cpu cpu = cpuStat.map(this::cpuUsage).orElse(new MetricsSnapshot.Cpu(0, List.of()));
        long cpuTotalDelta = cpuStat.filter(s -> previousCpu != null)
                .map(s -> s.total().total() - previousCpu.total().total()).orElse(0L);
        int cores = Math.max(1, cpuStat.map(s -> s.cores().size()).orElse(1));
        long bootSeconds = cpuStat.map(CpuStat::bootTimeSeconds).orElse(0L);
        List<ProcessInfo> processes = sampleProcesses(cpuTotalDelta, cores, bootSeconds, memInfo.totalBytes());

        double[] rates = previousNet == null ? new double[] {0, 0} : net.ratesSince(previousNet, now - previousMillis);
        MetricsSnapshot snapshot = new MetricsSnapshot(
                now,
                procFs.read("proc/uptime").map(SystemFiles::uptimeSeconds).orElse(0L),
                procFs.read("proc/loadavg").map(SystemFiles::loadAverage).orElse(List.of(0.0, 0.0, 0.0)),
                processes.size(),
                cpu,
                memory(memInfo),
                disks(),
                new MetricsSnapshot.Network(round(rates[0], 1), round(rates[1], 1), net.rxBytes(), net.txBytes()));

        previousCpu = cpuStat.orElse(null);
        previousNet = net;
        previousMillis = now;
        latest = new Sample(snapshot, List.copyOf(processes));
    }

    private MetricsSnapshot.Cpu cpuUsage(CpuStat current) {
        if (previousCpu == null) {
            return new MetricsSnapshot.Cpu(0, current.cores().stream().map(c -> 0.0).toList());
        }
        List<Double> perCore = new ArrayList<>(current.cores().size());
        for (int i = 0; i < current.cores().size(); i++) {
            CpuTimes before = i < previousCpu.cores().size() ? previousCpu.cores().get(i) : current.cores().get(i);
            perCore.add(round(current.cores().get(i).usagePercentSince(before), 1));
        }
        return new MetricsSnapshot.Cpu(round(current.total().usagePercentSince(previousCpu.total()), 1), perCore);
    }

    private static MetricsSnapshot.Memory memory(MemInfo info) {
        return new MetricsSnapshot.Memory(info.totalBytes(), info.totalBytes() - info.availableBytes(),
                info.availableBytes(), info.swapTotalBytes(),
                Math.max(0, info.swapTotalBytes() - info.swapFreeBytes()));
    }

    private List<MetricsSnapshot.Disk> disks() {
        List<MetricsSnapshot.Disk> disks = new ArrayList<>();
        for (MountEntry entry : procFs.read("proc/mounts").map(MountEntry::parseDisks).orElse(List.of())) {
            diskProbe.probe(entry.mount())
                    .filter(usage -> usage.totalBytes() > 0)
                    .ifPresent(usage -> disks.add(new MetricsSnapshot.Disk(entry.mount(), entry.device(),
                            entry.fsType(), usage.totalBytes(), usage.usedBytes())));
        }
        return disks;
    }

    private List<ProcessInfo> sampleProcesses(long cpuTotalDelta, int cores, long bootSeconds, long memTotal) {
        Map<Long, String> userNames = procFs.read("etc/passwd").map(SystemFiles::userNames).orElse(Map.of());
        Map<Long, ProcessTicks> ticks = new HashMap<>();
        List<ProcessInfo> processes = new ArrayList<>();
        for (long pid : procFs.pids()) {
            Optional<ProcessStat> stat = procFs.read("proc/" + pid + "/stat").flatMap(SystemSampler::parseStat);
            if (stat.isEmpty()) {
                continue; // the process exited while being listed
            }
            ProcessStat s = stat.get();
            ProcessStatus status = procFs.read("proc/" + pid + "/status").map(ProcessStatus::parse)
                    .orElse(new ProcessStatus(-1, 0));
            String command = ProcessStatus.commandLine(procFs.read("proc/" + pid + "/cmdline").orElse(""), s.name());
            ticks.put(pid, new ProcessTicks(s.startTicks(), s.cpuTicks()));
            processes.add(new ProcessInfo(pid, s.ppid(), s.name(),
                    status.uid() < 0 ? "?" : userNames.getOrDefault(status.uid(), Long.toString(status.uid())),
                    s.state(),
                    round(processCpuPercent(s, cpuTotalDelta, cores), 1),
                    status.rssBytes(),
                    memTotal > 0 ? round(status.rssBytes() * 100.0 / memTotal, 2) : 0.0,
                    s.threads(),
                    bootSeconds * 1000 + s.startTicks() * 1000 / clockTicksPerSecond,
                    command));
        }
        previousProcesses = ticks;
        return processes;
    }

    private double processCpuPercent(ProcessStat stat, long cpuTotalDelta, int cores) {
        ProcessTicks before = previousProcesses.get(stat.pid());
        if (before == null || before.startTicks() != stat.startTicks() || cpuTotalDelta <= 0) {
            return 0.0;
        }
        return processCpuPercent(stat.cpuTicks() - before.cpuTicks(), cpuTotalDelta, cores);
    }

    /**
     * Computes a process CPU percentage relative to one core, as {@code top} does.
     *
     * @param processTicksDelta ticks the process consumed between two samples
     * @param cpuTotalDelta     ticks elapsed on all cores together (aggregate {@code cpu} line)
     * @param cores             number of logical cores
     * @return the percentage, 0 when no time elapsed
     */
    public static double processCpuPercent(long processTicksDelta, long cpuTotalDelta, int cores) {
        if (cpuTotalDelta <= 0 || processTicksDelta <= 0) {
            return 0.0;
        }
        double ticksPerCore = (double) cpuTotalDelta / Math.max(1, cores);
        return processTicksDelta / ticksPerCore * 100.0;
    }

    private static Optional<ProcessStat> parseStat(String content) {
        try {
            return Optional.of(ProcessStat.parse(content));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static MetricsSnapshot emptySnapshot(long now) {
        return new MetricsSnapshot(now, 0, List.of(0.0, 0.0, 0.0), 0, new MetricsSnapshot.Cpu(0, List.of()),
                new MetricsSnapshot.Memory(0, 0, 0, 0, 0), List.of(), new MetricsSnapshot.Network(0, 0, 0, 0));
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
