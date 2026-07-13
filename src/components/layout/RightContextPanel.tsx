import React from 'react';
import { SystemStats } from '../../types';

interface RightContextPanelProps {
  stats: SystemStats;
}

export const RightContextPanel: React.FC<RightContextPanelProps> = ({ stats }) => (
  <aside className="w-72 bg-[#111827] bg-opacity-40 border-l border-gray-800/50 p-4 overflow-y-auto space-y-4 font-mono text-[11px]">
    <div className="text-[10px] font-bold text-gray-500 tracking-wider uppercase">Context Matrix Engine</div>
    <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
      <div className="text-gray-400 font-semibold">🧠 Memory Layer State</div>
      <div className="space-y-1 text-[10px] pt-0.5">
        <div className="flex justify-between text-gray-500"><span>Redis Window Cache:</span> <span className="text-gray-300">82%</span></div>
        <div className="flex justify-between text-gray-500"><span>Vector Database Base:</span> <span className="text-gray-300">94%</span></div>
      </div>
    </div>
    <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
      <div className="text-gray-400 font-semibold flex items-center justify-between">
        <span>🔄 Worker Watchdogs</span>
        <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
      </div>
      <div className="text-[10px] text-gray-500 space-y-1 pt-0.5">
        <div className="flex items-center space-x-2 text-amber-400/90">
          <span>⚙️</span>
          <span className="truncate">SoX processing audio buffers...</span>
        </div>
      </div>
    </div>
  </aside>
);
