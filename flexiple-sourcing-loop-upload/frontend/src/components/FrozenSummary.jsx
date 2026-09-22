import React from 'react'
import FiltersPanel from './FiltersPanel.jsx'
import RubricPanel from './RubricPanel.jsx'

export default function FrozenSummary({ session, onReopen, onNewSearch }) {
  const results = session.results || []

  return (
    <div className="frozen-wrap">
      <div className="frozen-hero">
        <div className="seal">✓</div>
        <h1>Search frozen</h1>
        <p>
          “{session.query}” · {session.round} round{session.round === 1 ? '' : 's'} of refinement ·{' '}
          {results.length} candidate{results.length === 1 ? '' : 's'} shortlisted from {session.total_profiles}
        </p>
      </div>

      <div className="frozen-grid">
        <FiltersPanel
          filters={session.filters}
          onChange={() => {}}
          disabled
          matched={session.matched_count}
          total={session.total_profiles}
        />
        <RubricPanel rubric={session.rubric} onChange={() => {}} disabled />
      </div>

      <div className="card">
        <div className="card-head">
          <div className="card-title">
            <span>☰</span> Final ranked shortlist
          </div>
          <span className="badge badge-frozen">frozen</span>
        </div>

        {results.length === 0 && (
          <div className="card-body">
            <span className="empty-value">No candidates matched the frozen filters.</span>
          </div>
        )}

        {results.map((item, i) => (
          <div className="shortlist-row" key={item.profile.id}>
            <div className="rank">{i + 1}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <span className="profile-name" style={{ fontSize: 14.5 }}>
                  {item.profile.name}
                </span>
                <span className="badge">{item.score} fit</span>
              </div>
              <div className="profile-title">
                {item.profile.current_title} at {item.profile.current_company} ·{' '}
                {item.profile.years_experience} yrs · {item.profile.location}
              </div>
              {item.reason && (
                <div className="reason" style={{ marginTop: 8, fontSize: 13 }}>
                  {item.reason}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      <div className="final-actions">
        <button className="btn" onClick={onReopen}>
          ← Reopen and keep refining
        </button>
        <button className="btn btn-primary" onClick={onNewSearch}>
          Start a new search
        </button>
      </div>
    </div>
  )
}
