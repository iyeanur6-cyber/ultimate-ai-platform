import React, { useState, KeyboardEvent } from 'react';
import { NavigationItem, SystemStats } from '../../types';
import { ChatPanel } from '../panels/ChatPanel';
import { WorkspacePanel } from '../panels/WorkspacePanel';
import { MemoryPanel } from '../panels/MemoryPanel';

export const WorkspaceLayout: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavigationItem>('chat');
  const [prompt, setPrompt] = useState<string>('');
  
  const [stats] = useState<SystemStats>({
    cpu: 42,
    ram: 58,
    activeContainers: 4,
    runningTasks: 2,
    tokenUsage: 14245
  });

  // Handle Multi-line Enter Execution & Shift+Enter Newline
  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (prompt.trim()) {
        console.log("Executing core engine task:", prompt);
        setPrompt('');
      }
    }
  };

  // Render Panel Dynamic Routing Matrix
  const renderActivePanel = () => {
    switch (activeTab) {
      case 'chat':
        return <ChatPanel />;
      case 'projects':
        return <WorkspacePanel />;
      case 'memory':
        return <MemoryPanel />;
      default:
        return (
          <div className="p-4 border border-dashed border-gray-800 rounded-xl text-center text-xs text-gray-500 font-mono">
            Subsystem operational node [{activeTab}] view template attached. Fully wired via virtual REST gateway.
          </div>
        );
    }
  };

  return (
    <div className="min-h-screen bg-[#0B1120] text-gray-100 flex flex-col font-sans select-none antialiased">
      
      {/* Top Navigation Frame */}
      <header className="h-14 bg-[#111827] border-b border-gray-800/60 px-4 flex items-center justify-between backdrop-blur-md bg-opacity-70 sticky top-0 z-50">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-2">
            <div className="w-3.5 h-3.5 rounded bg-blue-500 animate-pulse" />
            <span className="font-bold text-base tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-indigo-400">UltimateAI</span>
            <span className="text-[9px] bg-blue-500/10 text-blue-400 border border-blue-500/20 px-1.5 py-0.5 rounded font-mono font-bold">OS v1.0</span>
          </div>
          <nav className="hidden md:flex items-center space-x-1 text-xs font-medium text-gray-400">
            <button className="px-3 py-1.5 rounded-lg text-gray-100 bg-gray-800/50 transition-all">Workspace Dashboard</button>
            <button className="px-3 py-1.5 rounded-lg hover:text-gray-200 hover:bg-gray-800/30 transition-all">Architecture Docs</button>
          </nav>
        </div>
        
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2 text-[10px] bg-[#1F2937]/40 border border-gray-800 px-2.5 py-1 rounded-full font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-ping" />
            <span className="text-gray-500">Router status:</span> <span className="text-green-400">Healthy</span>
          </div>
          <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-600 border border-gray-700 shadow-md" />
        </div>
      </header>

      {/* Main Container Frame */}
      <div className="flex-1 flex overflow-hidden">
        
        {/* Left Control Sidebar */}
        <aside className="w-64 bg-[#111827] bg-opacity-40 border-r border-gray-800/50 flex flex-col justify-between overflow-y-auto">
          <div className="p-3 space-y-1">
            <div className="text-[10px] font-bold text-gray-500 tracking-wider px-3 uppercase mb-2">Control Matrix</div>
            
            {[
              { id: 'chat', label: 'AI Chat Hub', icon: '💬' },
              { id: 'projects', label: 'IDE Workspace', icon: '📁' },
              { id: 'files', label: 'Documents Area', icon: '📝' },
              { id: 'memory', label: 'Memory Inspector', icon: '🧠' },
              { id: 'tools', label: 'Virtual Core Tools', icon: '🔧' },
              { id: 'containers', label: 'Docker Units', icon: '📦' },
              { id: 'browser', label: 'Chromium Engine', icon: '🌐' },
              { id: 'models', label: 'Model Monitors', icon: '📊' },
              { id: 'logs', label: 'Active Event Logs', icon: '📜' },
              { id: 'settings', label: 'System Settings', icon: '⚙️' }
            ].map((item) => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id as NavigationItem)}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-all group ${
                  activeTab === item.id 
                    ? 'bg-blue-600/15 text-blue-400 border border-blue-500/20 shadow-sm' 
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/40 border border-transparent'
                }`}
              >
                <div className="flex items-center space-x-3">
                  <span className="text-sm group-hover:scale-110 transition-transform">{item.icon}</span>
                  <span>{item.label}</span>
                </div>
                {item.id === 'containers' && (
                  <span className="text-[9px] bg-green-500/10 text-green-400 border border-green-500/20 px-1.5 py-0.5 rounded font-mono font-bold">4 LIVE</span>
                )}
              </button>
            ))}
          </div>

          {/* Core Hardware Metrics Dashboard Unit */}
          <div className="p-4 border-t border-gray-800/60 bg-[#111827]/60 backdrop-blur-sm space-y-3 font-mono text-[11px]">
            <div className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Cluster Resource Monitor</div>
            <div className="space-y-2">
              <div>
                <div className="flex justify-between text-gray-400 mb-1">
                  <span>⚡ Compute Cluster</span>
                  <span className="text-blue-400">{stats.cpu}%</span>
                </div>
                <div className="w-full bg-gray-800 h-1 rounded-full overflow-hidden">
                  <div className="bg-blue-500 h-full transition-all duration-500" style={{ width: `${stats.cpu}%` }} />
                </div>
              </div>
              <div>
                <div className="flex justify-between text-gray-400 mb-1">
                  <span>🧠 RAM Allocation</span>
                  <span className="text-purple-400">{stats.ram}%</span>
                </div>
                <div className="w-full bg-gray-800 h-1 rounded-full overflow-hidden">
                  <div className="bg-purple-500 h-full transition-all duration-500" style={{ width: `${stats.ram}%` }} />
                </div>
              </div>
            </div>
          </div>
        </aside>

        {/* Central Orchestration workspace routing */}
        <main className="flex-1 flex flex-col bg-[#0B1120] relative overflow-hidden">
          <div className="flex-1 p-5 overflow-y-auto space-y-5">
            <div className="bg-[#111827]/40 border border-gray-800/60 rounded-2xl p-5 backdrop-blur-xl shadow-xl min-h-[420px] flex flex-col">
              <div className="flex items-center justify-between border-b border-gray-800/60 pb-2.5 mb-4">
                <h2 className="text-sm font-semibold tracking-wide text-gray-200 capitalize flex items-center space-x-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
                  <span>Subsystem Frame: {activeTab}</span>
                </h2>
                <span className="text-[10px] bg-gray-800 px-2 py-0.5 rounded text-gray-500 font-mono">Isolated Container Environment</span>
              </div>
              
              {/* Load Panel Content via Router Matrix */}
              <div className="flex-1">
                {renderActivePanel()}
              </div>
            </div>
          </div>

          {/* Interactive Multi-line Input Pipeline Footer */}
          <footer className="p-4 border-t border-gray-800/60 bg-[#111827]/30 backdrop-blur-md">
            <div className="max-w-4xl mx-auto space-y-2">
              <div className="flex items-center space-x-3 text-[10px] font-mono text-gray-500 px-1">
                <span className="text-green-500">✔ VSCodium Container</span>
                <span className="text-green-500">✔ Chromium Headless</span>
                <span className="text-blue-400">✔ SoX Audio Subsystem</span>
                <span className="text-purple-400">✔ LibreOffice Worker</span>
              </div>
              <div className="bg-[#111827] border border-gray-800 rounded-xl p-2 flex flex-col shadow-inner space-y-2">
                <textarea 
                  rows={2}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="Ask UltimateAI to compile modules, execute VSCodium automation, reduce track noise or audit sandbox state... (Press Enter to Execute, Shift+Enter for newline)" 
                  className="bg-transparent text-xs text-gray-100 placeholder-gray-600 focus:outline-none flex-1 px-2 resize-none"
                />
                <div className="flex justify-end pt-1 border-t border-gray-900">
                  <button className="bg-blue-600 hover:bg-blue-500 text-white font-medium text-[11px] px-4 py-1.5 rounded-lg shadow-md shadow-blue-600/10 transition-all transform active:scale-95">
                    Dispatch Pipeline
                  </button>
                </div>
              </div>
            </div>
          </footer>
        </main>

        {/* Right Metric Context Panel */}
        <aside className="w-72 bg-[#111827] bg-opacity-40 border-l border-gray-800/50 p-4 overflow-y-auto space-y-4 font-mono text-[11px]">
          <div className="text-[10px] font-bold text-gray-500 tracking-wider uppercase">Context Matrix Explorer</div>
          
          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
            <div className="text-gray-400 font-semibold">🧠 Active Memory Stack</div>
            <div className="space-y-1 text-[10px] pt-0.5">
              <div className="flex justify-between text-gray-500"><span>Redis Session:</span> <span className="text-gray-300">82%</span></div>
              <div className="flex justify-between text-gray-500"><span>Vector Store:</span> <span className="text-gray-300">94%</span></div>
            </div>
          </div>

          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
            <div className="text-gray-400 font-semibold">📄 Storage File Mounts</div>
            <div className="text-[10px] space-y-1 pt-0.5">
              <div className="truncate text-blue-400/90 hover:underline cursor-pointer">→ /workspace/Main.java</div>
              <div className="truncate text-purple-400/90 hover:underline cursor-pointer">→ /workspace/report.docx</div>
            </div>
          </div>

          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
            <div className="text-gray-400 font-semibold flex items-center justify-between">
              <span>🔄 Worker Watchdogs</span>
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
            </div>
            <div className="text-[10px] text-gray-500 space-y-1 pt-0.5">
              <div className="flex items-center space-x-2 text-amber-400/90">
                <span className="animate-spin text-[10px]">⚙️</span>
                <span className="truncate">SoX Noise profiling task active</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
};

export default WorkspaceLayout;
