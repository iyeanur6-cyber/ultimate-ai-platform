import React from 'react';
export const Header: React.FC = () => (
  <header className="h-14 bg-[#111827] border-b border-gray-800/60 px-4 flex items-center justify-between backdrop-blur-md bg-opacity-70 sticky top-0 z-50">
    <div className="flex items-center space-x-3">
      <div className="w-3.5 h-3.5 rounded bg-blue-500 shadow-lg shadow-blue-500/50" />
      <span className="font-bold text-base tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-indigo-400 font-sans">UltimateAI OS</span>
    </div>
    <div className="text-[10px] bg-[#1F2937]/40 border border-gray-800 px-2.5 py-1 rounded-full font-mono text-gray-400">
      <span className="w-1.5 h-1.5 rounded-full bg-green-500 inline-block mr-1.5 animate-pulse" />
      Routing Engine: <span className="text-green-400">Secure Isolation</span>
    </div>
  </header>
);
