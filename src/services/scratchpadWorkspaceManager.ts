/**
 * ScratchpadWorkspaceManager
 * --------------------------
 * Central, multi-tenant, concurrency-safe workspace and scratchpad manager
 * for the UltimateAI multimedia automation suites (LibreOffice, Chromium,
 * FFmpeg, ImageMagick).
 *
 * Addresses Issue #26:
 *  1. Centralized path resolution. Every workspace path flows through this
 *     module and is named with a cryptographically-secure UUID. Timestamps are
 *     never used for naming (they are predictable and can collide).
 *  2. FD-leak safety. Directory traversals open a handle and always release it
 *     in a finally block (the JS equivalent of try-with-resources / Files.walk).
 *  3. Strict tenant egress. A tenant can only resolve paths inside its own
 *     root; '..' traversal, absolute escapes and symlink escapes are rejected.
 */

import { promises as fs } from 'fs';
import type { Dir, Dirent } from 'fs';
import * as path from 'path';
import { randomUUID } from 'crypto';

/* ------------------------------------------------------------------ */
/* Types                                                               */
/* ------------------------------------------------------------------ */

export type ToolName = 'libreoffice' | 'chromium' | 'ffmpeg' | 'imagemagick';

export interface WorkspaceLayout {
  readonly input: string;
  readonly work: string;
  readonly output: string;
}

export interface WorkspaceHandle {
  readonly id: string;
  readonly tenantId: string;
  readonly tool: ToolName;
  readonly root: string;
  readonly layout: WorkspaceLayout;
  readonly createdAt: number;
}

export interface ScratchpadManagerOptions {
  baseDir: string;
  ttlMs?: number;
  accessMatrix?: Readonly<Record<string, ReadonlyArray<ToolName>>>;
}

export interface SweepReport {
  readonly scanned: number;
  readonly removed: number;
  readonly freed: ReadonlyArray<string>;
}

/* ------------------------------------------------------------------ */
/* Errors                                                              */
/* ------------------------------------------------------------------ */

export class EgressViolationError extends Error {
  constructor(tenantId: string, attempted: string) {
    super(
      `[scratchpad] Egress violation: tenant '${tenantId}' attempted to access '${attempted}' outside its workspace`,
    );
    this.name = 'EgressViolationError';
  }
}

export class AccessDeniedError extends Error {
  constructor(tenantId: string, tool: ToolName) {
    super(`[scratchpad] Tenant '${tenantId}' is not permitted to use '${tool}'`);
    this.name = 'AccessDeniedError';
  }
}

/* ------------------------------------------------------------------ */
/* Concurrency: serialises mutating operations (JS stand-in for locks) */
/* ------------------------------------------------------------------ */

class AsyncMutex {
  private queue: Promise<void> = Promise.resolve();

  runExclusive<T>(fn: () => Promise<T>): Promise<T> {
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    const previous = this.queue;
    this.queue = previous.then(() => held);
    return previous.then(async () => {
      try {
        return await fn();
      } finally {
        release();
      }
    });
  }
}

/* ------------------------------------------------------------------ */
/* Internal helpers                                                    */
/* ------------------------------------------------------------------ */

const SAFE_SEGMENT = /^[A-Za-z0-9_-]{1,128}$/;

function assertSafeSegment(value: string, label: string): void {
  if (!SAFE_SEGMENT.test(value)) {
    throw new Error(`[scratchpad] Unsafe ${label}: '${value}'`);
  }
}

// Cryptographically-random workspace id. Deliberately not a timestamp.
function secureWorkspaceId(): string {
  return randomUUID();
}

// FD-leak-safe recursive listing. Each directory handle is opened and then
// guaranteed to be closed in a finally block, even if a nested read throws.
async function listRecursive(root: string): Promise<string[]> {
  const found: string[] = [];
  const dir: Dir = await fs.opendir(root);
  try {
    let entry: Dirent | null;
    while ((entry = await dir.read()) !== null) {
      const full = path.join(root, entry.name);
      found.push(full);
      if (entry.isDirectory()) {
        found.push(...(await listRecursive(full)));
      }
    }
  } finally {
    await dir.close();
  }
  return found;
}

// Resolves 'candidate' strictly inside 'base'. Rejects '..' traversal,
// absolute escapes and symlinks that point outside the tenant root.
async function resolveInside(base: string, candidate: string, tenantId: string): Promise<string> {
  const realBase = await fs.realpath(base);
  const target = path.resolve(realBase, candidate);
  const rel = path.relative(realBase, target);
  if (rel === '..' || rel.startsWith('..' + path.sep) || path.isAbsolute(rel)) {
    throw new EgressViolationError(tenantId, candidate);
  }
  try {
    const realTarget = await fs.realpath(target);
    const realRel = path.relative(realBase, realTarget);
    if (realRel === '..' || realRel.startsWith('..' + path.sep) || path.isAbsolute(realRel)) {
      throw new EgressViolationError(tenantId, candidate);
    }
    return realTarget;
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code === 'ENOENT') {
      return target;
    }
    throw err;
  }
}

/* ------------------------------------------------------------------ */
/* Manager                                                             */
/* ------------------------------------------------------------------ */

export class ScratchpadManager {
  private readonly baseDir: string;
  private readonly ttlMs: number;
  private readonly accessMatrix?: Readonly<Record<string, ReadonlyArray<ToolName>>>;
  private readonly mutex = new AsyncMutex();
  private readonly active = new Map<string, WorkspaceHandle>();

  constructor(options: ScratchpadManagerOptions) {
    if (!path.isAbsolute(options.baseDir)) {
      throw new Error('[scratchpad] baseDir must be absolute');
    }
    this.baseDir = options.baseDir;
    this.ttlMs = options.ttlMs ?? 60 * 60 * 1000;
    this.accessMatrix = options.accessMatrix;
  }

  private tenantRoot(tenantId: string): string {
    assertSafeSegment(tenantId, 'tenantId');
    return path.join(this.baseDir, tenantId);
  }

  private assertToolAllowed(tenantId: string, tool: ToolName): void {
    if (!this.accessMatrix) return;
    const allowed = this.accessMatrix[tenantId];
    if (!allowed || !allowed.includes(tool)) {
      throw new AccessDeniedError(tenantId, tool);
    }
  }

  async allocate(tenantId: string, tool: ToolName): Promise<WorkspaceHandle> {
    assertSafeSegment(tenantId, 'tenantId');
    this.assertToolAllowed(tenantId, tool);
    return this.mutex.runExclusive(async () => {
      const tenantRoot = this.tenantRoot(tenantId);
      await fs.mkdir(tenantRoot, { recursive: true });
      const id = secureWorkspaceId();
      await resolveInside(tenantRoot, id, tenantId);
      const root = path.join(tenantRoot, id);
      const layout = {
        input: path.join(root, 'input'),
        work: path.join(root, 'work'),
        output: path.join(root, 'output'),
      };
      await fs.mkdir(layout.input, { recursive: true });
      await fs.mkdir(layout.work, { recursive: true });
      await fs.mkdir(layout.output, { recursive: true });
      const handle: WorkspaceHandle = {
        id,
        tenantId,
        tool,
        root,
        layout,
        createdAt: Date.now(),
      };
      this.active.set(id, handle);
      return handle;
    });
  }

  async resolvePath(handle: WorkspaceHandle, ...segments: string[]): Promise<string> {
    const tenantRoot = this.tenantRoot(handle.tenantId);
    const candidate = path.join(handle.id, ...segments);
    return resolveInside(tenantRoot, candidate, handle.tenantId);
  }

  async listWorkspaces(tenantId: string): Promise<string[]> {
    assertSafeSegment(tenantId, 'tenantId');
    const tenantRoot = this.tenantRoot(tenantId);
    try {
      return await listRecursive(tenantRoot);
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code === 'ENOENT') return [];
      throw err;
    }
  }

  async release(handle: WorkspaceHandle): Promise<void> {
    return this.mutex.runExclusive(async () => {
      const tenantRoot = this.tenantRoot(handle.tenantId);
      const target = await resolveInside(tenantRoot, handle.id, handle.tenantId);
      await fs.rm(target, { recursive: true, force: true });
      this.active.delete(handle.id);
    });
  }

  async sweep(now: number = Date.now()): Promise<SweepReport> {
    return this.mutex.runExclusive(async () => {
      let scanned = 0;
      const freed: string[] = [];
      const tenants = await this.safeReadDir(this.baseDir);
      for (const tenant of tenants) {
        if (!tenant.isDirectory()) continue;
        const tenantId = tenant.name;
        if (!SAFE_SEGMENT.test(tenantId)) continue;
        const tenantRoot = path.join(this.baseDir, tenantId);
        const workspaces = await this.safeReadDir(tenantRoot);
        for (const ws of workspaces) {
          if (!ws.isDirectory()) continue;
          scanned += 1;
          const wsPath = path.join(tenantRoot, ws.name);
          try {
            const stat = await fs.stat(wsPath);
            if (now - stat.mtimeMs > this.ttlMs) {
              await fs.rm(wsPath, { recursive: true, force: true });
              this.active.delete(ws.name);
              freed.push(wsPath);
            }
          } catch {
            // removed concurrently; ignore
          }
        }
      }
      return { scanned, removed: freed.length, freed };
    });
  }

  private async safeReadDir(dir: string): Promise<Dirent[]> {
    try {
      return await fs.readdir(dir, { withFileTypes: true });
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code === 'ENOENT') return [];
      throw err;
    }
  }
}

/* ------------------------------------------------------------------ */
/* Factory + default singleton                                         */
/* ------------------------------------------------------------------ */

let defaultManager: ScratchpadManager | null = null;

export function createScratchpadManager(options: ScratchpadManagerOptions): ScratchpadManager {
  return new ScratchpadManager(options);
}

export function getScratchpadManager(): ScratchpadManager {
  if (!defaultManager) {
    const baseDir = process.env.SCRATCHPAD_BASE_DIR ?? path.join(process.cwd(), '.scratchpad');
    defaultManager = createScratchpadManager({ baseDir });
  }
  return defaultManager;
}
