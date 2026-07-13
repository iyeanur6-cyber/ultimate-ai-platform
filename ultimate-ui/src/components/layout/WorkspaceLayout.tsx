import React, { useState, KeyboardEvent, useEffect } from 'react';
import { NavigationItem, SystemStats } from '../../types';
import { sendPrompt } from '../../services/api';
import { WorkspacePanel } from '../panels/WorkspacePanel';
import { 
  ChatPanel, MemoryPanel, BrowserPanel, ToolsPanel, 
  ContainersPanel, LogsPanel, ModelsPanel, SettingsPanel, FilesPanel 
} from '../panels/Panels';

const panels: Record<NavigationItem, React.ComponentType> = {
  chat: ChatPanel, projects: WorkspacePanel, memory: MemoryPanel, browser: BrowserPanel,
  tools: ToolsPanel, containers: ContainersPanel, logs: LogsPanel, models: ModelsPanel,
  settings: SettingsPanel, files: FilesPanel,
};

export const WorkspaceLayout: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavigationItem>('chat');
  const [prompt, setPrompt] = useState<string>('');
  const [isDispatching, setIsDispatching] = useState<boolean>(false);
  const [stats, setStats] = useState<SystemStats>({ cpu: 34, ram: 51, activeContainers: 4, runningTasks: 1, tokenUsage: 14245 });

  useEffect(() => {
    const interval = setInterval(() => {
      setStats(prev => ({
        ...prev,
        cpu: Math.floor(Math.random() * (45 - 25 + 1)) + 25,
        ram: Math.floor(Math.random() * (60 - 50 + 1)) + 50,
      }));
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleDispatch = async () => {
    if (!prompt.trim()) return;
    const currentPrompt = prompt.trim();
    setPrompt('');
    setIsDispatching(true);

    // ১. চ্যাট উইন্ডোতে মেসেজ পুশ করার জন্য কাস্টম ইভেন্ট ফায়ার
    window.dispatchEvent(new CustomEvent('ai-live-prompt', { detail: { prompt: currentPrompt } }));

    try {
      // ২. সরাসরি আমাদের রিভার্স প্রক্সি দিয়ে স্প্রিং বুটের /api/chat এন্ডপয়েন্ট কল করা
      const data = await sendPrompt({ prompt: currentPrompt });
      console.log("Spring Core Framework Pipeline Return Verified:", data);
    } catch (err) {
      console.error("API Link fallback constraint (バックエンドが起動していない可能性):", err);
    } finally {
      setIsDispatching(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleDispatch();
    }
  };

  const ActivePanel = panels[activeTab];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', backgroundColor: '#0B1120', color: '#F3F4F6', fontFamily: 'sans-serif', overflow: 'hidden' }}>
      <header style={{ height: '56px', backgroundColor: '#111827', borderBottom: '1px solid #1F2937', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ width: '12px', height: '12px', borderRadius: '50%', backgroundColor: '#3B82F6' }} />
          <span style={{ fontWeight: 'bold' }}>UltimateAI OS Kernel</span>
        </div>
        <div style={{ fontSize: '11px', color: '#34D399', fontFamily: 'monospace' }}>API PORT PROXY: LINKED</div>
      </header>

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        <aside style={{ width: '240px', backgroundColor: '#111827', borderRight: '1px solid #1F2937', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '12px' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <div style={{ fontSize: '10px', fontWeight: 'bold', color: '#4B5563', padding: '0 8px', marginBottom: '8px' }}>CONTROL MATRIX</div>
            {(Object.keys(panels) as NavigationItem[]).map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                style={{
                  width: '100%', textAlign: 'left', padding: '8px 12px', borderRadius: '8px', fontSize: '12px', fontWeight: '500', border: 'none', cursor: 'pointer',
                  backgroundColor: activeTab === tab ? '#1E3A8A' : 'transparent',
                  color: activeTab === tab ? '#3B82F6' : '#9CA3AF'
                }}
              >
                {tab.toUpperCase()}
              </button>
            ))}
          </div>
          <div style={{ borderTop: '1px solid #1F2937', paddingTop: '12px', fontFamily: 'monospace', fontSize: '11px', color: '#9CA3AF' }}>
            <div style={{ marginBottom: '8px' }}>⚡ CPU: <span style={{ color: '#3B82F6' }}>{stats.cpu}%</span></div>
            <div>🧠 RAM: <span style={{ color: '#A78BFA' }}>{stats.ram}%</span></div>
          </div>
        </aside>

        <main style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '20px', overflow: 'hidden' }}>
          <div style={{ flex: 1, backgroundColor: '#111827', border: '1px solid #1F2937', borderRadius: '16px', padding: '20px', overflowY: 'auto' }}>
            <div style={{ borderBottom: '1px solid #1F2937', paddingBottom: '10px', marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '14px', fontWeight: '600' }}>Active Node: {activeTab.toUpperCase()}</span>
              <span style={{ fontSize: '10px', color: '#4B5563', fontFamily: 'monospace' }}>SECURE TRANSMISSION TUNNEL</span>
            </div>
            <ActivePanel />
          </div>

          <div style={{ marginTop: '16px', display: 'flex', gap: '8px' }}>
            <textarea 
              rows={2}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isDispatching}
              placeholder={isDispatching ? "Streaming tokens from Spring Kernel..." : "Send raw core prompt to Spring Boot Gateway..."} 
              style={{ flex: 1, backgroundColor: '#111827', border: '1px solid #1F2937', borderRadius: '12px', padding: '12px', color: '#F3F4F6', fontSize: '13px', outline: 'none', resize: 'none' }}
            />
            <button 
              onClick={handleDispatch}
              disabled={isDispatching}
              style={{ backgroundColor: '#2563EB', color: '#FFF', border: 'none', borderRadius: '12px', padding: '0 20px', cursor: 'pointer', fontSize: '13px', fontWeight: 'bold' }}
            >
              Dispatch
            </button>
          </div>
        </main>
      </div>
    </div>
  );
};

export default WorkspaceLayout;
