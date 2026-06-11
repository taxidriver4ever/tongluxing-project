package com.tongdao.common.utils;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;

public final class SnowflakeIdGenerator {

    private static final long EPOCH = 1735689600000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator(resolveDatacenterId(), resolveWorkerId());

    private final long datacenterId;
    private final long workerId;
    private long sequence;
    private long lastTimestamp = -1L;

    private SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    public static long nextId() {
        return INSTANCE.next();
    }

    public static String nextIdString() {
        return String.valueOf(nextId());
    }

    private synchronized long next() {
        long timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntil(long timestamp) {
        long current = currentTimeMillis();
        while (current <= timestamp) {
            Thread.yield();
            current = currentTimeMillis();
        }
        return current;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private static long resolveDatacenterId() {
        return Math.floorMod(hashMacAddress(), MAX_DATACENTER_ID + 1);
    }

    private static long resolveWorkerId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return Math.floorMod(runtimeName.hashCode(), (int) MAX_WORKER_ID + 1);
    }

    private static int hashMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                byte[] mac = interfaces.nextElement().getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    int hash = 0;
                    for (byte value : mac) {
                        hash = 31 * hash + value;
                    }
                    return hash;
                }
            }
        } catch (Exception ignored) {
            // Fall through to random fallback.
        }
        return new SecureRandom().nextInt();
    }
}
