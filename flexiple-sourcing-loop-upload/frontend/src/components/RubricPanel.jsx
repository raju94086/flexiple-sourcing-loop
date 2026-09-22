import React, { useState } from 'react'

// edits commit on blur so typing doesn't fight with a re-render
export default function RubricPanel({ rubric, onChange, disabled }) {
  const [addingFlag, setAddingFlag] = useState(false)
  const [flagDraft, setFlagDraft] = useState('')

  const criteria = rubric.criteria || []
  const redFlags = rubric.red_flags || []

  const updateCriterion = (index, key, value) => {
    const next = criteria.map((c, i) => (i === index ? { ...c, [key]: value } : c))
    onChange({ ...rubric, criteria: next })
  }

  const removeCriterion = (index) => {
    onChange({ ...rubric, criteria: criteria.filter((_, i) => i !== index) })
  }

  const totalWeight = criteria.reduce((sum, c) => sum + (Number(c.weight) || 0), 0)

  return (
    <div className="card">
      <div className="card-head">
        <div className="card-title">
          <span>◎</span> Fit rubric
        </div>
        <span className={'badge' + (totalWeight === 100 ? '' : ' badge-accent')}>{totalWeight} pts</span>
      </div>

      <div className="card-body">
        <div className="field-row">
          <div className="section-label">What we are hiring for</div>
          <div
            className="criterion-desc"
            style={{ fontSize: 13, color: 'var(--text)' }}
            contentEditable={!disabled}
            suppressContentEditableWarning
            onBlur={(e) => onChange({ ...rubric, role_summary: e.target.textContent.trim() })}
          >
            {rubric.role_summary}
          </div>
        </div>

        <div className="field-row">
          <div className="section-label">Scored on</div>
          {criteria.length === 0 && <span className="empty-value">No criteria yet</span>}

          {criteria.map((c, i) => (
            <div className="criterion" key={i}>
              <div className="criterion-head">
                <span
                  className="criterion-name"
                  contentEditable={!disabled}
                  suppressContentEditableWarning
                  onBlur={(e) => updateCriterion(i, 'name', e.target.textContent.trim())}
                >
                  {c.name}
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
                  <span
                    className="criterion-weight"
                    contentEditable={!disabled}
                    suppressContentEditableWarning
                    onBlur={(e) =>
                      updateCriterion(i, 'weight', parseInt(e.target.textContent, 10) || 0)
                    }
                  >
                    {c.weight}
                  </span>
                  {!disabled && (
                    <button className="token-x" title="Remove criterion" onClick={() => removeCriterion(i)}>
                      ×
                    </button>
                  )}
                </span>
              </div>
              <div
                className="criterion-desc"
                contentEditable={!disabled}
                suppressContentEditableWarning
                onBlur={(e) => updateCriterion(i, 'description', e.target.textContent.trim())}
              >
                {c.description}
              </div>
            </div>
          ))}
        </div>

        <div className="field-row">
          <div className="section-label">Red flags</div>
          {redFlags.length === 0 && !addingFlag && <span className="empty-value">None noted</span>}

          {redFlags.map((flag, i) => (
            <div className="redflag" key={i}>
              <span className="dot">▲</span>
              <span style={{ flex: 1 }}>{flag}</span>
              {!disabled && (
                <button
                  className="token-x"
                  title="Remove"
                  onClick={() => onChange({ ...rubric, red_flags: redFlags.filter((_, x) => x !== i) })}
                >
                  ×
                </button>
              )}
            </div>
          ))}

          {!disabled &&
            (addingFlag ? (
              <input
                className="token-input"
                style={{ width: '100%', marginTop: 6 }}
                autoFocus
                value={flagDraft}
                onChange={(e) => setFlagDraft(e.target.value)}
                onBlur={() => {
                  const v = flagDraft.trim()
                  if (v) onChange({ ...rubric, red_flags: [...redFlags, v] })
                  setFlagDraft('')
                  setAddingFlag(false)
                }}
                onKeyDown={(e) => e.key === 'Enter' && e.target.blur()}
              />
            ) : (
              <button className="token-add" style={{ marginTop: 6 }} onClick={() => setAddingFlag(true)}>
                + add a red flag
              </button>
            ))}
        </div>
      </div>
    </div>
  )
}
