import React from 'react'

function scoreClass(score) {
  if (score >= 75) return 'score-strong'
  if (score >= 50) return 'score-mid'
  return 'score-weak'
}

export default function ProfileCard({ item, rank, vote, onVote, readOnly, delay = 0 }) {
  const p = item.profile

  return (
    <div
      className={'profile' + (vote === 'yes' ? ' voted-yes' : vote === 'no' ? ' voted-no' : '')}
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="profile-top">
        <div className="rank">{rank}</div>

        <div className="profile-id">
          <div className="profile-name">{p.name}</div>
          <div className="profile-title">
            {p.current_title} at {p.current_company}
          </div>
        </div>

        <div className="score">
          <div className={'score-value ' + scoreClass(item.score)}>{item.score}</div>
          <div className="score-label">fit</div>
        </div>
      </div>

      {item.reason && <div className="reason">{item.reason}</div>}

      {(item.evidence?.length > 0 || item.concerns?.length > 0) && (
        <div className="evidence">
          {(item.evidence || []).map((e, i) => (
            <span className="chip" key={'e' + i}>
              {e}
            </span>
          ))}
          {(item.concerns || []).map((c, i) => (
            <span className="chip chip-concern" key={'c' + i}>
              ▲ {c}
            </span>
          ))}
        </div>
      )}

      <div className="profile-foot">
        <div className="facts">
          <span>
            <b>{p.years_experience}</b> yrs
          </span>
          <span>{p.location}</span>
          <span>{p.current_company_type}</span>
          <span>{(p.skills || []).slice(0, 4).join(' · ')}</span>
        </div>

        {!readOnly && (
          <div className="vote">
            <button
              className={'vote-btn' + (vote === 'yes' ? ' on-yes' : '')}
              onClick={() => onVote(vote === 'yes' ? null : 'yes')}
            >
              ✓ Match
            </button>
            <button
              className={'vote-btn' + (vote === 'no' ? ' on-no' : '')}
              onClick={() => onVote(vote === 'no' ? null : 'no')}
            >
              ✕ No
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
