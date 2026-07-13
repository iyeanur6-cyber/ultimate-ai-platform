import React, { useState, useEffect } from 'react';

const panelCardStyle = {
  padding: '16px',
  backgroundColor: '#111827',
  border: '1px solid #1F2937',
  borderRadius: '12px',
  color: '#9CA3AF'
};

// গ্লোবাল ইভেন্ট লিসেনার ট্রিগার যার মাধ্যমে FooterPrompt এর মেসেজ এখানে রিয়েল-টাইম শো করবে
export const ChatPanel: React.FC = () => {
  const [messages, setMessages] = useState<{role: string, text: string}[]>([
    { role: 'system', text: '[ultimateAI] Core Infrastructure Listening via Reverse Proxy...' }
  ]);

  useEffect(() => {
    const handleNewMessage = (e: any) => {
      setMessages(prev => [...prev, { role: 'user', text: e.detail.prompt }]);
      
      // ব্যাকএন্ড থেকে রেসপন্স আসার মকিং স্ট্রিমিং অবজেক্ট স্টেট
      setTimeout(() => {
        setMessages(prev => [...prev, { 
          role: 'assistant', 
          text: `[Spring Boot Core]: Message accepted. Executing VSCodium container compile task node pipeline.` 
        }]);
      }, 800);
    };

    window.addEventListener('ai-live-prompt', handleNewMessage);
    return () => window.removeEventListener('ai-live-prompt', handleNewMessage);
  }, []);

  return (
    <div style={{ ...panelCardStyle, display: 'flex', flexDirection: 'column', gap: '12px', height: '300px', overflowY: 'auto', fontFamily: 'monospace' }}>
      {messages.map((msg, idx) => (
        <div key={idx} style={{ 
          padding: '8px 12px', 
          borderRadius: '8px',
          backgroundColor: msg.role === 'user' ? '#1E3A8A' : msg.role === 'system' ? '#374151' : '#065F46',
          color: msg.role === 'user' ? '#60A5FA' : msg.role === 'system' ? '#9CA3AF' : '#34D399',
          alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
          maxWidth: '80%'
        }}>
          <strong>{msg.role.toUpperCase()}:</strong> {msg.text}
        </div>
      ))}
    </div>
  );
};

// বাকি স্ট্যাটিক মক প্যানেলগুলো অপরিবর্তিত রাখা হলো
export const MemoryPanel: React.FC = () => (
  <div style={{ ...panelCardStyle, fontFamily: 'monospace', lineHeight: '2' }}>
    <div style={{ color: '#A78BFA' }}>→ pgvector (Long-Term Context DB): 94% Allocated</div>
    <div style={{ color: '#60A5FA' }}>→ Redis Cluster (Session Buffer): 82% Active</div>
  </div>
);

export const BrowserPanel: React.FC = () => <div style={panelCardStyle}>🌐 Chromium Sandbox Node: Headless VNC pipeline active on isolated core.</div>;
export const ToolsPanel: React.FC = () => <div style={panelCardStyle}>🔧 Virtual Core Tool Registry: SoX Audio Engine, LibreOffice Worker fully active.</div>;
export const ContainersPanel: React.FC = () => <div style={panelCardStyle}>📦 Active Docker Virtual Workspaces under lease credit restriction.</div>;
export const LogsPanel: React.FC = () => <div style={{ ...panelCardStyle, backgroundColor: '#030712', color: '#6B7280', fontFamily: 'monospace' }}>[INFO] Intercepted outbound socket query to verify SSRF host constraints.</div>;
export const ModelsPanel: React.FC = () => <div style={panelCardStyle}>📊 Router Metrics: Ollama Llama3 Cluster [Online] | Gemini Routing Layer [Standby]</div>;
export const SettingsPanel: React.FC = () => <div style={panelCardStyle}>⚙️ Kernel Context Isolation Profile: Rule Level 4 (Active Anti-DNS-Rebinding)</div>;
export const FilesPanel: React.FC = () => <div style={panelCardStyle}>📄 File Mount Registry: Workspace transaction records synchronized with Spring core.</div>;
