import React from 'react';

export const MemoryPanel: React.FC = () => {
  return (
    <div className="space-y-6 font-mono text-xs">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-4 bg-gray-800/20 border border-gray-800 rounded-xl">
          <div className="text-gray-400 mb-2">Session Memory (Redis)</div>
          <div className="w-full bg-gray-800 h-2 rounded-full overflow-hidden">
            <div className="bg-blue-500 h-full" style={{ width: '82%' }} />
          </div>
          <div className="text-right text-[10px] text-gray-500 mt-1">82% Allocation Cache</div>
        </div>
        <div className="p-4 bg-gray-800/20 border border-gray-800 rounded-xl">
          <div className="text-gray-400 mb-2">Long-Term (pgvector)</div>
          <div className="w-full bg-gray-800 h-2 rounded-full overflow-hidden">
            <div className="bg-purple-500 h-full" style={{ width: '94%' }} />
          </div>
          <div className="text-right text-[10px] text-gray-500 mt-1">94% RAG Cluster Base</div>
        </div>
        <div className="p-4 bg-gray-800/20 border border-gray-800 rounded-xl">
          <div className="text-gray-400 mb-2">Context Tokens window</div>
          <div className="text-sm font-bold text-indigo-400 mt-1">14,245 Active</div>
        </div>
      </div>
    </div>
  );
};
