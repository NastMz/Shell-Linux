package com.osproject.shell.server.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemSamplerTest {

    @TempDir
    Path root;

    private final AtomicLong now = new AtomicLong(1_732_300_000_000L);
    private SystemSampler sampler;

    @BeforeEach
    void setUp() throws IOException {
        Fixtures.write(root, "proc/stat", Fixtures.PROC_STAT_1);
        Fixtures.write(root, "proc/meminfo", Fixtures.MEMINFO);
        Fixtures.write(root, "proc/loadavg", Fixtures.LOADAVG);
        Fixtures.write(root, "proc/uptime", Fixtures.UPTIME);
        Fixtures.write(root, "proc/net/dev", Fixtures.NET_DEV_1);
        Fixtures.write(root, "proc/mounts", Fixtures.MOUNTS);
        Fixtures.write(root, "proc/cpuinfo", Fixtures.CPUINFO);
        Fixtures.write(root, "proc/sys/kernel/hostname", "ubuntu-srv\n");
        Fixtures.write(root, "proc/sys/kernel/osrelease", "6.8.0-45-generic\n");
        Fixtures.write(root, "etc/os-release", Fixtures.OS_RELEASE);
        Fixtures.write(root, "etc/passwd", Fixtures.PASSWD);
        Fixtures.write(root, "proc/1/stat", Fixtures.PID1_STAT);
        Fixtures.write(root, "proc/1/status", Fixtures.PID1_STATUS);
        Fixtures.write(root, "proc/1/cmdline", Fixtures.PID1_CMDLINE);
        Fixtures.write(root, "proc/2/stat", Fixtures.KTHREAD_STAT);
        Fixtures.write(root, "proc/2/status", Fixtures.KTHREAD_STATUS);
        Fixtures.write(root, "proc/2/cmdline", "");
        Fixtures.write(root, "proc/4242/stat", Fixtures.WEIRD_STAT);
        Fixtures.write(root, "proc/4242/status", Fixtures.WEIRD_STATUS);
        Fixtures.write(root, "proc/4242/cmdline", "./app\0--flag\0");

        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
        DiskUsageProbe probe = mount -> mount.equals("/")
                ? Optional.of(new DiskUsageProbe.Usage(250_000_000_000L, 90_000_000_000L))
                : Optional.empty();
        sampler = new SystemSampler(new ProcFs(root), probe, 100, clock);
    }

    @Test
    void firstSampleHasAbsoluteValuesAndZeroRates() {
        sampler.sample();
        MetricsSnapshot m = sampler.metrics();

        assertEquals(1_732_300_000_000L, m.timestamp());
        assertEquals(10000, m.uptimeSeconds());
        assertEquals(List.of(0.42, 0.37, 0.30), m.loadAverage());
        assertEquals(3, m.processCount());
        assertEquals(new MetricsSnapshot.Cpu(0.0, List.of(0.0, 0.0)), m.cpu());
        assertEquals(new MetricsSnapshot.Memory(16624517120L, 5120000000L, 11504517120L, 2147479552L, 1073739776L),
                m.memory());
        assertEquals(List.of(new MetricsSnapshot.Disk("/", "/dev/nvme0n1p2", "ext4", 250_000_000_000L,
                90_000_000_000L)), m.disks());
        assertEquals(new MetricsSnapshot.Network(0.0, 0.0, 123456789L, 23456789L), m.network());
    }

    @Test
    void secondSampleComputesDeltas() throws IOException {
        sampler.sample();
        Fixtures.write(root, "proc/stat", Fixtures.PROC_STAT_2);
        Fixtures.write(root, "proc/net/dev", Fixtures.NET_DEV_2);
        Fixtures.write(root, "proc/1/stat", Fixtures.PID1_STAT_LATER);
        now.addAndGet(1000);

        sampler.sample();
        MetricsSnapshot m = sampler.metrics();

        assertEquals(new MetricsSnapshot.Cpu(50.0, List.of(75.0, 25.0)), m.cpu());
        assertEquals(20480.0, m.network().rxBytesPerSec());
        assertEquals(4096.0, m.network().txBytesPerSec());
        ProcessInfo init = sampler.processes().get(0);
        assertEquals(10.0, init.cpuPercent());
        assertEquals(0.0, sampler.processes().get(2).cpuPercent());
    }

    @Test
    void listsProcessesWithOwnerMemoryAndCommand() {
        sampler.sample();
        List<ProcessInfo> processes = sampler.processes();

        assertEquals(List.of(
                new ProcessInfo(1, 0, "systemd", "root", "S", 0.0, 13104 * 1024L, 0.08, 1,
                        1_732_290_000_050L, "/sbin/init splash"),
                new ProcessInfo(2, 0, "kthreadd", "root", "S", 0.0, 0, 0.0, 1, 1_732_290_000_050L, "[kthreadd]"),
                new ProcessInfo(4242, 1000, "my (weird) app", "kevin", "R", 0.0, 2000 * 1024L, 0.01, 4,
                        1_732_290_000_000L + 1_234_560L, "./app --flag")), processes);
    }

    @Test
    void processThatExitedIsSkipped() throws IOException {
        Fixtures.write(root, "proc/999/cmdline", "");

        sampler.sample();

        assertEquals(3, sampler.processes().size());
    }

    @Test
    void readsSystemInfo() {
        SystemInfo info = new SystemInfoReader(new ProcFs(root)).read();

        assertEquals("ubuntu-srv", info.hostname());
        assertEquals("Ubuntu 24.04.1 LTS", info.os());
        assertEquals("6.8.0-45-generic", info.kernel());
        assertEquals("Intel(R) Core(TM) i7-10750H CPU @ 2.60GHz", info.cpuModel());
        assertEquals(2, info.cpuCores());
        assertEquals(16624517120L, info.memoryTotalBytes());
        assertEquals(1_732_290_000_000L, info.bootTime());
    }
}
