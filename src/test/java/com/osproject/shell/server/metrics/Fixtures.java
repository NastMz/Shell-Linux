package com.osproject.shell.server.metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Realistic {@code /proc} and {@code /etc} samples captured from Ubuntu 24.04 (x86_64).
 */
final class Fixtures {

    static final String PROC_STAT_1 = """
            cpu  10132153 290696 3084719 46828483 16683 0 25195 0 0 0
            cpu0 1393280 32966 572056 13530645 2236 0 24004 0 0 0
            cpu1 1335734 54208 557064 13626066 2830 0 409 0 0 0
            intr 199292687 32 9 0 0 0 0 3 0 1 0 0 0 82 0 0 0 0
            ctxt 381487823
            btime 1732290000
            processes 1162094
            procs_running 2
            procs_blocked 0
            softirq 49391297 3 15476081 1 1053 0 0 7302 0 12 33906845
            """;

    /** One second after {@link #PROC_STAT_1}: aggregate 50% busy, cpu0 75% busy, cpu1 25% busy. */
    static final String PROC_STAT_2 = """
            cpu  10132353 290696 3084719 46828683 16683 0 25195 0 0 0
            cpu0 1393430 32966 572056 13530695 2236 0 24004 0 0 0
            cpu1 1335784 54208 557064 13626216 2830 0 409 0 0 0
            btime 1732290000
            """;

    static final String MEMINFO = """
            MemTotal:       16234880 kB
            MemFree:         1029064 kB
            MemAvailable:   11234880 kB
            Buffers:          482508 kB
            Cached:          9431820 kB
            SwapCached:            0 kB
            Active:          6418316 kB
            SwapTotal:       2097148 kB
            SwapFree:        1048574 kB
            HugePages_Total:       0
            Hugepagesize:       2048 kB
            """;

    static final String LOADAVG = "0.42 0.37 0.30 2/1349 41234\n";

    static final String UPTIME = "10000.73 118200.15\n";

    static final String NET_DEV_1 = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 9000000   80000    0    0    0     0          0         0  9000000   80000    0    0    0     0       0          0
            enp3s0: 120000000  150000    0    0    0     0          0       100 20000000   90000    0    0    0     0       0          0
             wlan0: 3456789    4000    0    0    0     0          0         0  3456789    3000    0    0    0     0       0          0
            """;

    static final String NET_DEV_2 = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 9900000   80000    0    0    0     0          0         0  9900000   80000    0    0    0     0       0          0
            enp3s0: 120020480  150000    0    0    0     0          0       100 20004096   90000    0    0    0     0       0          0
             wlan0: 3456789    4000    0    0    0     0          0         0  3456789    3000    0    0    0     0       0          0
            """;

    static final String MOUNTS = """
            sysfs /sys sysfs rw,nosuid,nodev,noexec,relatime 0 0
            proc /proc proc rw,nosuid,nodev,noexec,relatime 0 0
            udev /dev devtmpfs rw,nosuid,relatime,size=8077060k,nr_inodes=2019265,mode=755 0 0
            tmpfs /run tmpfs rw,nosuid,nodev,noexec,relatime,size=1623488k,mode=755 0 0
            /dev/nvme0n1p2 / ext4 rw,relatime,errors=remount-ro 0 0
            /dev/loop0 /snap/core22/1663 squashfs ro,nodev,relatime,errors=continue 0 0
            tmpfs /dev/shm tmpfs rw,nosuid,nodev 0 0
            /dev/nvme0n1p1 /boot/efi vfat rw,relatime,fmask=0077,dmask=0077 0 0
            /dev/sdb1 /media/kevin/My\\040Disk ntfs3 rw,relatime 0 0
            overlay /var/lib/docker/overlay2/abc/merged overlay rw,relatime,lowerdir=/x 0 0
            /dev/nvme0n1p2 /var/snap/bind ext4 rw,relatime 0 0
            cgroup2 /sys/fs/cgroup cgroup2 rw,nosuid,nodev,noexec,relatime 0 0
            """;

    static final String CPUINFO = """
            processor\t: 0
            vendor_id\t: GenuineIntel
            model name\t: Intel(R) Core(TM) i7-10750H CPU @ 2.60GHz
            cpu cores\t: 6

            processor\t: 1
            vendor_id\t: GenuineIntel
            model name\t: Intel(R) Core(TM) i7-10750H CPU @ 2.60GHz
            cpu cores\t: 6
            """;

    static final String OS_RELEASE = """
            PRETTY_NAME="Ubuntu 24.04.1 LTS"
            NAME="Ubuntu"
            VERSION_ID="24.04"
            VERSION="24.04.1 LTS (Noble Numbat)"
            ID=ubuntu
            """;

    static final String PASSWD = """
            root:x:0:0:root:/root:/bin/bash
            daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin
            kevin:x:1000:1000:Kevin,,,:/home/kevin:/bin/bash
            """;

    static final String PID1_STAT = "1 (systemd) S 0 1 1 0 -1 4194560 52413 2361418 98 1162 300 200 4917 2005 20 0 1 0 5 "
            + "171282432 3276 18446744073709551615 1 1 0 0 0 0 671173123 4096 1260 0 0 0 17 3 0 0 0 0 0\n";

    static final String PID1_STAT_LATER = "1 (systemd) S 0 1 1 0 -1 4194560 52413 2361418 98 1162 310 210 4917 2005 20 0 1 0 5 "
            + "171282432 3276 18446744073709551615 1 1 0 0 0 0 671173123 4096 1260 0 0 0 17 3 0 0 0 0 0\n";

    static final String PID1_STATUS = """
            Name:\tsystemd
            Umask:\t0000
            State:\tS (sleeping)
            Tgid:\t1
            Pid:\t1
            PPid:\t0
            Uid:\t0\t0\t0\t0
            Gid:\t0\t0\t0\t0
            VmRSS:\t   13104 kB
            Threads:\t1
            """;

    static final String PID1_CMDLINE = "/sbin/init\0splash\0";

    /** A command name with spaces and a closing parenthesis, which naive parsers get wrong. */
    static final String WEIRD_STAT = "4242 (my (weird) app) R 1000 4242 4242 0 -1 4194304 100 0 0 0 1500 500 0 0 20 0 4 0 "
            + "123456 2000000 500 18446744073709551615 1 1 0 0 0 0 0 0 0 0 0 0 17 1 0 0 0 0 0\n";

    static final String WEIRD_STATUS = """
            Name:\tmy (weird) app
            Uid:\t1000\t1000\t1000\t1000
            VmRSS:\t    2000 kB
            Threads:\t4
            """;

    static final String KTHREAD_STAT = "2 (kthreadd) S 0 0 0 0 -1 2129984 0 0 0 0 0 7 0 0 20 0 1 0 5 0 0 "
            + "18446744073709551615 0 0 0 0 0 0 0 2147483647 0 0 0 0 17 2 0 0 0 0 0\n";

    static final String KTHREAD_STATUS = """
            Name:\tkthreadd
            Uid:\t0\t0\t0\t0
            Threads:\t1
            """;

    private Fixtures() {
    }

    static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
