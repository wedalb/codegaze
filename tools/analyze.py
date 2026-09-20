#!/usr/bin/env python3
"""Derive token dwell intervals, not physiological fixations, from CodeGaze JSONL."""
import argparse
import csv
import json
from pathlib import Path

FIELDS = ['client_id', 'source', 'file', 'revision', 'token', 'start_offset', 'end_offset',
          'line', 'column', 'start_client_ms', 'end_client_ms', 'duration_ms', 'samples']

def derive(events, min_duration_ms=100.0, max_gap_ms=100.0):
    active = {}
    result = []
    def finish(client):
        group = active.pop(client, None)
        if group and group['duration_ms'] >= min_duration_ms:
            result.append({k: group[k] for k in FIELDS})
    for event in events:
        sample = event['sample']
        client = (event['sessionId'], sample['clientId'])
        target = event.get('target')
        if not sample['valid'] or event['mappingStatus'] != 'mapped' or not target:
            finish(client)
            continue
        time = sample['clientMonoMs']
        key = (sample['source'], target['file'], target['revision'], target['startOffset'], target['endOffset'])
        group = active.get(client)
        if group and (group['key'] != key or not 0 < time-group['end_client_ms'] <= max_gap_ms
                      or sample['sequence'] != group['last_sequence']+1):
            finish(client)
            group = None
        if group is None:
            group = dict(client_id=sample['clientId'], source=sample['source'], file=target['file'],
                         revision=target['revision'], token=target['text'], start_offset=target['startOffset'],
                         end_offset=target['endOffset'], line=target['line'], column=target['column'],
                         start_client_ms=time, end_client_ms=time, duration_ms=0, samples=0, key=key)
            active[client] = group
        group['end_client_ms'] = time
        group['duration_ms'] = time-group['start_client_ms']
        group['last_sequence'] = sample['sequence']
        group['samples'] += 1
    for client in list(active):
        finish(client)
    return sorted(result, key=lambda row: (row['client_id'], row['start_client_ms']))

def safe_cell(value):
    if isinstance(value, str) and value[:1] in ('=', '+', '-', '@', '\t', '\r', '\n'):
        return "'"+value
    return value

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('samples', type=Path)
    parser.add_argument('--output', type=Path, default=Path('dwell.csv'))
    parser.add_argument('--min-duration-ms', type=float, default=100.0)
    parser.add_argument('--max-gap-ms', type=float, default=100.0)
    args = parser.parse_args()
    if args.min_duration_ms < 0 or args.max_gap_ms <= 0:
        parser.error('Durations must be nonnegative and max gap positive')
    with args.samples.open(encoding='utf-8') as source:
        rows = derive((json.loads(line) for line in source if line.strip()), args.min_duration_ms, args.max_gap_ms)
    with args.output.open('w', encoding='utf-8', newline='') as output:
        writer = csv.DictWriter(output, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows({k:safe_cell(v) for k,v in row.items()} for row in rows)
    print(f'Wrote {len(rows)} token dwell intervals to {args.output}. These are not validated eye fixations.')

if __name__ == '__main__':
    main()
