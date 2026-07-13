export type NavigationItem = 
  | 'chat' 
  | 'projects' 
  | 'files' 
  | 'memory' 
  | 'tools' 
  | 'containers' 
  | 'browser' 
  | 'models' 
  | 'logs' 
  | 'settings';

export interface SystemStats {
  cpu: number;
  ram: number;
  activeContainers: number;
  runningTasks: number;
  tokenUsage: number;
}
