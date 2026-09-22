export class ApiError extends Error {
  constructor(message, retryable) {
    super(message)
    this.retryable = retryable
  }
}

async function post(path, body) {
  let res
  try {
    res = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch (e) {
    throw new ApiError('Could not reach the server. Is the Java backend running on port 8080?', true)
  }

  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch (e) {
    throw new ApiError('The server sent back something unreadable.', true)
  }

  if (!res.ok) {
    throw new ApiError(data?.error || `Request failed (${res.status}).`, Boolean(data?.retryable))
  }
  return data
}

export const api = {
  health: async () => {
    try {
      const res = await fetch('/api/health')
      return await res.json()
    } catch (e) {
      return null
    }
  },
  search: (query) => post('/api/search', { query }),
  refine: (session_id, feedback) => post('/api/refine', { session_id, feedback }),
  edit: (session_id, filters, rubric) => post('/api/edit', { session_id, filters, rubric }),
  freeze: (session_id) => post('/api/freeze', { session_id }),
  unfreeze: (session_id) => post('/api/unfreeze', { session_id }),
}
