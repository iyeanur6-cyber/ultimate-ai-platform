import React from 'react';
import { createRoot } from 'react-dom/client';
import { WorkspaceLayout } from './components/layout/WorkspaceLayout';
import './index.css';

const container = document.getElementById('root');
if (container) {
  const root = createRoot(container);
  root.render(
    <React.StrictMode>
      <WorkspaceLayout />
    </React.StrictMode>
  );
}
