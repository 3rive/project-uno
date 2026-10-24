package com.projectuno.ingress.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CapacityGuardTest {

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new CapacityGuard(0, new SimpleMeterRegistry()));
    }

    @Test
    void admitsOneMillionThenRejectsTheNext() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CapacityGuard guard = new CapacityGuard(1_000_000, registry);

        for (int i = 1; i <= 1_000_000; i++) {
            Admission admission = guard.tryAdmit();
            assertTrue(admission.admitted(), "admit " + i);
            assertEquals(i, admission.inflight());
        }

        Admission overflow = guard.tryAdmit();
        assertFalse(overflow.admitted());
        assertEquals(RejectReason.SATURATED, overflow.reason());
        assertEquals(1_000_000, overflow.inflight());
        assertEquals(1_000_000, guard.snapshot().maxInflight());
        assertEquals(0, guard.snapshot().available());
        assertFalse(guard.snapshot().accepting());
        assertEquals(1.0, registry.get("ingress.capacity.rejected").counter().count());

        for (int i = 0; i < 1_000_000; i++) {
            guard.release();
        }
        assertEquals(0, guard.inflight());
        assertTrue(guard.snapshot().accepting());

        Admission again = guard.tryAdmit();
        assertTrue(again.admitted());
        guard.release();
        assertEquals(0, guard.inflight());
    }

    @Test
    void releaseNeverDrivesTheCounterNegative() {
        CapacityGuard guard = new CapacityGuard(2, new SimpleMeterRegistry());
        guard.release();
        guard.release();
        assertEquals(0, guard.inflight());
        assertTrue(guard.tryAdmit().admitted());
        guard.release();
        guard.release();
        assertEquals(0, guard.inflight());
    }

    @Test
    void shutdownRejectsWithoutConsumingASlot() {
        CapacityGuard guard = new CapacityGuard(10, new SimpleMeterRegistry());
        guard.stopAccepting();

        Admission admission = guard.tryAdmit();
        assertFalse(admission.admitted());
        assertEquals(RejectReason.SHUTTING_DOWN, admission.reason());
        assertEquals(0, guard.inflight());
        assertFalse(guard.snapshot().accepting());
    }

    @Test
    void contentionStaysWithinTheCapAndDrains() throws Exception {
        int max = 256;
        CapacityGuard guard = new CapacityGuard(max, new SimpleMeterRegistry());
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong peak = new AtomicLong();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                tasks.add(executor.submit(() -> {
                    while (running.get()) {
                        Admission admission = guard.tryAdmit();
                        if (!admission.admitted()) {
                            assertEquals(RejectReason.SATURATED, admission.reason());
                            continue;
                        }
                        assertTrue(admission.inflight() <= max);
                        peak.accumulateAndGet(guard.inflight(), Math::max);
                        guard.release();
                    }
                }));
            }
            Thread.sleep(300);
            running.set(false);
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }

        assertTrue(peak.get() > 0);
        assertTrue(peak.get() <= max);
        assertEquals(0, guard.inflight());
    }

    @Test
    void manyThreadsCanHoldTheFullMillion() throws Exception {
        int max = 1_000_000;
        int threads = 16;
        CapacityGuard guard = new CapacityGuard(max, new SimpleMeterRegistry());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            int share = max / threads;
            for (int i = 0; i < threads; i++) {
                tasks.add(executor.submit(() -> {
                    for (int n = 0; n < share; n++) {
                        Admission admission = guard.tryAdmit();
                        if (!admission.admitted()) {
                            throw new IllegalStateException("unexpected rejection at " + admission.inflight());
                        }
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        }

        assertEquals(max, guard.inflight());
        assertFalse(guard.tryAdmit().admitted());
        assertEquals(max, guard.inflight());
    }
}
