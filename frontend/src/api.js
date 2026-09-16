const API_BASE = import.meta.env.VITE_API_URL ?? ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {})
    }
  })

  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: 'Erro inesperado na API.' }))
    throw new Error(body.message ?? `Erro HTTP ${response.status}`)
  }

  return response.json()
}

export const api = {
  listEngines: () => request('/api/v1/platform/engines'),
  generateScript: payload => request('/api/v1/scripts/generate', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
