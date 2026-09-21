package ai.ultimate.tools.workspace;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Allocates bounded scratch directories for external tools without exposing
 * another tenant or tool's workspace through the manager API.
 *
 * <p>Every lease is bound to a tenant and tool fingerprint. Paths are always
 * resolved beneath a cryptographically random UUID directory. Existing symbolic
 * links inside the managed tree are rejected before a path is returned. Callers
 * should obtain all scratch paths through this manager rather than constructing
 * sibling paths from a returned {@link Path}.</p>
 */
@Component
public final class ScratchpadWorkspaceManager {

    static final String MANAGED_DIRECTORY = "ultimate-managed-workspaces";
    private static final int MAX_UUID_ATTEMPTS = 16;

    private final Path managedRoot;
    private final Clock clock;
    private final Supplier<UUID> uuidSupplier;
    private final Object managerIdentity = new Object();
    private final ConcurrentMap<UUID, LeaseState> leases = new ConcurrentHashMap<>();

    /** Creates a production manager rooted in the user's managed workspace directory. */
    public ScratchpadWorkspaceManager() {
        this(Path.of(System.getProperty("user.home"), MANAGED_DIRECTORY),
                Clock.systemUTC(), UUID::randomUUID);
    }

    ScratchpadWorkspaceManager(Path managedRoot, Clock clock, Supplier<UUID> uuidSupplier) {
        this.managedRoot = Objects.requireNonNull(managedRoot, "managedRoot")
                .toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
    }

    /**
     * Allocates one private workspace for a tenant/tool pair.
     *
     * @throws IOException if a safe workspace cannot be created
     * @throws IllegalArgumentException when tenant or tool identity is blank
     * @throws SecurityException when the managed directory tree is symlinked
     */
    public WorkspaceLease allocate(String tenantId, String toolId) throws IOException {
        AccessKey access = AccessKey.of(tenantId, toolId);
        Path root = ensureManagedRoot();
        Path tenantRoot = ensureDirectory(root.resolve("tenant-" + access.tenantFingerprint()));
        Path toolRoot = ensureDirectory(tenantRoot.resolve("tool-" + access.toolFingerprint()));

        for (int attempt = 0; attempt < MAX_UUID_ATTEMPTS; attempt++) {
            UUID id = Objects.requireNonNull(uuidSupplier.get(), "uuidSupplier returned null");
            if (leases.containsKey(id)) {
                continue;
            }

            Path leaseRoot = toolRoot.resolve(id.toString());
            try {
                Files.createDirectory(leaseRoot);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            rejectSymlink(leaseRoot);

            LeaseState state = new LeaseState(id, access, leaseRoot, clock.instant());
            if (leases.putIfAbsent(id, state) == null) {
                return new WorkspaceLease(managerIdentity, id, state.createdAt);
            }
            deleteTree(leaseRoot);
        }
        throw new IOException("Unable to allocate a unique scratch workspace.");
    }

    /**
     * Resolves a relative path inside a lease after enforcing tenant/tool ownership.
     * Existing symbolic-link components are rejected.
     */
    public Path resolve(String tenantId, String toolId, WorkspaceLease lease, String relativePath)
            throws IOException {
        Objects.requireNonNull(relativePath, "relativePath");
        AccessKey access = AccessKey.of(tenantId, toolId);
        LeaseState state = requireLease(lease);
        synchronized (state.monitor) {
            requireActive(state, access);
            Path target = resolveConfined(state.root, relativePath);
            state.lastAccess = clock.instant();
            return target;
        }
    }

    /**
     * Creates a directory (and missing parents) inside a lease without following
     * symlinks already present in the managed tree.
     */
    public Path createDirectory(
            String tenantId, String toolId, WorkspaceLease lease, String relativePath)
            throws IOException {
        Objects.requireNonNull(relativePath, "relativePath");
        AccessKey access = AccessKey.of(tenantId, toolId);
        LeaseState state = requireLease(lease);
        synchronized (state.monitor) {
            requireActive(state, access);
            Path target = resolveConfined(state.root, relativePath);
            Path relative = state.root.relativize(target);
            Path current = state.root;
            for (Path part : relative) {
                current = ensureDirectory(current.resolve(part));
            }
            state.lastAccess = clock.instant();
            return target;
        }
    }

    /**
     * Releases one lease and recursively deletes its scratch contents.
     *
     * @return {@code false} when the lease has already been released or swept
     */
    public boolean release(String tenantId, String toolId, WorkspaceLease lease) throws IOException {
        AccessKey access = AccessKey.of(tenantId, toolId);
        LeaseState state = requireLeaseOrNull(lease);
        if (state == null) {
            return false;
        }
        synchronized (state.monitor) {
            if (leases.get(state.id) != state) {
                return false;
            }
            requireAccess(state, access);
            if (state.closing) {
                return false;
            }
            state.closing = true;
            try {
                deleteTree(state.root);
                leases.remove(state.id, state);
                return true;
            } catch (IOException | RuntimeException e) {
                state.closing = false;
                throw e;
            }
        }
    }

    /**
     * Removes leases that have been idle for at least {@code maxIdle}.
     * Accessing or creating a directory through this manager refreshes the idle clock.
     */
    public int sweepExpired(Duration maxIdle) throws IOException {
        Objects.requireNonNull(maxIdle, "maxIdle");
        if (maxIdle.isNegative() || maxIdle.isZero()) {
            throw new IllegalArgumentException("maxIdle must be positive");
        }
        Instant cutoff = clock.instant().minus(maxIdle);
        int removed = 0;
        for (LeaseState state : leases.values()) {
            synchronized (state.monitor) {
                if (leases.get(state.id) != state || state.closing || state.lastAccess.isAfter(cutoff)) {
                    continue;
                }
                state.closing = true;
                try {
                    deleteTree(state.root);
                    if (leases.remove(state.id, state)) {
                        removed++;
                    }
                } catch (IOException | RuntimeException e) {
                    state.closing = false;
                    throw e;
                }
            }
        }
        return removed;
    }

    /** Returns the number of active leases, primarily for health/metrics reporting. */
    public int activeLeaseCount() {
        return leases.size();
    }

    private LeaseState requireLease(WorkspaceLease lease) {
        LeaseState state = requireLeaseOrNull(lease);
        if (state == null) {
            throw new IllegalStateException("Workspace lease is not active.");
        }
        return state;
    }

    private LeaseState requireLeaseOrNull(WorkspaceLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (lease.managerIdentity != managerIdentity) {
            throw new SecurityException("Workspace lease belongs to a different manager.");
        }
        return leases.get(lease.id);
    }

    private void requireActive(LeaseState state, AccessKey access) {
        if (leases.get(state.id) != state || state.closing) {
            throw new IllegalStateException("Workspace lease is not active.");
        }
        requireAccess(state, access);
    }

    private static void requireAccess(LeaseState state, AccessKey access) {
        if (!state.access.equals(access)) {
            throw new SecurityException("Workspace lease is not accessible to this tenant/tool pair.");
        }
    }

    private Path ensureManagedRoot() throws IOException {
        if (Files.exists(managedRoot, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymlink(managedRoot);
            if (!Files.isDirectory(managedRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Managed workspace root is not a directory.");
            }
            return managedRoot;
        }
        Files.createDirectories(managedRoot);
        rejectSymlink(managedRoot);
        return managedRoot;
    }

    private static Path ensureDirectory(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymlink(path);
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Workspace path is not a directory: " + path.getFileName());
            }
            return path;
        }
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException race) {
            rejectSymlink(path);
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Workspace path is not a directory: " + path.getFileName(), race);
            }
        }
        rejectSymlink(path);
        return path;
    }

    private static Path resolveConfined(Path root, String relativePath) throws IOException {
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) {
            throw new SecurityException("Absolute scratch paths are not allowed.");
        }
        Path normalized = relative.normalize();
        if (normalized.startsWith("..")) {
            throw new SecurityException("Scratch path escapes its workspace.");
        }
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new SecurityException("Scratch path escapes its workspace.");
        }
        rejectExistingSymlinkChain(root, target);
        return target;
    }

    private static void rejectExistingSymlinkChain(Path root, Path target) throws IOException {
        rejectSymlink(root);
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymlink(current);
            }
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new SecurityException("Symbolic links are not allowed in managed workspace paths.");
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        // Materialize the walk while its stream is open, close every directory
        // descriptor, and only then begin deletion. This matters on platforms
        // that refuse directory mutation while traversal handles remain open.
        java.util.List<Path> pathsToDelete;
        try (Stream<Path> paths = Files.walk(root)) {
            pathsToDelete = paths.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : pathsToDelete) {
            Files.deleteIfExists(path);
        }
    }

    private static String fingerprint(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Opaque allocation token. Only the allocating manager can validate it. */
    public static final class WorkspaceLease {
        private final Object managerIdentity;
        private final UUID id;
        private final Instant createdAt;

        private WorkspaceLease(Object managerIdentity, UUID id, Instant createdAt) {
            this.managerIdentity = managerIdentity;
            this.id = id;
            this.createdAt = createdAt;
        }

        public UUID id() {
            return id;
        }

        public Instant createdAt() {
            return createdAt;
        }
    }

    private record AccessKey(String tenantFingerprint, String toolFingerprint) {
        static AccessKey of(String tenantId, String toolId) {
            return new AccessKey(fingerprint(tenantId, "tenantId"), fingerprint(toolId, "toolId"));
        }
    }

    private static final class LeaseState {
        private final Object monitor = new Object();
        private final UUID id;
        private final AccessKey access;
        private final Path root;
        private final Instant createdAt;
        private Instant lastAccess;
        private boolean closing;

        private LeaseState(UUID id, AccessKey access, Path root, Instant createdAt) {
            this.id = id;
            this.access = access;
            this.root = root;
            this.createdAt = createdAt;
            this.lastAccess = createdAt;
        }
    }
}
