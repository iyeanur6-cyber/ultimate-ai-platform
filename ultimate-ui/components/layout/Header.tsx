import React from 'react';

export const Header: React.FC = () => {
  return (
    <header className="h-14 bg-[#111827] border-b border-gray-800/60 px-4 flex items-center justify-between backdrop-blur-md bg-opacity-70 sticky top-0 z-50">
      <div className="flex items-center space-x-6">
        <div className="flex items-center space-x-2">
          <div className="w-3.5 h-3.5 rounded bg-blue-500 shadow-lg shadow-blue-500/50" />
          <span className="font-bold text-base tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-indigo-400">UltimateAI</span>
          <span className="text-[9px] bg-blue-500/10 text-blue-400 border border-blue-500/20 px-1.5 py-0.5 rounded font-mono font-bold">OS KERNEL</span>
        </div>
        <nav className="hidden md:flex items-center space-x-1 text-xs font-medium text-gray-400">
          <span className="px-3 py-1.5 rounded-lg text-gray-100 bg-gray-800/50 border border-gray-700/40">Enterprise Orchestration Management</span>
        </nav>
      </div>
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2 text-[10px] bg-[#1F2937]/40 border border-gray-800 px-2.5 py-1 rounded-full font-mono">
          <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
          <span className="text-gray-500">Node Secure Routing:</span> <span className="text-green-400">Active</span>
        </div>
      </div>
    </header>
  );
};
