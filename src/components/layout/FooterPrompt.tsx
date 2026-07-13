import React, { KeyboardEvent } from 'react';

interface FooterPromptProps {
  prompt: string;
  setPrompt: (val: string) => void;
  isDispatching: boolean;
  handleKeyDown: (e: KeyboardEvent<HTMLTextAreaElement>) => void;
  dispatchPipeline: () => void;
}

export const FooterPrompt: React.FC<FooterPromptProps> = ({
  prompt, setPrompt, isDispatching, handleKeyDown, dispatchPipeline
}) => (
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
);
