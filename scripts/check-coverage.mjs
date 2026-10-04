#!/usr/bin/env node
//
// Fails when the combined test coverage is under a floor.
//
// Coverage is summed across every module before it is compared, never per
// module. That is deliberate: the services are independent, most of them carry
// only a context test, and a per module threshold would be red on day one for
// reasons that have nothing to do with the change being validated. A threshold
// that always fails is not a gate, it is noise.
//
//   node scripts/check-coverage.mjs --floor 0.30 --format jacoco <file>...
//   node scripts/check-coverage.mjs --floor 0.30 --format istanbul <file>...
//
// Two report formats, both parsed from files rather than from process output,
// because the exit code is the only thing the pipeline should have to read.

import { readFileSync } from 'node:fs';

const args = process.argv.slice(2);

function option(name, fallback) {
  const index = args.indexOf(`--${name}`);
  return index === -1 ? fallback : args[index + 1];
}

const floor = Number(option('floor', '0.30'));
const format = option('format', 'jacoco');
const files = args.filter((arg, index) => !arg.startsWith('--') && args[index - 1] !== '--floor'
  && args[index - 1] !== '--format');

if (!Number.isFinite(floor) || floor < 0 || floor > 1) {
  console.error(`Invalid floor "${floor}", expected a ratio between 0 and 1.`);
  process.exit(2);
}

if (files.length === 0) {
  console.error('No coverage report given. Refusing to report 0% as a pass.');
  process.exit(1);
}

/**
 * JaCoCo nests a <counter> per class, per source file, per package and once for
 * the whole report. Only the report level counter is the total, so the last one
 * in the file is the one to read; summing the others counts everything twice.
 */
function readJacoco(path) {
  const xml = readFileSync(path, 'utf8');
  const counters = xml.match(/<counter type="LINE"[^>]*>/g) ?? [];
  if (counters.length === 0) {
    throw new Error(`No LINE counter in ${path}. Is it really a JaCoCo report?`);
  }

  const last = counters[counters.length - 1];
  const missed = Number(/missed="(\d+)"/.exec(last)[1]);
  const covered = Number(/covered="(\d+)"/.exec(last)[1]);
  return { covered, missed };
}

/** The istanbul json-summary reporter writes the totals it computed itself. */
function readIstanbul(path) {
  // The reporter never emits a byte order mark, but this project is developed
  // on Windows where an editor may add one, and JSON.parse rejects it outright.
  const summary = JSON.parse(readFileSync(path, 'utf8').replace(/^﻿/, ''));
  const lines = summary.total?.lines;
  if (!lines || typeof lines.total !== 'number') {
    throw new Error(`No total.lines in ${path}.`);
  }
  return { covered: lines.covered ?? 0, missed: lines.total - (lines.covered ?? 0) };
}

const read = format === 'istanbul' ? readIstanbul : readJacoco;

let covered = 0;
let missed = 0;
const perFile = [];

for (const file of files) {
  try {
    const part = read(file);
    perFile.push([file, part.covered, part.missed]);
    covered += part.covered;
    missed += part.missed;
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}

const total = covered + missed;

// 0/0 is what an empty report or a run that never reached the code produces.
// Treating that as full coverage would turn a broken pipeline green.
if (total === 0) {
  console.error('Coverage is 0/0 lines. That is no coverage at all, not full coverage.');
  process.exit(1);
}

const ratio = covered / total;

for (const [file, c, m] of perFile) {
  const pct = m + c === 0 ? 'n/a' : `${((c / (c + m)) * 100).toFixed(1)}%`;
  console.log(`  ${pct.padStart(6)}  ${c}/${c + m}  ${file}`);
}

console.log('');
console.log(`Lines covered: ${covered} / ${total}  (${(ratio * 100).toFixed(2)}%)`);
console.log(`Required:      ${(floor * 100).toFixed(2)}%`);

if (ratio < floor) {
  console.log('');
  console.log('Coverage is under the floor.');
  process.exit(1);
}

console.log('Coverage floor met.');
