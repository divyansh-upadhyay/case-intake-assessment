import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

type Field = {
  id: string;
  label: string;
  section: string;
  type: string;
  options?: { code: string; label: string }[];
};

type Catalog = {
  sections: { id: string; label: string }[];
  fields: Field[];
};

type Evidence = {
  quote: string;
  start: number;
  end: number;
};

type FieldValue = {
  status: 'extracted' | 'unknown' | 'conflict';
  value?: unknown;
  confidence?: 'high' | 'medium' | 'low';
  provenance?: Evidence[];
  note?: string;
};

type Case = {
  case_index: number;
  label?: string;
  fields: Record<string, FieldValue>;
};

type Extraction = {
  cases: Case[];
  flags?: string[];
};

type Doc = {
  id: string;
  text: string;
  extraction: Extraction | null;
};

type CaseApiResponse = {
  documentId: string;
  sourceText: string;
  extraction: Extraction | null;
};

type Summary = {
  id: string;
  title: string;
  caseCount: number;
  flags: string[];
};

type Correction = {
  value: unknown;
  reviewed: boolean;
  note: string;
};

type ReviewData = {
  corrections: Record<string, Record<string, Correction>>;
};

const base = '';

function showValue(value: unknown): string {
  if (Array.isArray(value)) return value.join(', ');
  if (value === null || value === undefined) return '—';
  return String(value);
}

function App() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [docs, setDocs] = useState<Summary[]>([]);
  const [selected, setSelected] = useState('D01');
  const [doc, setDoc] = useState<Doc | null>(null);

  const [caseIndex, setCaseIndex] = useState(0);
  const [fieldId, setFieldId] = useState<string | null>(null);

  const [review, setReview] = useState<ReviewData>({ corrections: {} });
  const [draftValue, setDraftValue] = useState('');
  const [draftNote, setDraftNote] = useState('');

  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);

  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    Promise.all([
      fetch(base + '/api/catalog').then(response => {
        if (!response.ok) throw new Error('Unable to load catalog');
        return response.json() as Promise<Catalog>;
      }),
      fetch(base + '/api/documents').then(response => {
        if (!response.ok) throw new Error('Unable to load documents');
        return response.json() as Promise<Summary[]>;
      })
    ])
      .then(([catalogData, documentData]) => {
        setCatalog(catalogData);
        setDocs(documentData);
      })
      .catch(err => setError(String(err.message)));
  }, []);

  useEffect(() => {
    let cancelled = false;

    setDoc(null);
    setReview({ corrections: {} });
    setCaseIndex(0);
    setFieldId(null);
    setEditing(false);
    setDirty(false);
    setError('');
    setMessage('');

    Promise.all([
      fetch(base + '/api/cases/' + selected).then(async response => {
        if (!response.ok) {
          throw new Error(`Unable to load ${selected}: HTTP ${response.status}`);
        }
        return response.json() as Promise<CaseApiResponse>;
      }),

      fetch(base + '/api/cases/' + selected + '/review').then(async response => {
        if (!response.ok) {
          throw new Error(
            `Unable to load review for ${selected}: HTTP ${response.status}`
          );
        }
        return response.json() as Promise<ReviewData>;
      })
    ])
      .then(([caseData, reviewData]) => {
        if (cancelled) return;

        setDoc({
          id: caseData.documentId,
          text: caseData.sourceText,
          extraction: caseData.extraction
        });

        setReview({
          corrections: reviewData?.corrections ?? {}
        });
      })
      .catch(err => {
        if (!cancelled) setError(String(err.message));
      });

    return () => {
      cancelled = true;
    };
  }, [selected]);

  const current = doc?.extraction?.cases[caseIndex];

  const activeField = fieldId && current
    ? current.fields[fieldId]
    : null;

  const correction = fieldId
    ? review.corrections[String(caseIndex)]?.[fieldId]
    : undefined;

  const selectedCatalogField = catalog?.fields.find(
    field => field.id === fieldId
  );

  const spans = useMemo(() => {
    if (!doc || !activeField?.provenance?.length) return null;

    const evidence = activeField.provenance[0];
    const chars = Array.from(doc.text);

    if (
      evidence.start < 0 ||
      evidence.end > chars.length ||
      evidence.start >= evidence.end
    ) {
      return null;
    }

    return [
      chars.slice(0, evidence.start).join(''),
      chars.slice(evidence.start, evidence.end).join(''),
      chars.slice(evidence.end).join('')
    ];
  }, [doc, activeField]);

  useEffect(() => {
    document.querySelector('mark')?.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    });
  }, [spans]);

  function selectField(id: string) {
    setFieldId(id);
    setEditing(false);
    setMessage('');
  }

  function startEditing() {
    if (!fieldId) return;

    const value = correction
      ? correction.value
      : activeField?.value;

    setDraftValue(showValue(value) === '—' ? '' : showValue(value));
    setDraftNote(correction?.note ?? '');
    setEditing(true);
  }

  function updateCorrection(
    id: string,
    value: unknown,
    note: string
  ) {
    const caseKey = String(caseIndex);

    setReview(previous => ({
      corrections: {
        ...previous.corrections,
        [caseKey]: {
          ...(previous.corrections[caseKey] ?? {}),
          [id]: {
            value,
            reviewed: true,
            note
          }
        }
      }
    }));

    setDirty(true);
    setMessage('Changes pending. Click Save Review.');
  }

  function parseValue(raw: string, field?: Field): unknown {
    const trimmed = raw.trim();

    if (!trimmed) return null;

    const type = field?.type?.toLowerCase() ?? '';

    if (type.includes('number') || type.includes('integer')) {
      const number = Number(trimmed);

      if (!Number.isFinite(number)) {
        throw new Error('Please enter a valid number.');
      }

      return number;
    }

    if (type.includes('array') || type.includes('list')) {
      return trimmed.split(',').map(value => value.trim()).filter(Boolean);
    }

    return trimmed;
  }

  function applyEdit() {
    if (!fieldId) return;

    try {
      const parsedValue = parseValue(draftValue, selectedCatalogField);

      updateCorrection(fieldId, parsedValue, draftNote);
      setEditing(false);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  function verifyField() {
    if (!fieldId) return;

    const value = correction
      ? correction.value
      : activeField?.value ?? null;

    updateCorrection(
      fieldId,
      value,
      correction?.note ?? 'Verified against source document'
    );
  }

  async function saveReview() {
    if (!doc || saving) return;

    setSaving(true);
    setError('');
    setMessage('');

    try {
      const response = await fetch(
        base + '/api/cases/' + doc.id + '/review',
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(review)
        }
      );

      if (!response.ok) {
        throw new Error(`Save failed: HTTP ${response.status}`);
      }

      setDirty(false);
      setMessage('Review saved successfully.');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  const reviewedCount = Object.values(review.corrections).reduce(
    (count, fields) =>
      count + Object.values(fields).filter(value => value.reviewed).length,
    0
  );

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon">✳</span>
          <div>
            CASE INTAKE
            <small>REVIEW WORKSPACE</small>
          </div>
        </div>

        <div className="nav-heading">
          DOCUMENT QUEUE <span>{docs.length}</span>
        </div>

        <div className="doc-list">
          {docs.map(item => (
            <button
              key={item.id}
              className={
                'doc-link ' + (selected === item.id ? 'chosen' : '')
              }
              onClick={() => {
                if (
                  dirty &&
                  !window.confirm(
                    'You have unsaved changes. Switch documents anyway?'
                  )
                ) {
                  return;
                }

                setSelected(item.id);
              }}
            >
              <span className="doc-id">{item.id}</span>
              <span className="doc-title">{item.title}</span>
              <span className="case-count">{item.caseCount}</span>
            </button>
          ))}
        </div>

        <div className="side-footer">
          Offline-ready · Catalog-driven
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <div className="eyebrow">
              DOCUMENT REVIEW / {selected}
            </div>

            <h1>Evidence review</h1>

            <p>
              Verify extracted fields against the original source text.
            </p>
          </div>

          <div className="top-pill">
            ● {doc?.extraction ? 'Extraction available' : 'Awaiting extraction'}
          </div>
        </header>

        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}

        {message && (
          <div className="review-message" role="status">
            {message}
          </div>
        )}

        {!doc ? (
          <div className="placeholder">Loading document…</div>
        ) : (
          <div className="workspace">
            <section className="panel document-panel">
              <div className="panel-head">
                <div>
                  <span className="eyebrow">SOURCE DOCUMENT</span>
                  <h2>{selected} · Original report</h2>
                </div>

                <span className="meta">
                  {Array.from(doc.text).length} characters
                </span>
              </div>

              <div className="document-text">
                {spans ? (
                  <>
                    {spans[0]}
                    <mark>{spans[1]}</mark>
                    {spans[2]}
                  </>
                ) : (
                  doc.text
                )}
              </div>
            </section>

            <section className="panel fields-panel">
              <div className="panel-head">
                <div>
                  <span className="eyebrow">
                    STRUCTURED EXTRACTION
                  </span>

                  <h2>Extracted values</h2>
                </div>

                <span className="meta">
                  {catalog?.fields.length ?? 0} fields
                </span>
              </div>

              <div className="review-toolbar">
                <span>
                  <strong>{reviewedCount}</strong> reviewed
                  {dirty ? ' · Unsaved changes' : ''}
                </span>

                <button
                  className="save-review"
                  disabled={!dirty || saving}
                  onClick={saveReview}
                >
                  {saving ? 'Saving…' : 'Save Review'}
                </button>
              </div>

              {!doc.extraction ? (
                <div className="empty">
                  <h3>No extraction yet</h3>
                  <p>
                    Generate model fixtures and run replay to populate
                    this document.
                  </p>
                </div>
              ) : !doc.extraction.cases.length ? (
                <div className="empty">
                  <h3>No valid cases</h3>
                  <p>
                    {doc.extraction.flags?.join(', ') ||
                      'No case identified in this document.'}
                  </p>
                </div>
              ) : (
                <>
                  <div className="case-tabs">
                    {doc.extraction.cases.map((item, index) => (
                      <button
                        key={index}
                        className={caseIndex === index ? 'active' : ''}
                        onClick={() => {
                          setCaseIndex(index);
                          setFieldId(null);
                          setEditing(false);
                        }}
                      >
                        {item.label || 'Case ' + (index + 1)}
                      </button>
                    ))}
                  </div>

                  <div className="field-scroll">
                    {catalog?.sections.map(section => (
                      <div
                        key={section.id}
                        className="field-section"
                      >
                        <h3>{section.label}</h3>

                        {catalog.fields
                          .filter(field => field.section === section.id)
                          .map(field => {
                            const extracted = current?.fields[field.id];

                            const saved =
                              review.corrections[String(caseIndex)]
                                ?.[field.id];

                            const value = saved
                              ? saved.value
                              : extracted?.value;

                            const isSelected = fieldId === field.id;

                            return (
                              <div key={field.id}>
                                <button
                                  className={
                                    'field-row ' +
                                    (isSelected ? 'selected' : '')
                                  }
                                  onClick={() => selectField(field.id)}
                                >
                                  <span className="field-main">
                                    <span className="field-label">
                                      {field.label}
                                    </span>

                                    <strong>
                                      {saved
                                        ? showValue(value)
                                        : extracted?.status === 'extracted'
                                          ? showValue(value)
                                          : extracted?.status || 'not provided'}
                                    </strong>

                                    {isSelected &&
                                    extracted?.provenance?.length ? (
                                      <small className="evidence">
                                        “{extracted.provenance[0].quote}”
                                      </small>
                                    ) : null}
                                  </span>

                                  <span
                                    className={
                                      'badge ' +
                                      (saved?.reviewed
                                        ? 'high'
                                        : extracted?.confidence ||
                                          extracted?.status ||
                                          'unknown')
                                    }
                                  >
                                    {saved?.reviewed
                                      ? 'REVIEWED'
                                      : extracted?.confidence ||
                                        extracted?.status ||
                                        'unknown'}
                                  </span>
                                </button>

                                {isSelected && (
                                  <div className="review-editor">
                                    {!editing ? (
                                      <>
                                        <div className="review-actions">
                                          <button
                                            onClick={startEditing}
                                          >
                                            Edit
                                          </button>

                                          <button
                                            onClick={verifyField}
                                          >
                                            Verify
                                          </button>
                                        </div>

                                        {saved?.note && (
                                          <p className="review-note">
                                            Note: {saved.note}
                                          </p>
                                        )}
                                      </>
                                    ) : (
                                      <>
                                        <label>
                                          Corrected value
                                          <input
                                            value={draftValue}
                                            onChange={event =>
                                              setDraftValue(event.target.value)
                                            }
                                          />
                                        </label>

                                        <label>
                                          Review note
                                          <textarea
                                            value={draftNote}
                                            onChange={event =>
                                              setDraftNote(event.target.value)
                                            }
                                            rows={3}
                                          />
                                        </label>

                                        <div className="review-actions">
                                          <button onClick={applyEdit}>
                                            Apply Locally
                                          </button>

                                          <button
                                            onClick={() => setEditing(false)}
                                          >
                                            Cancel
                                          </button>
                                        </div>
                                      </>
                                    )}
                                  </div>
                                )}
                              </div>
                            );
                          })}
                      </div>
                    ))}
                  </div>
                </>
              )}
            </section>
          </div>
        )}
      </main>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
