import test from 'node:test';
import assert from 'node:assert/strict';

const base = process.env.CASE_INTAKE_API_URL || 'http://localhost:8080';
const enabled = process.env.CASE_INTAKE_E2E === '1';

async function json(path) {
  const response = await fetch(`${base}${path}`);
  assert.equal(response.status, 200, `${path} returned ${response.status}`);
  return response.json();
}

test('reviewer API serves catalog and document list', { skip: !enabled }, async () => {
  const catalog = await json('/api/catalog');
  assert.ok(Array.isArray(catalog.fields) && catalog.fields.length > 0);
  const documents = await json('/api/documents');
  assert.ok(Array.isArray(documents));
  assert.ok(documents.some(d => d.id === 'D03'));
});

test('reviewer API serves D03 with grounded GB country extraction', { skip: !enabled }, async () => {
  const result = await json('/api/documents/D03');
  assert.equal(result.id, 'D03');
  assert.equal(typeof result.text, 'string');
  assert.ok(result.extraction?.cases?.length > 0, 'D03 must have extraction results');
  const country = result.extraction.cases[0].fields.country;
  assert.equal(country.status, 'extracted');
  assert.equal(country.value, 'GB');
  for (const evidence of country.provenance || []) {
    const points = Array.from(result.text);
    assert.equal(points.slice(evidence.start, evidence.end).join(''), evidence.quote);
  }
});
