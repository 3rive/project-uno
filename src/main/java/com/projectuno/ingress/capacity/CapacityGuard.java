package com.projectuno.ingress.capacity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

/**
 * Lock-free admission gate. A request holds one permit only while it is inside
 * the virtual-thread handler, so the counter tracks real concurrency rather than
 * completed throughput.
 *
 * <p>The compare-and-swap loop is correct under contention from many virtual
 * threads. Work stays off the Vert.x event loop, so those threads keep accepting
 * sockets while admitted calls are parked on downstream I/O.
 */
@ApplicationScoped
public class CapacityGuard {

    private static final Logger LOG = Logger.getLogger(CapacityGuard.class);

    private final long maxInflight;
    private final AtomicLong inflight = new AtomicLong();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Counter rejected;

    @Inject
    public CapacityGuard(CapacityConfig config, MeterRegistry registry) {
        this(config.maxInflight(), registry);
    }

    public CapacityGuard(long maxInflight, MeterRegistry registry) {
        if (maxInflight < 1) {
            throw new IllegalArgumentException("app.capacity.max-inflight must be >= 1");
        }
        this.maxInflight = maxInflight;
        this.rejected = Counter.builder("ingress.capacity.rejected")
                .description("Requests rejected because the ingress was saturated or shutting down")
                .register(registry);
        Gauge.builder("ingress.capacity.inflight", inflight, value -> (double) value.get())
                .description("Requests currently inside the handler")
                .register(registry);
        Gauge.builder("ingress.capacity.max", this, value -> (double) value.maxInflight())
                .description("Configured in-flight ceiling")
                .register(registry);
    }

    public Admission tryAdmit() {
        if (!accepting.get()) {
            rejected.increment();
            LOG.debug("Rejected request because ingress is shutting down");
            return Admission.rejected(inflight.get(), maxInflight, RejectReason.SHUTTING_DOWN);
        }
        while (true) {
            long current = inflight.get();
            if (current >= maxInflight) {
                rejected.increment();
                LOG.debugf("Rejected request at inflight=%d max=%d", current, maxInflight);
                return Admission.rejected(current, maxInflight, RejectReason.SATURATED);
            }
            if (inflight.compareAndSet(current, current + 1)) {
                return Admission.admitted(current + 1, maxInflight);
            }
        }
    }

    public void release() {
        while (true) {
            long current = inflight.get();
            if (current <= 0) {
                return;
            }
            if (inflight.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    public CapacitySnapshot snapshot() {
        long current = inflight.get();
        long available = Math.max(0, maxInflight - current);
        boolean open = accepting.get() && current < maxInflight;
        return new CapacitySnapshot(maxInflight, current, available, open);
    }

    public long inflight() {
        return inflight.get();
    }

    public long maxInflight() {
        return maxInflight;
    }

    public void stopAccepting() {
        accepting.set(false);
    }

    void onShutdown(@Observes ShutdownEvent event) {
        stopAccepting();
        LOG.infof("Stopped admitting requests; inflight=%d", inflight.get());
    }
}
