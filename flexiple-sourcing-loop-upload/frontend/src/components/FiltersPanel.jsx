import React, { useState } from 'react'

function TokenList({ values, onChange, disabled, muted, addLabel = '+ add', emptyText }) {
  const [adding, setAdding] = useState(false)
  const [draft, setDraft] = useState('')

  const commit = () => {
    const v = draft.trim()
    if (v && !values.includes(v)) onChange([...values, v])
    setDraft('')
    setAdding(false)
  }

  return (
    <div className="tokens">
      {values.length === 0 && !adding && <span className="empty-value">{emptyText}</span>}

      {values.map((v) => (
        <span className={'token' + (muted ? ' muted' : '')} key={v}>
          {v}
          {!disabled && (
            <button
              className="token-x"
              title="Remove"
              onClick={() => onChange(values.filter((x) => x !== v))}
            >
              ×
            </button>
          )}
        </span>
      ))}

      {!disabled &&
        (adding ? (
          <input
            className="token-input"
            autoFocus
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === 'Enter') commit()
              if (e.key === 'Escape') {
                setDraft('')
                setAdding(false)
              }
            }}
          />
        ) : (
          <button className="token-add" onClick={() => setAdding(true)}>
            {addLabel}
          </button>
        ))}
    </div>
  )
}

export default function FiltersPanel({ filters, onChange, disabled, matched, total }) {
  const set = (key, value) => onChange({ ...filters, [key]: value })

  const years = (key, placeholder) => (
    <input
      className="years-input"
      type="number"
      min="0"
      disabled={disabled}
      placeholder={placeholder}
      value={filters[key] ?? ''}
      onChange={(e) => set(key, e.target.value === '' ? null : Number(e.target.value))}
    />
  )

  return (
    <div className="card">
      <div className="card-head">
        <div className="card-title">
          <span>⛃</span> Objective filters
        </div>
        <span className="badge">
          {matched} of {total}
        </span>
      </div>

      <div className="card-body">
        <div className="field-row">
          <div className="section-label">Must have these skills</div>
          <TokenList
            values={filters.required_skills || []}
            onChange={(v) => set('required_skills', v)}
            disabled={disabled}
            emptyText="No skill is mandatory"
          />
        </div>

        <div className="field-row">
          <div className="section-label">Nice to have · not filtered on</div>
          <TokenList
            values={filters.preferred_skills || []}
            onChange={(v) => set('preferred_skills', v)}
            disabled={disabled}
            muted
            emptyText="None"
          />
        </div>

        <div className="field-row">
          <div className="section-label">Years of experience</div>
          <div className="years-row">
            {years('min_years_experience', 'any')}
            <span className="hint">to</span>
            {years('max_years_experience', 'any')}
            <span className="hint">years</span>
          </div>
        </div>

        <div className="field-row">
          <div className="section-label">Location</div>
          <TokenList
            values={filters.locations || []}
            onChange={(v) => set('locations', v)}
            disabled={disabled}
            emptyText="Anywhere"
          />
        </div>

        <div className="field-row">
          <div className="section-label">Company background · now or previously</div>
          <TokenList
            values={filters.company_types || []}
            onChange={(v) => set('company_types', v)}
            disabled={disabled}
            emptyText="Any company type"
          />
        </div>

        <div className="field-row">
          <div className="section-label">Title contains</div>
          <TokenList
            values={filters.title_keywords || []}
            onChange={(v) => set('title_keywords', v)}
            disabled={disabled}
            emptyText="Any title"
          />
        </div>
      </div>
    </div>
  )
}
