import React, { useState } from 'react'

const EXAMPLES = [
  'RDS developers with 4-7 years of experience who have worked at startups, for a role based in Bangalore.',
  'Senior frontend engineers in Pune who know React and TypeScript and have shipped design systems.',
  'Data engineers with Spark and Airflow, 5+ years, comfortable in an enterprise environment.',
]

export default function SearchScreen({ onSearch, health, error, onDismissError }) {
  const [text, setText] = useState('')

  const submit = () => {
    if (text.trim()) onSearch(text.trim())
  }

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="landing">
      <div className="landing-inner">
        <div className="eyebrow">
          <span>◈</span> Sourcing
        </div>

        <h1>Describe who you want to hire.</h1>
        <p className="sub">
          Write it the way you would say it out loud. I will turn it into filters and a fit rubric you can
          see and edit, then we refine it together until the shortlist is right.
        </p>

        {error && (
          <div className="error-banner">
            <span>⚠</span>
            <span className="grow">{error.message}</span>
            <button className="btn btn-sm" onClick={onDismissError}>
              Dismiss
            </button>
          </div>
        )}

        {health && !health.has_api_key && (
          <div className="error-banner">
            <span>⚠</span>
            <span className="grow">
              The server has no <span className="mono">GEMINI_API_KEY</span> set, so searches will fail. Set it
              and restart the backend.
            </span>
          </div>
        )}

        <div className="search-box">
          <textarea
            autoFocus
            value={text}
            placeholder="e.g. RDS developers with 4-7 years of experience who have worked at startups, for a role based in Bangalore."
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <div className="search-actions">
            <span className="hint">
              {health ? `${health.profiles} profiles in the talent map` : 'Connecting to the server…'}
            </span>
            <button className="btn btn-primary btn-lg" onClick={submit} disabled={!text.trim()}>
              Search →
            </button>
          </div>
        </div>

        <div className="examples">
          <div className="examples-label">Or start from one of these</div>
          {EXAMPLES.map((ex) => (
            <button key={ex} className="example-chip" onClick={() => setText(ex)}>
              {ex}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
