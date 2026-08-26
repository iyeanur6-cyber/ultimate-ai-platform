import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import * as fs from 'fs/promises';
import * as path from 'path';
import * as os from 'os';

import {
  getScratchpadManager,
  resetScratchpadManagerForTests,
  ScratchpadWorkspaceManager,
} from './scratchpadManager';

describe('ScratchpadWorkspaceManager', () => {
  let tmpBase: string;
  let previousBase: string | undefined;

  beforeEach(async () => {
    resetScratchpadManagerForTests();
    tmpBase = await fs.mkdtemp(
      path.join(os.tmpdir(), 'scratch-test-')
    );
    previousBase = process.env.ULTIMATE_AI_SCRATCH_BASE;
    process.env.ULTIMATE_AI_SCRATCH_BASE = tmpBase;
  });

  afterEach(async () => {
    if (previousBase === undefined) {
      delete process.env.ULTIMATE_AI_SCRATCH_BASE;
    } else {
      process.env.ULTIMATE_AI_SCRATCH_BASE = previousBase;
    }
    await fs.rm(tmpBase, { recursive: true, force: true });
    resetScratchpadManagerForTests();
  });

  it('rejects timestamp-based collision by allocating unique UUIDs in bulk',
    async () => {
    const mgr = getScratchpadManager();
    mgr.registerTenant({
      tenantId: 'acme',
      subnet: '10.0.0.0/24',
      allowedTools: ['ffmpeg'],
    });

    const ids = new Set<string>();
    for (let i = 0; i < 500; i++) {
      const alloc = await mgr.allocateWorkspace('acme', 'ffmpeg');
      expect(ids.has(alloc.workspaceId)).toBe(false);
      ids.add(alloc.workspaceId);
      // Each ID must look like a v4 UUID, not a timestamp.
      expect(alloc.workspaceId).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
      );
    }
  });

  it('rejects invalid tenant IDs (path traversal defence)', async () => {
    const mgr = getScratchpadManager();
    mgr.registerTenant({
      tenantId: 'safe',
      subnet: '10.0.0.0/24',
      allowedTools: ['libreoffice'],
    });

    await expect(
      mgr.allocateWorkspace('../etc', 'libreoffice')
    ).rejects.toThrow(/Rejected tenant ID/);
  });

  it('enforces egress boundary when resolving tool paths', async () => {
    const mgr = getScratchpadManager();
    mgr.registerTenant({
      tenantId: 'alpha',
      subnet: '10.0.1.0/24',
      allowedTools: ['chromium'],
    });
    mgr.registerTenant({
      tenantId: 'beta',
      subnet: '10.0.2.0/24',
      allowedTools: ['chromium'],
    });

    const a = await mgr.allocateWorkspace('alpha', 'chromium');
    await mgr.allocateWorkspace('beta', 'chromium');

    // alpha cannot escape into beta's tree via `..`.
    expect(() =>
      mgr.resolveToolPath(a, '..', '..', 'beta')
    ).toThrow(/Egress violation/);
  });

  it('enforces access matrix across tenant subnets', async () => {
    const mgr = getScratchpadManager();
    mgr.registerTenant({
      tenantId: 'restricted',
      subnet: '10.0.3.0/24',
      allowedTools: ['libreoffice'],
    });

    await expect(
      mgr.allocateWorkspace('restricted', 'ffmpeg')
    ).rejects.toThrow(/not permitted to use tool/);
  });

  it('walkDirectory does not leak file descriptors (early return)', async () => {
    const mgr = getScratchpadManager();
    mgr.registerTenant({
      tenantId: 'walker',
      subnet: '10.0.4.0/24',
      allowedTools: ['imagemagick'],
    });

    const alloc = await mgr.allocateWorkspace('walker', 'imagemagick');
    for (let i = 0; i < 50; i++) {
      await fs.writeFile(
        path.join(alloc.rootPath, `file-${i}.tmp`),
        String(i)
      );
    }

    // Consume only one entry then bail — must not throw on close.
    const iter = mgr.walkDirectory(alloc.rootPath, 'walker');
    const first = await iter.next();
    expect(first.done).toBe(false);
    // Returning early must not crash — `finally` closes the Dir handle.
    if (typeof (iter as any).return === 'function') {
      await (iter as any).return();
    }

    // A second full walk afterwards should still succeed (FDs were released).
    let count = 0;
    for await (const _ of mgr.walkDirectory(alloc.rootPath, 'walker')) {
      count++;
    }
    expect(count).toBeGreaterThan(0);
  });

  it('sweepExpired removes only old workspaces', async () => {
    const mgr = getScratchpadManager();
    mgr.registerTenant({
      tenantId: 'gc',
      subnet: '10.0.5.0/24',
      allowedTools: ['ffmpeg'],
    });

    const fresh = await mgr.allocateWorkspace('gc', 'ffmpeg');
    const stale = await mgr.allocateWorkspace('gc', 'ffmpeg');

    // Backdate the stale workspace metadata.
    const staleMeta = path.join(stale.rootPath, '.workspace-meta.json');
    await fs.writeFile(
      staleMeta,
      JSON.stringify({
        ...JSON.parse(await fs.readFile(staleMeta, 'utf-8')),
        createdAt: Date.now() - 1000 * 60 * 60,
      })
    );

    const removed = await mgr.sweepExpired('gc', 1000 * 60);
    expect(removed).toBe(1);

    await expect(fs.stat(fresh.rootPath)).resolves.toBeDefined();
    await expect(fs.stat(stale.rootPath)).rejects.toThrow();
  });
});
