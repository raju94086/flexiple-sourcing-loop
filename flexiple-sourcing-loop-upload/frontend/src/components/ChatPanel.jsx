import React, { useEffect, useRef, useState } from 'react'

export default function ChatPanel({ chat, onSend, busy, frozen, voteSentence }) {
  const [text, setText] = useState('')
  const logRef = useRef(null)

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight
  }, [chat, busy])

  // typing wins over the thumbs
  const outgoing = text.trim() || voteSentence

  const send = () => {
    if (!outgoing || busy || frozen) return
    onSend(outgoing)
    setText('')
  }

  return (
    <div className="card chat">
      <div className="card-head">
        <div className="card-title">
          <span>◗</span> Refinement
        </div>
      </div>

      <div className="chat-log" ref={logRef}>
        {chat.map((m, i) => (
          <div className={'msg ' + (m.role === 'recruiter' ? 'msg-recruiter' : 'msg-app')} key={i}>
            <div className="bubble">{m.text}</div>

            {m.changes?.length > 0 && (
              <div className="changes">
                <div className="changes-title">What I changed</div>
                {m.changes.map((c, j) => (
                  <div className="change" key={j}>
                    <span className="change-field">{c.field}</span>{' '}
                    <span className="change-value">{c.change}</span>
                    <div className="change-why">{c.why}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}

        {busy && (
          <div className="msg msg-app">
            <div className="bubble" style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
              <span className="spinner spinner-dark" />
              Re-reading your feedback and re-running the search…
            </div>
          </div>
        )}
      </div>

      {!frozen && (
        <div className="chat-composer">
          <textarea
            value={text}
            disabled={busy}
            placeholder={
              voteSentence
                ? `Send "${voteSentence}" — or type something more specific`
                : 'e.g. 1 is too junior, 2 and 4 are right — I want more payments background'
            }
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                send()
              }
            }}
          />
          <div className="composer-foot">
            <span className="vote-summary">
              {voteSentence ? voteSentence : 'Enter to send · Shift+Enter for a new line'}
            </span>
            <button className="btn btn-primary btn-sm" onClick={send} disabled={busy || !outgoing}>
              {busy ? <span className="spinner" /> : 'Send'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
