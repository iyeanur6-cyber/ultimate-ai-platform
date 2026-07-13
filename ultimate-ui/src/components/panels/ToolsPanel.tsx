import React from 'react';
export const ToolsPanel: React.FC = () => (
  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 font-mono text-xs">
    {[
      { name: 'Java Compiler Server', ver: 'JDK 21', status: 'Healthy', latency: '42ms' },
      { name: 'SoX Audio Processing Worker', ver: 'v14.4.2', status: 'Task Running', latency: '120ms' },
      { name: 'LibreOffice Suite Sandbox', ver: 'v24.2', status: 'Standby', latency: '10ms' },
      { name: 'Android Build Toolchain', ver: 'SDK 34', status: 'Ready', latency: '5ms' }
    ].map((tool, i) => (
      <div key={i} className="p-4 bg-[#111827]/60 border border-gray-800 rounded-xl flex justify-between items-center">
        <div>
          <div className="text-gray-200 font-bold">{tool.name}</div>
          <div className="text-gray-500 text-[10px]">{tool.ver}</div>
        </div>
        <div className="text-right">
          <span className={`text-[10px] px-2 py-0.5 rounded ${tool.status.includes('Running') ? 'bg-amber-500/10 text-amber-400' : 'bg-green-500/10 text-green-400'}`}>{tool.status}</span>
          <div className="text-gray-500 text-[10px] mt-1">Latency: {tool.latency}</div>
        </div>
      </div>
    ))}
  </div>
);
