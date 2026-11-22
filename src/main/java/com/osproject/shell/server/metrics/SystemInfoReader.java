package com.osproject.shell.server.metrics;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Reads the static host facts returned by {@code system.info}.
 */
public final class SystemInfoReader {

    private final ProcFs procFs;

    /**
     * Creates a reader.
     *
     * @param procFs access to {@code /proc} and {@code /etc}
     */
    public SystemInfoReader(ProcFs procFs) {
        this.procFs = procFs;
    }

    /**
     * Reads the host facts, falling back to JVM properties when a file is unavailable.
     *
     * @return the system information
     */
    public SystemInfo read() {
        String cpuInfo = procFs.read("proc/cpuinfo").orElse("");
        int cores = SystemFiles.cpuCount(cpuInfo);
        long bootSeconds = procFs.read("proc/stat").map(CpuStat::parse).map(CpuStat::bootTimeSeconds).orElse(0L);
        return new SystemInfo(
                procFs.read("proc/sys/kernel/hostname").map(String::trim).filter(s -> !s.isEmpty())
                        .orElseGet(SystemInfoReader::jvmHostname),
                procFs.read("etc/os-release").flatMap(SystemFiles::osPrettyName)
                        .orElseGet(() -> System.getProperty("os.name")),
                procFs.read("proc/sys/kernel/osrelease").map(String::trim).filter(s -> !s.isEmpty())
                        .orElseGet(() -> System.getProperty("os.version")),
                System.getProperty("os.arch"),
                SystemFiles.cpuModel(cpuInfo).orElse("Unknown CPU"),
                cores > 0 ? cores : Runtime.getRuntime().availableProcessors(),
                procFs.read("proc/meminfo").map(MemInfo::parse).map(MemInfo::totalBytes).orElse(0L),
                bootSeconds * 1000);
    }

    private static String jvmHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
