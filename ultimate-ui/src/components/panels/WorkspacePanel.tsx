import React from 'react';

const javaMockCode = `public class Main {
    public static void main(String[] args) {
        System.out.println("UltimateAI Operational");
    }
}`;

export const WorkspacePanel: React.FC = () => {
  return (
    <div className="grid grid-cols-3 gap-4 font-mono text-xs">
      <div className="col-span-1 border border-gray-800 rounded-xl p-3 bg-[#111827]/40">
        <div className="text-[10px] font-bold text-gray-500 mb-2 uppercase">File Explorer</div>
        <div className="text-gray-400 space-y-1.5">
          <div className="text-blue-400 cursor-pointer">📁 src/main/java</div>
          <div className="pl-4 text-gray-300 cursor-pointer">📄 Main.java</div>
          <div className="text-purple-400 cursor-pointer">📄 build.gradle</div>
          <div className="text-amber-400 cursor-pointer">📄 README.md</div>
        </div>
      </div>
      <div className="col-span-2 border border-gray-800 rounded-xl p-4 bg-[#111827]/80 text-gray-300 min-h-[300px] flex flex-col justify-between">
        <div>
          <div className="text-gray-500 mb-2">// Monaco Editor Engine Active</div>
          <pre className="text-indigo-300 whitespace-pre-wrap font-mono leading-relaxed">
            {javaMockCode}
          </pre>
        </div>
        <div className="mt-4 p-2 bg-black/40 border border-gray-800 rounded text-[11px] text-green-400">
          ultimate-terminal:&gt; ./gradlew build<br />
          ✔ Build Successful [Latency: 142ms]
        </div>
      </div>
    </div>
  );
};
