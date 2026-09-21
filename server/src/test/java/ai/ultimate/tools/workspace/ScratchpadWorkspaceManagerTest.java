package ai.ultimate.tools.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScratchpadWorkspaceManagerTest {

    @TempDir
    Path temporary;

    @Test
    void allocationUsesOpaqueTenantToolDirectoriesAndUuidLeaseNames() throws Exception {
        var manager = manager(new MutableClock(Instant.parse("2026-09-19T00:00:00Z")));
        var lease = manager.allocate("tenant@example.com", "libreoffice");

        Path root = manager.resolve("tenant@example.com", "libreoffice", lease, ".");

        assertTrue(root.startsWith(temporary.resolve("managed")));
        assertEquals(lease.id().toString(), root.getFileName().toString());
        assertEquals(lease.id(), UUID.fromString(root.getFileName().toString()));
        assertFalse(root.toString().contains("tenant@example.com"));
        assertFalse(root.toString().contains("libreoffice"));
    }

    @Test
    void accessMatrixRejectsParallelTenantAndToolNamespaces() throws Exception {
        var manager = manager(new MutableClock(Instant.EPOCH));
        var lease = manager.allocate("tenant-a", "image");

        assertThrows(SecurityException.class,
                () -> manager.resolve("tenant-b", "image", lease, "input.bin"));
        assertThrows(SecurityException.class,
                () -> manager.resolve("tenant-a", "libreoffice", lease, "input.bin"));
        assertEquals(1, manager.activeLeaseCount());
    }

    @Test
    void rejectsParentTraversalAndAbsolutePaths() throws Exception {
        var manager = manager(new MutableClock(Instant.EPOCH));
        var lease = manager.allocate("tenant-a", "image");

        assertThrows(SecurityException.class,
                () -> manager.resolve("tenant-a", "image", lease, "../tenant-b/secret"));
        assertThrows(SecurityException.class,
                () -> manager.resolve("tenant-a", "image", lease, temporary.resolve("outside").toString()));
    }

    @Test
    void existingSymlinkCannotTurnAWorkspacePathIntoEgress() throws Exception {
        var manager = manager(new MutableClock(Instant.EPOCH));
        var lease = manager.allocate("tenant-a", "image");
        Path root = manager.resolve("tenant-a", "image", lease, ".");
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("keep.txt"), "keep");
        try {
            Files.createSymbolicLink(root.resolve("escape"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false, "symbolic links unavailable on this host");
        }

        assertThrows(SecurityException.class,
                () -> manager.resolve("tenant-a", "image", lease, "escape/keep.txt"));
        assertEquals("keep", Files.readString(sentinel));
    }

    @Test
    void releaseRecursivelyDeletesScratchAndInvalidatesLease() throws Exception {
        var manager = manager(new MutableClock(Instant.EPOCH));
        var lease = manager.allocate("tenant-a", "image");
        Path nested = manager.createDirectory("tenant-a", "image", lease, "a/b/c");
        Files.writeString(nested.resolve("payload.txt"), "payload");
        Path root = manager.resolve("tenant-a", "image", lease, ".");

        assertTrue(manager.release("tenant-a", "image", lease));

        assertFalse(Files.exists(root));
        assertEquals(0, manager.activeLeaseCount());
        assertFalse(manager.release("tenant-a", "image", lease));
        assertThrows(IllegalStateException.class,
                () -> manager.resolve("tenant-a", "image", lease, "later.txt"));
    }

    @Test
    void sweepRemovesOnlyIdleLeases() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-09-19T00:00:00Z"));
        var manager = manager(clock);
        var oldLease = manager.allocate("tenant", "image");
        clock.advance(Duration.ofSeconds(30));
        var freshLease = manager.allocate("tenant", "image");
        clock.advance(Duration.ofSeconds(30));

        assertEquals(1, manager.sweepExpired(Duration.ofSeconds(45)));

        assertThrows(IllegalStateException.class,
                () -> manager.resolve("tenant", "image", oldLease, "."));
        assertTrue(Files.isDirectory(manager.resolve("tenant", "image", freshLease, ".")));
        assertEquals(1, manager.activeLeaseCount());
    }

    @Test
    void accessRefreshesIdleDeadline() throws Exception {
        var clock = new MutableClock(Instant.EPOCH);
        var manager = manager(clock);
        var lease = manager.allocate("tenant", "image");
        clock.advance(Duration.ofSeconds(40));
        manager.resolve("tenant", "image", lease, "work.txt");
        clock.advance(Duration.ofSeconds(20));

        assertEquals(0, manager.sweepExpired(Duration.ofSeconds(30)));
        assertEquals(1, manager.activeLeaseCount());
    }

    @Test
    void leaseFromAnotherManagerCannotBeReused() throws Exception {
        var managerA = manager(new MutableClock(Instant.EPOCH));
        var managerB = new ScratchpadWorkspaceManager(
                temporary.resolve("other-managed"), Clock.systemUTC(), UUID::randomUUID);
        var lease = managerA.allocate("tenant", "image");

        assertThrows(SecurityException.class,
                () -> managerB.resolve("tenant", "image", lease, "."));
    }

    @Test
    void concurrentAllocationsRemainUniqueAndTracked() throws Exception {
        var manager = new ScratchpadWorkspaceManager(
                temporary.resolve("managed"), Clock.systemUTC(), UUID::randomUUID);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ScratchpadWorkspaceManager.WorkspaceLease>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                tasks.add(() -> manager.allocate("tenant", "image"));
            }
            var futures = executor.invokeAll(tasks);
            var leases = new ArrayList<ScratchpadWorkspaceManager.WorkspaceLease>();
            for (var future : futures) {
                leases.add(future.get());
            }

            assertEquals(32, new HashSet<>(leases.stream().map(
                    ScratchpadWorkspaceManager.WorkspaceLease::id).toList()).size());
            assertEquals(32, manager.activeLeaseCount());
            for (var lease : leases) {
                assertTrue(manager.release("tenant", "image", lease));
            }
            assertEquals(0, manager.activeLeaseCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recursiveTraversalIsClosedBeforeReleaseReturns() throws Exception {
        var manager = manager(new MutableClock(Instant.EPOCH));
        var lease = manager.allocate("tenant", "image");
        Path root = manager.resolve("tenant", "image", lease, ".");
        Files.writeString(manager.resolve("tenant", "image", lease, "payload.txt"), "payload");
        assertTrue(manager.release("tenant", "image", lease));

        Path managed = temporary.resolve("managed");
        Path renamed = temporary.resolve("managed-renamed");
        Files.move(managed, renamed);

        assertFalse(Files.exists(root));
        assertTrue(Files.isDirectory(renamed));
    }

    @Test
    void managedRootSymlinkIsRejected() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside-root"));
        Path managed = temporary.resolve("managed");
        try {
            Files.createSymbolicLink(managed, outside);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false, "symbolic links unavailable on this host");
        }
        var manager = new ScratchpadWorkspaceManager(managed, Clock.systemUTC(), UUID::randomUUID);

        assertThrows(SecurityException.class, () -> manager.allocate("tenant", "image"));
        assertEquals(0, manager.activeLeaseCount());
    }

    @Test
    void blankAccessIdentitiesAreRejected() {
        var manager = manager(new MutableClock(Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> manager.allocate(" ", "image"));
        assertThrows(IllegalArgumentException.class, () -> manager.allocate("tenant", ""));
    }

    private ScratchpadWorkspaceManager manager(MutableClock clock) {
        AtomicLong sequence = new AtomicLong(1);
        return new ScratchpadWorkspaceManager(
                temporary.resolve("managed"), clock,
                () -> new UUID(0x123456789abcdef0L, sequence.getAndIncrement()));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("test clock is UTC-only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
