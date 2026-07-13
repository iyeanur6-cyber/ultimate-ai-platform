import React, { useState } from 'react';
import { NavigationItem, SystemStats } from '../../types';

interface WorkspaceLayoutProps {
  children: React.ReactNode;
}

export const WorkspaceLayout: React.FC<WorkspaceLayoutProps> = ({ children }) => {
  const [activeTab, setActiveTab] = useState<NavigationItem>('chat');
  
  // Enterprise Live System Stats State
  const [stats] = useState<SystemStats>({
    cpu: 42,
    ram: 58,
    activeContainers: 4,
    runningTasks: 2,
    tokenUsage: 14245
  });

  return (
    <div className="min-h-screen bg-[#0B1120] text-gray-100 flex flex-col font-sans select-none antialiased">
      
      {/* ┌───────────────────────────────────────────────────────────────┐ */}
      {/* │ UltimateAI Logo      Workspace      Docs      Settings  User  │ */}
      {/* └───────────────────────────────────────────────────────────────┘ */}
      <header className="h-14 bg-[#111827] border-b border-gray-800/60 px-4 flex items-center justify-between backdrop-blur-md bg-opacity-70 sticky top-0 z-50">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-2">
            <div className="w-4 h-4 rounded bg-blue-500 animate-pulse" />
            <span className="font-bold text-lg tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-indigo-400">UltimateAI</span>
            <span className="text-[10px] bg-blue-500/10 text-blue-400 border border-blue-500/20 px-1.5 py-0.5 rounded font-mono">PRO</span>
          </div>
          <nav className="hidden md:flex items-center space-x-1 text-sm font-medium text-gray-400">
            <button className="px-3 py-1.5 rounded-lg text-gray-100 bg-gray-800/50 transition-all">Workspace</button>
            <button className="px-3 py-1.5 rounded-lg hover:text-gray-200 hover:bg-gray-800/30 transition-all">Docs</button>
            <button className="px-3 py-1.5 rounded-lg hover:text-gray-200 hover:bg-gray-800/30 transition-all">Changelog</button>
          </nav>
        </div>
        
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2 text-xs bg-[#1F2937]/40 border border-gray-800 px-2.5 py-1 rounded-full font-mono">
            <span className="w-2 h-2 rounded-full bg-green-500" />
            <span className="text-gray-400">Ollama:</span> <span className="text-green-400">Online</span>
          </div>
          <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-blue-500 to-purple-600 border border-gray-700 cursor-pointer shadow-lg hover:scale-105 transition-transform" />
        </div>
      </header>

      {/* Main Core View Grid Container */}
      <div className="flex-1 flex overflow-hidden">
        
        {/* ┌───────────────┐ */}
        {/* │ Navigation    │ (Left Sidebar) */}
        {/* └───────────────┘ */}
        <aside className="w-64 bg-[#111827] bg-opacity-40 border-r border-gray-800/50 flex flex-col justify-between overflow-y-auto">
          <div className="p-3 space-y-1">
            <div className="text-[11px] font-bold text-gray-500 tracking-wider px-3 uppercase mb-2">Navigation</div>
            
            {[
              { id: 'chat', label: 'Chats', icon: '💬' },
              { id: 'projects', label: 'Workspace IDE', icon: '📁' },
              { id: 'files', label: 'Documents', icon: '📝' },
              { id: 'memory', label: 'Memory Visualizer', icon: '🧠' },
              { id: 'tools', label: 'Virtual Tools', icon: '🔧' },
              { id: 'containers', label: 'Docker Units', icon: '📦' },
              { id: 'browser', label: 'Chromium Engine', icon: '🌐' },
              { id: 'models', label: 'Model Monitor', icon: '📊' },
              { id: 'logs', label: 'System Logs', icon: '📜' },
              { id: 'settings', label: 'Settings', icon: '⚙️' }
            ].map((item) => (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id as NavigationItem)}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-sm font-medium transition-all group ${
                  activeTab === item.id 
                    ? 'bg-blue-600/15 text-blue-400 border border-blue-500/20 shadow-inner' 
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/40 border border-transparent'
                }`}
              >
                <div className="flex items-center space-x-3">
                  <span className="text-base group-hover:scale-110 transition-transform">{item.icon}</span>
                  <span>{item.label}</span>
                </div>
                {item.id === 'containers' && (
                  <span className="text-[10px] bg-green-500/10 text-green-400 border border-green-500/20 px-1.5 py-0.5 rounded font-mono font-bold">{stats.activeContainers} LIVE</span>
                )}
              </button>
            ))}
          </div>

          {/* ┌───────────────┐ */}
          {/* │ System Stats  │ (Left Bottom Panel) */}
          {/* └───────────────┘ */}
          <div className="p-4 border-t border-gray-800/60 bg-[#111827]/60 backdrop-blur-sm space-y-3 font-mono text-xs">
            <div className="text-[11px] font-bold text-gray-500 uppercase tracking-wider">System Resource Node</div>
            <div className="space-y-2">
              <div>
                <div className="flex justify-between text-gray-400 mb-1">
                  <span>⚡ CPU Cluster</span>
                  <span className="text-blue-400">{stats.cpu}%</span>
                </div>
                <div className="w-full bg-gray-800 h-1.5 rounded-full overflow-hidden">
                  <div className="bg-blue-500 h-full rounded-full transition-all duration-500" style={{ width: `${stats.cpu}%` }} />
                </div>
              </div>
              <div>
                <div className="flex justify-between text-gray-400 mb-1">
                  <span>🧠 RAM Matrix</span>
                  <span className="text-purple-400">{stats.ram}%</span>
                </div>
                <div className="w-full bg-gray-800 h-1.5 rounded-full overflow-hidden">
                  <div className="bg-purple-500 h-full rounded-full transition-all duration-500" style={{ width: `${stats.ram}%` }} />
                </div>
              </div>
            </div>
          </div>
        </aside>

        {/* ┌───────────────────────────────────────────────┐ */}
        {/* │                Chat Workspace                 │ (Center Panel Matrix) */}
        {/* └───────────────────────────────────────────────┘ */}
        <main className="flex-1 flex flex-col bg-[#0B1120] relative overflow-hidden">
          <div className="flex-1 p-6 overflow-y-auto space-y-6">
            
            {/* Context Panel Content Router Area */}
            <div className="bg-[#111827]/40 border border-gray-800/60 rounded-2xl p-6 backdrop-blur-xl shadow-2xl min-h-[400px]">
              <div className="flex items-center justify-between border-b border-gray-800/60 pb-3 mb-4">
                <h2 className="text-md font-semibold tracking-wide text-gray-200 capitalize flex items-center space-x-2">
                  <span className="w-2 h-2 rounded-full bg-blue-500 animate-ping" />
                  <span>Active Node Context: {activeTab}</span>
                </h2>
                <span className="text-[11px] font-mono text-gray-500">Node Cluster: Secure Isolation Mode</span>
              </div>
              
              {/* Dynamic Subsystem Load Display */}
              <div className="text-sm text-gray-400 space-y-4">
                <p>🚀 UltimateAI Kernel routing engine initialized. Systems fully synchronized via background Termux pipes.</p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                  <div className="p-4 bg-[#1F2937]/30 border border-gray-800/40 rounded-xl space-y-1">
                    <div className="text-xs text-gray-500 uppercase font-mono">VSCodium Dev Server</div>
                    <div className="text-sm font-semibold text-gray-300 font-mono">Status: Connected to API Layer (Port 8080)</div>
                  </div>
                  <div className="p-4 bg-[#1F2937]/30 border border-gray-800/40 rounded-xl space-y-1">
                    <div className="text-xs text-gray-500 uppercase font-mono">LibreOffice Document Automation</div>
                    <div className="text-sm font-semibold text-gray-300 font-mono">Status: Sandbox Workspace Isolated</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Prompt Input Box + Tool Status Bottom Segment */}
          <footer className="p-4 border-t border-gray-800/60 bg-[#111827]/30 backdrop-blur-md">
            <div className="max-w-4xl mx-auto space-y-2">
              <div className="flex items-center space-x-3 text-[11px] font-mono text-gray-500 px-1">
                <span className="text-green-500">✔ VSCodium</span>
                <span className="text-green-500">✔ Chromium</span>
                <span className="text-blue-500">✔ SoX Audio</span>
                <span className="text-purple-500">✔ LibreOffice</span>
              </div>
              <div className="bg-[#111827] border border-gray-800 rounded-xl p-2 flex items-center justify-between shadow-inner">
                <input 
                  type="text" 
                  placeholder="Ask UltimateAI to code, run VSCodium pipelines, manipulate assets or audit endpoints..." 
                  className="bg-transparent text-sm text-gray-100 placeholder-gray-600 focus:outline-none flex-1 px-3"
                />
                <button className="bg-blue-600 hover:bg-blue-500 text-white font-medium text-xs px-4 py-2 rounded-lg shadow-lg shadow-blue-600/20 transition-all transform active:scale-95">
                  Execute Command
                </button>
              </div>
            </div>
          </footer>
        </main>

        {/* ┌──────────────────────────────┐ */}
        {/* │ Right Sidebar (Context Panel)│ */}
        {/* └──────────────────────────────┘ */}
        <aside className="w-80 bg-[#111827] bg-opacity-40 border-l border-gray-800/50 p-4 overflow-y-auto space-y-5 font-mono text-xs">
          <div className="text-[11px] font-bold text-gray-500 tracking-wider uppercase">Context Panel Matrix</div>
          
          {/* Current Memory Visual Segment */}
          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
            <div className="text-gray-400 font-semibold flex items-center space-x-2">
              <span>🧠 Current Memory Layers</span>
            </div>
            <div className="space-y-1.5 pt-1 text-[11px]">
              <div className="flex justify-between text-gray-500"><span>Redis Session Cache:</span> <span className="text-gray-300">82%</span></div>
              <div className="flex justify-between text-gray-500"><span>Long-Term Vector DB:</span> <span className="text-gray-300">94%</span></div>
            </div>
          </div>

          {/* Active Documents Metadata Segment */}
          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
            <div className="text-gray-400 font-semibold">📄 Active Document Mounts</div>
            <div className="text-gray-500 text-[11px] space-y-1 pt-1">
              <div className="truncate text-blue-400/90 cursor-pointer hover:underline">→ /workspace/Main.java</div>
              <div className="truncate text-purple-400/90 cursor-pointer hover:underline">→ /workspace/report.docx</div>
            </div>
          </div>

          {/* Token Usage Metric Tracker */}
          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl text-[11px]">
            <div className="text-gray-400 font-semibold font-sans text-xs">Conversation Token Stats</div>
            <div className="flex justify-between pt-1 text-gray-500">
              <span>Token Account Window:</span>
              <span className="text-indigo-400 font-bold">{stats.tokenUsage} tkn</span>
            </div>
            <div className="flex justify-between text-gray-500">
              <span>Compute Lease Cost:</span>
              <span className="text-amber-400 font-bold">14.2 Credits</span>
            </div>
          </div>

          {/* Running Tasks Watchdog Track */}
          <div className="space-y-2 bg-[#111827]/60 border border-gray-800/60 p-3 rounded-xl">
            <div className="text-gray-400 font-semibold flex items-center justify-between">
              <span>🔄 Running Async Tasks</span>
              <span className="w-2 h-2 rounded-full bg-amber-500 animate-ping" />
            </div>
            <div className="text-[11px] text-gray-500 space-y-1.5 pt-1">
              <div className="flex items-center space-x-2 text-amber-400/90">
                <span className="animate-spin text-xs">⚙️</span>
                <span className="truncate">SoX Noise Profile Reduction</span>
              </div>
            </div>
          </div>

        </aside>
      </div>
    </div>
  );
};

export default WorkspaceLayout;
