import React from 'react';
export const LogsPanel: React.FC = () => (
  <div className="bg-gray-950 p-4 border border-gray-800 rounded-xl font-mono text-[11px] text-gray-400 space-y-1 max-h-[300px] overflow-y-auto">
    <div>[2026-07-13 20:58:12] <span className="text-blue-400">INFO</span> ai.ultimate.kernel.Orchestrator - Task budget validated.</div>
    <div>[2026-07-13 20:58:15] <span className="text-amber-400">WARN</span> ai.ultimate.tools.SoX - Post-publication rollback bypassed safely.</div>
    <div>[2026-07-13 20:58:19] <span className="text-green-400">SUCCESS</span> ai.ultimate.security.SSRFGate - DNS query intercepted & isolated.</div>
    <div>[2026-07-13 20:58:22] <span className="text-blue-400">INFO</span> ai.ultimate.gateway - Dispatching VSCodium live build pipeline.</div>
  </div>
);
