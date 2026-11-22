package com.osproject.shell.server.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParsersTest {

    @Test
    void parsesProcStat() {
        CpuStat stat = CpuStat.parse(Fixtures.PROC_STAT_1);

        assertEquals(2, stat.cores().size());
        assertEquals(1732290000L, stat.bootTimeSeconds());
        assertEquals(46828483L + 16683L, stat.total().idle());
        assertEquals(10132153L + 290696 + 3084719 + 46828483 + 16683 + 25195, stat.total().total());
    }

    @Test
    void procStatWithoutCpuLineIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CpuStat.parse("intr 1 2 3\n"));
    }

    @Test
    void computesCpuUsageDeltas() {
        CpuStat before = CpuStat.parse(Fixtures.PROC_STAT_1);
        CpuStat after = CpuStat.parse(Fixtures.PROC_STAT_2);

        assertEquals(50.0, after.total().usagePercentSince(before.total()), 1e-9);
        assertEquals(75.0, after.cores().get(0).usagePercentSince(before.cores().get(0)), 1e-9);
        assertEquals(25.0, after.cores().get(1).usagePercentSince(before.cores().get(1)), 1e-9);
        assertEquals(0.0, before.total().usagePercentSince(before.total()));
    }

    @Test
    void parsesMeminfoInBytes() {
        MemInfo info = MemInfo.parse(Fixtures.MEMINFO);

        assertEquals(16624517120L, info.totalBytes());
        assertEquals(11504517120L, info.availableBytes());
        assertEquals(2147479552L, info.swapTotalBytes());
        assertEquals(1073739776L, info.swapFreeBytes());
    }

    @Test
    void estimatesAvailableMemoryOnOldKernels() {
        MemInfo info = MemInfo.parse("MemTotal: 1000 kB\nMemFree: 100 kB\nBuffers: 50 kB\nCached: 250 kB\n");

        assertEquals(400 * 1024, info.availableBytes());
    }

    @Test
    void parsesNetDevWithoutLoopback() {
        NetCounters counters = NetCounters.parse(Fixtures.NET_DEV_1);

        assertEquals(120000000L + 3456789L, counters.rxBytes());
        assertEquals(20000000L + 3456789L, counters.txBytes());
    }

    @Test
    void computesNetworkRates() {
        NetCounters before = NetCounters.parse(Fixtures.NET_DEV_1);
        NetCounters after = NetCounters.parse(Fixtures.NET_DEV_2);

        assertArrayEquals(new double[] {20480.0, 4096.0}, after.ratesSince(before, 1000), 1e-9);
        assertArrayEquals(new double[] {10240.0, 2048.0}, after.ratesSince(before, 2000), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0}, before.ratesSince(after, 1000), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.0}, after.ratesSince(before, 0), 1e-9);
    }

    @Test
    void keepsOnlyDiskMounts() {
        List<MountEntry> disks = MountEntry.parseDisks(Fixtures.MOUNTS);

        assertEquals(List.of(
                new MountEntry("/dev/nvme0n1p2", "/", "ext4"),
                new MountEntry("/dev/nvme0n1p1", "/boot/efi", "vfat"),
                new MountEntry("/dev/sdb1", "/media/kevin/My Disk", "ntfs3"),
                new MountEntry("overlay", "/var/lib/docker/overlay2/abc/merged", "overlay")), disks);
    }

    @Test
    void parsesProcessStat() {
        ProcessStat stat = ProcessStat.parse(Fixtures.PID1_STAT);

        assertEquals(new ProcessStat(1, "systemd", "S", 0, 500, 1, 5), stat);
    }

    @Test
    void parsesProcessStatWithParenthesesInName() {
        ProcessStat stat = ProcessStat.parse(Fixtures.WEIRD_STAT);

        assertEquals(new ProcessStat(4242, "my (weird) app", "R", 1000, 2000, 4, 123456), stat);
    }

    @Test
    void rejectsTruncatedProcessStat() {
        assertThrows(IllegalArgumentException.class, () -> ProcessStat.parse("12 (bash) S 1 2"));
        assertThrows(IllegalArgumentException.class, () -> ProcessStat.parse("garbage"));
    }

    @Test
    void parsesProcessStatus() {
        assertEquals(new ProcessStatus(0, 13104 * 1024L), ProcessStatus.parse(Fixtures.PID1_STATUS));
        assertEquals(new ProcessStatus(0, 0), ProcessStatus.parse(Fixtures.KTHREAD_STATUS));
    }

    @Test
    void formatsCommandLines() {
        assertEquals("/sbin/init splash", ProcessStatus.commandLine(Fixtures.PID1_CMDLINE, "systemd"));
        assertEquals("[kthreadd]", ProcessStatus.commandLine("", "kthreadd"));
    }

    @Test
    void parsesLoadAverageAndUptime() {
        assertEquals(List.of(0.42, 0.37, 0.30), SystemFiles.loadAverage(Fixtures.LOADAVG));
        assertEquals(10000L, SystemFiles.uptimeSeconds(Fixtures.UPTIME));
    }

    @Test
    void parsesOsRelease() {
        assertEquals(Optional.of("Ubuntu 24.04.1 LTS"), SystemFiles.osPrettyName(Fixtures.OS_RELEASE));
        assertEquals(Optional.of("Alpine 3.20"), SystemFiles.osPrettyName("NAME=Alpine\nVERSION='3.20'\n"));
        assertEquals(Optional.empty(), SystemFiles.osPrettyName("# nothing\n"));
    }

    @Test
    void parsesCpuinfo() {
        assertEquals(Optional.of("Intel(R) Core(TM) i7-10750H CPU @ 2.60GHz"), SystemFiles.cpuModel(Fixtures.CPUINFO));
        assertEquals(2, SystemFiles.cpuCount(Fixtures.CPUINFO));
        assertEquals(Optional.of("Raspberry Pi 4 Model B Rev 1.4"),
                SystemFiles.cpuModel("processor\t: 0\nBogoMIPS\t: 108.00\n\nModel\t\t: Raspberry Pi 4 Model B Rev 1.4\n"));
    }

    @Test
    void parsesPasswd() {
        assertEquals(Map.of(0L, "root", 1L, "daemon", 1000L, "kevin"), SystemFiles.userNames(Fixtures.PASSWD + "+::::::\n"));
    }

    @Test
    void computesProcessCpuRelativeToOneCore() {
        // 20 ticks used while 400 ticks elapsed over 2 cores (200 per core) = 10% of one core.
        assertEquals(10.0, SystemSampler.processCpuPercent(20, 400, 2), 1e-9);
        // A process saturating both cores reports 200%, like top.
        assertEquals(200.0, SystemSampler.processCpuPercent(400, 400, 2), 1e-9);
        assertEquals(0.0, SystemSampler.processCpuPercent(20, 0, 2));
    }
}
