import { SystemStats } from '../types';

export interface ChatRequest {
  prompt: string;
  contextId?: string;
}

export interface ChatResponse {
  message: string;
  conversationId: string;
  executionTimeMs?: number;
}

export async function sendPrompt(request: ChatRequest): Promise<ChatResponse> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`API error: ${response.statusText}`);
  return response.json();
}

export async function fetchSystemMetrics(): Promise<SystemStats> {
  const response = await fetch("/api/system/metrics");
  if (!response.ok) throw new Error("Metrics fetch failure");
  return response.json();
}
