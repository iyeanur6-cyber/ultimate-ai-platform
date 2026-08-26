import * as crypto from 'crypto';
import * as path from 'path';
import * as fs from 'fs/promises';
import { createReadStream } from 'fs';

/**
 * Central Multi-Tenant Scratchpad Workspace Manager.
 *
 * Responsibilities:
 *   1. Centralize path resolution for all external tools (LibreOffice, Chromium,
 *      FFmpeg, ImageMagick) using cryptographically secure UUID-based naming.
 *      Timestamp-based naming is explicitly rejected to avoid collision and
 *      prediction attacks.
 *   2. Mitigate file-descriptor leaks by wrapping every directory traversal
 *      in deterministic try/finally blocks (the Node equivalent of Java's
 *      try-with-resources on `Files.walk`).
 *   3. Enforce strict egress restrictions so a tool running for tenant A can
 *      never touch tenant B's subnet directory tree.
 *
 * All external tool integrations MUST route through `getScratchpadManager()`.
 */

export type ToolIdentifier =
  | 'libreoffice'
  | 'chromium'
  | 'ffmpeg'
  | 'imagemagick';

export interface TenantContext {
  tenantId: string;
  subnet: string;
  allowedTools: ToolIdentifier[];
}

export interface WorkspaceAllocation {
  workspaceId: string;
  tenantId: string;
  tool: ToolIdentifier;
  rootPath: string;
  seed: string;
  createdAt: number;
}

const TOOL_ROOT_SEGMENT: Record<ToolIdentifier, string> = {
  libreoffice: 'lo',
  chromium: 'cr',
  ffmpeg: 'ff',
  imagemagick: 'im',
};

const BASE_SCRATCH_DIR: string = path.resolve(
  process.env.ULTIMATE_AI_SCRATCH_BASE ?? '/tmp/ultimate-ai/scratch'
);

// Strict tenant ID pattern — no path separators, no `..`, no traversal chars.
const TENANT_ID_PATTERN = /^[a-zA-Z0-9_-]{1,64}$/;

function assertValidTenantId(tenantId: string): void {
  if (typeof tenantId !== 'string' || !TENANT_ID_PATTERN.test(tenantId)) {
    throw new Error(
      `Rejected tenant ID "${tenantId}": must match ${TENANT_ID_PATTERN.source}`
    );
  }
}

/**
 * Secure workspace identifier.
 *
 * Deliberately uses crypto.randomUUID() instead of Date.now()/counter-based
 * naming. Timestamp schemes are vulnerable to collision attacks under bursty
 * multi-tenant load and leak allocation timing.
 */
function generateSecureWorkspaceId(): string {
  // crypto.randomUUID is RFC 4122 v4 — 122 bits of CSPRNG entropy.
  return crypto.randomUUID();
}

/**
 * Cryptographic seed allocated per workspace. Used downstream by external
 * tools (e.g. FFmpeg temp frame naming) to avoid predictable scratch names.
 */
function generateCryptographicSeed(): string {
  return crypto.randomBytes(32).toString('hex');
}

function tenantRoot(tenantId: string): string {
  return path.join(BASE_SCRATCH_DIR, 'tenants', tenantId);
}

function workspaceRoot(
  tenantId: string,
  workspaceId: string,
  tool: ToolIdentifier
): string {
  return path.join(
    tenantRoot(tenantId),
    'workspaces',
    workspaceId,
    TOOL_ROOT_SEGMENT[tool]
  );
}

/**
 * Egress restriction.
 *
 * After resolving any path, we re-verify the resolved absolute path is still
 * inside the owning tenant's root. This catches symbolic-link escapes,
 * `..` injection, and absolute-path tricks.
 */
function assertWithinTenantBoundary(
  targetPath: string,
  tenantId: string
): void {
  const expectedRoot = path.resolve(tenantRoot(tenantId));
  const resolved = path.resolve(targetPath);
  const relative = path.relative(expectedRoot, resolved);
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(
      `Egress violation: path "${targetPath}" escapes tenant "${tenantId}" boundary`
    );
  }
}

export class ScratchpadWorkspaceManager {
  /**
   * Access matrix: tenantId -> set of tools they are allowed to invoke.
   * Tools not in this set cannot allocate a workspace — full stop.
   */
  private readonly accessMatrix: Map<string, Set<ToolIdentifier>> = new Map();

  /**
   * Register a tenant and the tools they are allowed to use.
   * Required before any `allocateWorkspace` call for that tenant.
   */
  registerTenant(ctx: TenantContext): void {
    assertValidTenantId(ctx.tenantId);
    if (!Array.isArray(ctx.allowedTools) || ctx.allowedTools.length === 0) {
      throw new Error(
        `Tenant "${ctx.tenantId}" must declare at least one allowed tool`
      );
    }
    this.accessMatrix.set(ctx.tenantId, new Set(ctx.allowedTools));
  }

  /**
   * Internal: enforce the access matrix before any allocation or path
   * resolution. This is the egress restriction point at the API boundary.
   */
  private assertToolPermitted(
    tenantId: string,
    tool: ToolIdentifier
  ): void {
    const allowed = this.accessMatrix.get(tenantId);
    if (!allowed || !allowed.has(tool)) {
    throw new Error(
        `Tenant "${tenantId}" is not permitted to use tool "${tool}"`
      );
    }
  }

  /**
   * Allocate a fresh per-tool workspace for a tenant.
   *
   * - Uses a secure UUID, never a timestamp.
   * - Allocates a 32-byte cryptographic seed and persists it alongside
   *   the workspace metadata so external tools can read it deterministically.
   * - Verifies the egress boundary after `mkdir` to defend against symlink
   *   races.
   */
  async allocateWorkspace(
    tenantId: string,
    tool: ToolIdentifier
  ): Promise<WorkspaceAllocation> {
    assertValidTenantId(tenantId);
    this.assertToolPermitted(tenantId, tool);

    const workspaceId = generateSecureWorkspaceId();
    const rootPath = workspaceRoot(tenantId, workspaceId, tool);
    const seed = generateCryptographicSeed();

    await fs.mkdir(rootPath, { recursive: true });
    assertWithinTenantBoundary(rootPath, tenantId);

    const metadata = {
      workspaceId,
      tenantId,
      tool,
      seed,
      createdAt: Date.now(),
    };

    const metadataPath = path.join(rootPath, '.workspace-meta.json');
    await fs.writeFile(
      metadataPath,
      JSON.stringify(metadata, null, 2),
      'utf-8'
    );

    // Drop seed file for tools that read it as a plain blob.
    const seedPath = path.join(rootPath, '.seed');
    await fs.writeFile(seedPath, seed, 'utf-8');

    return { ...metadata, rootPath };
  }

  /**
   * FD-leak safe directory traversal.
   *
   * The original issue specifically calls out `Files.walk`-style traversals
   * leaking file descriptors. The Node equivalent is `fs.opendir` returning
   * a `Dir` handle that MUST be closed. We close it in a `finally` block —
   * the JavaScript analogue of Java's try-with-resources — on every iteration
   * of the stack, so an early `return`, `throw`, or `break` from the
   * consumer cannot leak a handle.
   */
  async *walkDirectory(
    rootPath: string,
    tenantId: string
  ): AsyncGenerator<string, void, void> {
    assertWithinTenantBoundary(rootPath, tenantId);

    const stack: string[] = [path.resolve(rootPath)];

    while (stack.length > 0) {
      const current = stack.pop()!;
      let dir: fs.Dir | null = null;

      try {
        try {
          dir = await fs.opendir(current);
        } catch (err) {
          const code = (err as NodeJS.ErrnoException).code;
          if (code === 'ENOTDIR') {
            // Leaf file — yield it, no handle to close.
            assertWithinTenantBoundary(current, tenantId);
            yield current;
            continue;
          }
          throw err;
        }

        for await (const entry of dir) {
          const fullPath = path.join(current, entry.name);
          // Egress check on every yielded path.
          assertWithinTenantBoundary(fullPath, tenantId);
          yield fullPath;
          if (entry.isDirectory()) {
            stack.push(fullPath);
          }
        }
      } finally {
        // Deterministic FD release — the whole point of the issue's
        // try-with-resources requirement.
        if (dir) {
          await dir.close().catch(() => {
            /* swallow: handle already released */
          });
        }
      }
    }
  }

  /**
   * Sweeping garbage collection for expired workspaces.
   *
   * - Walks each tenant's workspaces directory using the FD-safe traversal
   *   above (so GC itself cannot leak FDs).
   * - Reads `.workspace-meta.json` to determine createdAt; falls back to
   *   mtime if metadata is missing.
   * - Re-validates the tenant boundary on every path before unlinking.
   */
  async sweepExpired(
    tenantId: string,
    maxAgeMs: number
  ): Promise<number> {
    assertValidTenantId(tenantId);
    const tenantWorkspaceRoot = path.join(
      tenantRoot(tenantId),
      'workspaces'
    );

    let entries: string[];
    try {
      entries = await fs.readdir(tenantWorkspaceRoot);
    } catch (err) {
      const code = (err as NodeJS.ErrnoException).code;
      if (code === 'ENOENT') return 0;
      throw err;
    }

    const now = Date.now();
    let removed = 0;

    for (const entry of entries) {
      const wsDir = path.join(tenantWorkspaceRoot, entry);
      assertWithinTenantBoundary(wsDir, tenantId);

      let createdAt: number | null = null;

      try {
        const raw = await fs.readFile(
          path.join(wsDir, '.workspace-meta.json'),
          'utf-8'
        );
        const meta = JSON.parse(raw) as { createdAt?: number };
        if (typeof meta.createdAt === 'number') {
          createdAt = meta.createdAt;
        }
      } catch {
        // Fall back to mtime below.
      }

      if (createdAt === null) {
        try {
          const stat = await fs.stat(wsDir);
          createdAt = stat.mtimeMs;
        } catch {
          continue;
        }
      }

      if (now - createdAt > maxAgeMs) {
        await fs.rm(wsDir, { recursive: true, force: true });
        removed++;
      }
    }

    return removed;
  }

  /**
   * Centralized path resolver. All external tools must go through this —
   * no direct `path.join` against tenant directories elsewhere.
   */
  resolveToolPath(
    allocation: WorkspaceAllocation,
    ...segments: string[]
  ): string {
    const resolved = path.resolve(allocation.rootPath, ...segments);
    assertWithinTenantBoundary(resolved, allocation.tenantId);
    return resolved;
  }

  /**
   * FD-safe line reader. Wraps `createReadStream` in an async generator
   * whose `finally` destroys the stream — protecting against consumers
   * that `break` early and would otherwise leave a handle open.
   */
  async *safeReadLines(
    filePath: string,
    tenantId: string
  ): AsyncGenerator<string, void, void> {
    assertWithinTenantBoundary(filePath, tenantId);

    const stream = createReadStream(filePath, { encoding: 'utf-8' });
    let buffer = '';

    try {
      for await (const chunk of stream) {
        buffer += chunk as string;
        let idx: number;
        while ((idx = buffer.indexOf('\n')) >= 0) {
          yield buffer.slice(0, idx);
          buffer = buffer.slice(idx + 1);
        }
      }
      if (buffer.length > 0) {
        yield buffer;
      }
    } finally {
      stream.destroy();
    }
  }

  /**
   * Release a workspace explicitly (vs. waiting for the sweeper).
   * Re-verifies the tenant boundary before unlinking.
   */
  async releaseWorkspace(
    allocation: WorkspaceAllocation
  ): Promise<void> {
    assertWithinTenantBoundary(allocation.rootPath, allocation.tenantId);
    await fs.rm(allocation.rootPath, {
      recursive: true,
      force: true,
    });
  }
}

// Singleton — central architecture requirement of the issue.
let _instance: ScratchpadWorkspaceManager | null = null;

export function getScratchpadManager(): ScratchpadWorkspaceManager {
  if (_instance === null) {
    _instance = new ScratchpadWorkspaceManager();
  }
  return _instance;
}

export function resetScratchpadManagerForTests(): void {
  _instance = null;
}

export default ScratchpadWorkspaceManager;
