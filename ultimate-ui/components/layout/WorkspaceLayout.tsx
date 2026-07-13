import React, { useState, KeyboardEvent, useEffect } from 'react';
import { NavigationItem, SystemStats } from '../../types';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { RightContextPanel } from './RightContextPanel';
import { FooterPrompt } from './FooterPrompt';
import { sendPrompt } from '../../services/api';

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
  
  const [stats, setStats] = useState<SystemStats>({
    cpu: 34,
    ram: 51,
    activeContainers: 4,
    runningTasks: 1,
    tokenUsage: 14245
  });

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

  const dispatchPipeline = async () => {
    if (!prompt.trim()) return;
    setIsDispatching(true);
    try {
      const response = await sendPrompt({ prompt: prompt.trim() });
      if (response.ok) {
        console.log("Instruction processed safely inside sandbox core.");
      }
    } catch (error) {
      console.error("API Link boundary network constraint error:", error);
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
      default: return <div className="text-xs text-gray-500 font-mono">Node Template Active.</div>;
    }
  };

  return (
    <div className="min-h-screen bg-[#0B1120] text-gray-100 flex flex-col font-sans select-none antialiased">
      <Header />
      <div className="flex-1 flex overflow-hidden">
        <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} stats={stats} />
        <main className="flex-1 flex flex-col bg-[#0B1120] relative overflow-hidden">
          <div className="flex-1 p-5 overflow-y-auto space-y-5">
            <div className="bg-[#111827]/40 border border-gray-800/60 rounded-2xl p-5 backdrop-blur-xl shadow-xl min-h-[420px] flex flex-col">
              <div className="flex items-center justify-between border-b border-gray-800/60 pb-2.5 mb-4">
                <h2 className="text-sm font-semibold tracking-wide text-gray-200 capitalize flex items-center space-x-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
                  <span>Subsystem Component Panel: {activeTab}</span>
                </h2>
                <span className="text-[10px] bg-gray-800/60 px-2 py-0.5 border border-gray-800 rounded text-gray-500 font-mono">Isolated Sandbox Node</span>
              </div>
              <div className="flex-1">
                {renderActivePanel()}
              </div>
            </div>
          </div>
          <FooterPrompt 
            prompt={prompt} 
            setPrompt={setPrompt} 
            isDispatching={isDispatching} 
            handleKeyDown={handleKeyDown} 
            dispatchPipeline={dispatchPipeline} 
          />
        </main>
        <RightContextPanel stats={stats} />
      </div>
    </div>
  );
};

export default WorkspaceLayout;
