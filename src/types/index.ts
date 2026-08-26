export type NavigationItem = 
  | 'chat' 
  | 'projects' 
export type {
  ToolIdentifier,
  TenantContext,
  WorkspaceAllocation,
} from '../services/scratchpadManager';

export {
  getScratchpadManager,
  resetScratchpadManagerForTests,
  ScratchpadWorkspaceManager,
} from '../services/scratchpadManager';

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
