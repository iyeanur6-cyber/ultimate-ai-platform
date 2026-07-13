import React from 'react';

export const WorkspacePanel: React.FC = () => {
  return (
    <div className="grid grid-cols-3 gap-4 font-mono">
      <div className="col-span-1 border border-gray-800 rounded-xl p-3 bg-[#111827]/40">
        <div className="text-xs font-bold text-gray-500 mb-2 uppercase">File Explorer</div>
        <div className="text-xs text-gray-400 space-y-1.5">
          <div className="text-blue-400 cursor-pointer">📁 src/main/java</div>
          <div className="pl-4 text-gray-300 cursor-pointer">📄 Main.java</div>
          <div className="text-purple-400 cursor-pointer">📄 build.gradle</div>
          <div className="text-amber-400 cursor-pointer">📄 README.md</div>
        </div>
      </div>
      <div className="col-span-2 border border-gray-800 rounded-xl p-4 bg-[#111827]/80 text-gray-300 text-xs min-h-[300px] flex flex-col justify-between">
        <div>
          <span className="text-gray-500">// Monaco Editor Mock Pipeline</span><br />
          <span className="text-purple-400">public class</span> <span className="text-yellow-400">Main</span> {<br />
          <span className="text-purple-400 pl-4">public static void</span> <span className="text-blue-400">main</span>(String[] args) {<br />
          <span className="text-green-400 pl-8">System.out.println</span>(<span className="text-amber-300">"UltimateAI Operational"</span>);<br />
          <span className="pl-4">}</span><br />
          }
        </div>
        <div className="mt-4 p-2 bg-black/40 border border-gray-800 rounded text-[11px] text-green-400">
          ultimate-terminal:> ./gradlew build<br />
          ✔ Build Successful [Latency: 142ms]
        </div>
      </div>
    </div>
  );
};
