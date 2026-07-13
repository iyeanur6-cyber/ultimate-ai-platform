export interface ChatRequest {
  prompt: string;
  contextId?: string;
}

export async function sendPrompt(request: ChatRequest): Promise<Response> {
  return fetch("/api/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function fetchSystemMetrics(): Promise<Response> {
  return fetch("/api/system/metrics", {
    method: "GET",
    headers: { "Accept": "application/json" }
  });
}
