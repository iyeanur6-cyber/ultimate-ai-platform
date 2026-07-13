import React from 'react';
export const ContainersPanel: React.FC = () => (
  <div className="space-y-3 font-mono text-xs">
    <div className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">Managed Volatile Workspaces</div>
    <div className="p-4 bg-[#111827]/40 border border-gray-800 rounded-xl space-y-2">
      <div className="flex justify-between text-gray-300"><span>📦 container-sox-audio-01</span> <span className="text-amber-400">Processing Track #15</span></div>
      <div className="flex justify-between text-gray-300"><span>📦 container-chromium-headless</span> <span className="text-green-400">Idle Lease Available</span></div>
      <div className="flex justify-between text-gray-300"><span>📦 container-libreoffice-worker</span> <span className="text-green-400">Secure Mount Isolated</span></div>
    </div>
  </div>
);
