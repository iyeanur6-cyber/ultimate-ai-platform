import React from 'react';
import { NavigationItem, SystemStats } from '../../types';
import { sidebarNavigation } from '../../config/navigation';

interface SidebarProps {
  activeTab: NavigationItem;
  setActiveTab: (tab: NavigationItem) => void;
  stats: SystemStats;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab, stats }) => {
  return (
    <aside className="w-64 bg-[#111827] bg-opacity-40 border-r border-gray-800/50 flex flex-col justify-between overflow-y-auto">
      <div className="p-3 space-y-1">
        <div className="text-[10px] font-bold text-gray-500 tracking-wider px-3 uppercase mb-2">Control Matrix</div>
        {sidebarNavigation.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-all ${
                activeTab === item.id 
                  ? 'bg-blue-600/15 text-blue-400 border border-blue-500/20 shadow-sm' 
                  : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/40 border border-transparent'
              }`}
            >
              <div className="flex items-center space-x-3">
                <Icon size={14} className="opacity-80 group-hover:scale-105 transition-transform" />
                <span>{item.label}</span>
              </div>
              {item.id === 'containers' && (
                <span className="text-[9px] bg-green-500/10 text-green-400 border border-green-500/20 px-1.5 py-0.5 rounded font-mono font-bold">{stats.activeContainers} LIVE</span>
              )}
            </button>
          );
        })}
      </div>

      <div className="p-4 border-t border-gray-800/60 bg-[#111827]/60 backdrop-blur-sm space-y-3 font-mono text-[11px]">
        <div className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">Hardware Monitor</div>
        <div className="space-y-2">
          <div>
            <div className="flex justify-between text-gray-400 mb-1">
              <span>⚡ CPU Cluster</span>
              <span className="text-blue-400">{stats.cpu}%</span>
            </div>
            <div className="w-full bg-gray-800 h-1 rounded-full overflow-hidden">
              <div className="bg-blue-500 h-full transition-all duration-300" style={{ width: `${stats.cpu}%` }} />
            </div>
          </div>
          <div>
            <div className="flex justify-between text-gray-400 mb-1">
              <span>🧠 Memory Matrix</span>
              <span className="text-purple-400">{stats.ram}%</span>
            </div>
            <div className="w-full bg-gray-800 h-1 rounded-full overflow-hidden">
              <div className="bg-purple-500 h-full transition-all duration-300" style={{ width: `${stats.ram}%` }} />
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
};
