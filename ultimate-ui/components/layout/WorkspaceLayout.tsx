import React, { useState, KeyboardEvent, useEffect } from 'react';
import { NavigationItem, SystemStats } from '../../types';
import { ChatPanel } from '../panels/ChatPanel';
import { WorkspacePanel } from '../panels/WorkspacePanel';
import { MemoryPanel } from '../panels/MemoryPanel';
import { BrowserPanel } from '../panels/BrowserPanel';
import { ToolsPanel } from '../panels/ToolsPanel';
import { ContainersPanel } from '../panels/ContainersPanel';
import { LogsPanel } from '../panels/LogsPanel';
import { ModelsPanel, SettingsPanel, FilesPanel } from '../panels/GenericPanels';

export const WorkspaceLayout: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavigationItem>('chat');
  const [prompt, setPrompt] = useState<string>('');
  const [isDispatching, setIsDispatching] = useState<boolean>(false);
  
  // Dynamic Live System Metrics (REST/WebSocket Node Simulation)
  const [stats, setStats] = useState<SystemStats>({
    cpu: 34,
    ram: 51,
    activeContainers: 4,
    runningTasks: 1,
    tokenUsage: 14245
  });

  // Polling simulated REST API to query Spring Boot metrics endpoint (/api/system)
  useEffect(() => {
    const interval = setInterval(() => {
      setStats(prev => ({
        ...prev,
        cpu: Math.floor(Math.random() * (48 - 28 + 1)) + 28,
        ram: Math.floor(Math.random() * (62 - 50 + 1)) + 50,
      }));
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  // Spring Boot Real API Dispatch Pipeline Integration
  const dispatchPipeline = async () => {
    if (!prompt.trim()) return;
    setIsDispatching(true);
    try {
      console.log("Dispatching instruction context to UltimateAI core gateway...");
      const response = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt: prompt.trim() })
      });
      if (response.ok) {
        console.log("Instruction processed inside sandbox container.");
      }
    } catch (error) {
      console.error("API link network boundary constraint error:", error);
    } finally {
      setPrompt('');
      setIsDispatching(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      dispatchPipeline();
    }
  };

  // Component Control Router Router Matrix Mapping
  const renderActivePanel = () => {
    switch (activeTab) {
      case 'chat': return <ChatPanel />;
      case 'projects': return <WorkspacePanel />;
      case 'memory': return <MemoryPanel />;
      case 'browser': return <BrowserPanel />;
      case 'tools': return <ToolsPanel />;
      case 'containers': return <ContainersPanel />;
      case 'logs': return <LogsPanel />;
      case 'models': return <ModelsPanel />;
      case 'settings': return <SettingsPanel />;
      case 'files': return <FilesPanel />;
      default: return <div className="text-xs text-gray-500 font-mono">Node Template Attached.</div>;
    }
  };

  return (
    <div className="min-h-screen bg-[#0B1120] text-gray-100 flex flex-col font-sans select-none antialiased">
      
      {/* Structural Header Grid Node */}
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

      {/* Main Core View Area */}
      <div className="flex-1 flex overflow-hidden">
        
        {/* Left Control Bar (Lucide Icon Mocks Included) */}
        <aside className="w-64 bg-[#111827] bg-opacity-40 border-r border-gray-800/50 flex flex-col justify-between overflow-y-auto">
          <div className="p-3 space-y-1">
            <div className="text-[10px] font-bold text-gray-500 tracking-wider px-3 uppercase mb-2">Control Matrix</div>
            
            {[
              { id: 'chat', label: 'AI Chat Core', icon: '⚡' },
              { id: 'projects', label: 'IDE Workspace', icon: '📂' },
              { id: 'files', label: 'Storage Mounts', icon: '📝' },
              { id: 'memory', label: 'Memory Layers', icon: '🧠' },
              { id: 'tools', label: 'Virtual Tools', icon: '🔧' },
              { id: 'containers', label: 'Docker Units', icon: '📦' },
              { id: 'browser', label: 'Headless Chromium', icon: '🌐' },
              { id: 'models', label: 'Model Metrics', icon: '📊' },
              { id: 'logs', label: 'Event Logs', icon: '📜' },
              { id: 'settings', label: 'Kernel Rules', icon: '⚙️' }
            ].map((item) => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id as NavigationItem)}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-all ${
                  activeTab === item.id 
                    ? 'bg-blue-600/15 text-blue-400 border border-blue-500/20 shadow-sm' 
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/40 border border-transparent'
                }`}
              >
                <div className="flex items-center space-x-3">
                  <span className="text-sm">{item.icon}</span>
                  <span>{item.label}</span>
                </div>
                {item.id === 'containers' && (
                  <span className="text-[9px] bg-green-500/10 text-green-400 border border-green-500/20 px-1.5 py-0.5 rounded font-mono font-bold">{stats.activeContainers} LIVE</span>
                )}
              </button>
            ))}
          </div>

          {/* Live Cluster Metrics Progress Bar Indicators */}
          <div className="p-4 border-t border-gray-800/60 bg-[#111827]/60 backdrop-blur-sm space-y-3 font-mono text-[11px]">
            <div className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Cluster Hardware Metrics</div>
            <div className="space-y-2">
              <div>
                <div className="flex justify-between text-gray-400 mb-1">
                  <span>⚡ CPU Load Matrix</span>
                  <span className="text-blue-400">{stats.cpu}%</span>
                </div>
                <div className="w-full bg-gray-800 h-1 rounded-full overflow-hidden">
                  <div className="bg-blue-500 h-full transition-all duration-300" style={{ width: `${stats.cpu}%` }} />
                </div>
              </div>
              <div>
                <div className="flex justify-between text-gray-400 mb-1">
                  <span>🧠 Volatile Memory</span>
                  <span className="text-purple-400">{stats.ram}%</span>
                </div>
                <div className="w-full bg-gray-800 h-1 rounded-full overflow-hidden">
                  <div className="bg-purple-500 h-full transition-all duration-300" style={{ width: `${stats.ram}%` }} />
                </div>
              </div>
            </div>
          </div>
        </aside>

        {/* Central Core Content Router Console Grid Area */}
        <main className="flex-1 flex flex-col bg-[#0B1120] relative overflow-hidden">
          <div className="flex-1 p-5 overflow-y-auto space-y-5">
            <div className="bg-[#111827]/40 border border-gray-800/60 rounded-2xl p-5 backdrop-blur-xl shadow-xl min-h-[420px] flex flex-col">
              <div className="flex items-center justify-between border-b border-gray-800/60 pb-2.5 mb-4">
                <h2 className="text-sm font-semibold tracking-wide text-gray-200 capitalize flex items-center space-x-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
                  <span>Subsystem Instance Panel: {activeTab}</span>
                </h2>
                <span className="text-[10px] bg-gray-800/60 px-2 py-0.5 border border-gray-800 rounded text-gray-500 font-mono">Isolated Volatile Sandbox</span>
              </div>
              
              <div className="flex-1">
                {renderActivePanel()}
              </div>
            </div>
          </div>

          {/* Interactive Multiline Input Dispatch Control Segment */}
          <footer className="p-4 border-t border-gray-800/60 bg-[#111827]/30 backdrop-blur-md">
            <div className="max-w-4xl mx-auto space-y-2">
              <div className="flex items-center space-x-3 text-[10px] font-mono text-gray-500 px-1">
                <span className="text-green-500">✔ VSCodium Core</span>
                <span className="text-green-500">✔ Chromium Isolation</span>
                <span className="text-blue-400">✔ SoX Audio Pipeline</span>
                <span className="text-purple-400">✔ LibreOffice Bridge</span>
              </div>
              <div className="bg-[#111827] border border-gray-800 rounded-xl p-2 flex flex-col shadow-inner space-y-2">
                <textarea 
                  rows={2}
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={isDispatching}
                  placeholder={isDispatching ? "Core engine processing operation task context..." : "Enter system instructions... (Press Enter to Execute, Shift+Enter for newline)"} 
                  className="bg-transparent text-xs text-gray-100 placeholder-gray-600 focus:outline-none flex-1 px-2 resize-none"
                />
                <div className="flex justify-end pt-1 border-t border-gray-900/60">
                  <button 
                    onClick={dispatchPipeline}
                    disabled={isDispatching}
                    className="bg-blue-600 hover:bg-blue-500 disabled:bg-gray-800 disabled:text-gray-600 text-white font-medium text-[11px] px-4 py-1.5 rounded-lg shadow-md transition-all transform active:scale-95"
                  >
                    {isDispatching ? "Processing..." : "Dispatch Task"}
                  </button>
                </div>
              </div>
            </div>
          </footer>
        </main>

        {/* Right Metric Watchdog Panel Tracking Nodes */}
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
            <div className="text-gray-400 font-semibold">🔄 Active Watchdog Tasks</div>
            <div className="text-[10px] text-gray-500 space-y-1 pt-0.5">
              <div className="flex items-center space-x-2 text-amber-400/90">
                <span className="animate-spin text-[10px]">⚙️</span>
                <span className="truncate">SoX processing audio buffers...</span>
              </div>
            </div>
          </div>
        </aside>

      </div>
    </div>
  );
};

export default WorkspaceLayout;
