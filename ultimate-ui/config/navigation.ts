export interface NavItemConfig {
  id: string;
  label: string;
  icon: string;
}

export const sidebarNavigation: NavItemConfig[] = [
  { id: 'chat', label: 'AI Chat Core', icon: 'Terminal' },
  { id: 'projects', label: 'IDE Workspace', icon: 'Folder' },
  { id: 'files', label: 'Storage Mounts', icon: 'FileText' },
  { id: 'memory', label: 'Memory Layers', icon: 'Cpu' },
  { id: 'tools', label: 'Virtual Tools', icon: 'Wrench' },
  { id: 'containers', label: 'Docker Units', icon: 'Box' },
  { id: 'browser', label: 'Headless Chromium', icon: 'Globe' },
  { id: 'models', label: 'Model Metrics', icon: 'BarChart' },
  { id: 'logs', label: 'Event Logs', icon: 'Scroll' },
  { id: 'settings', label: 'Kernel Rules', icon: 'Settings' }
];
