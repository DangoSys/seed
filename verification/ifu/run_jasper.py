#!/usr/bin/env python3
"""Submit an existing IFU RTL bundle to JasperGold over SSH; preserve raw results.

This project launcher does not add a planner or a new product workflow. Generate
the RTL and Chisel assertions with run.py first. Counterexamples return exit 1.
"""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tarfile
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def summarize(raw):
    rows = [row for task in raw['fpv']['task'].values() for row in task['results']]
    assertions = [r for r in rows if r['type'] == 'assert']
    covers = [r for r in rows if r['type'] == 'cover']
    inactive = {r['name'].split(':precondition')[0] for r in covers
                if ':precondition' in r['name'] and r['status'] != 'covered'}
    proven = [r['name'] for r in assertions if r['status'] == 'proven' and r['name'] not in inactive]
    cex = [r['name'] for r in assertions if r['status'] == 'cex']
    return {
        'status': 'failed' if cex else ('passed' if assertions and len(proven) == len(assertions)
                                       and all(r['status'] == 'covered' for r in covers) else 'incomplete'),
        'verification_kind': 'formal',
        'scope': 'Post-reset safety under two IMEM assumptions; no bounded response/fairness assumption.',
        'assertion_statuses': dict(Counter(r['status'] for r in assertions)),
        'cover_statuses': dict(Counter(r['status'] for r in covers)),
        'nonvacuous_proven': proven,
        'counterexamples': cex,
        'inactive_antecedents': sorted(inactive),
        'note': 'The reset-state property has no active antecedent after Jasper reset initialization. '
                'Do not count it as a dynamic-reset proof. See simulation evidence.',
    }


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--rtl-dir', type=Path, required=True)
    p.add_argument('--host', default='jasper-vm', help='SSH config alias')
    p.add_argument('--jg', default='/tools/jasper_2024/bin/jg')
    p.add_argument('--output', type=Path)
    args = p.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', args.host) or args.host.startswith('-'):
        p.error('Use an SSH config alias without shell syntax')
    rtl = args.rtl_dir.resolve(strict=True)
    required = ['filelist.f', 'IFUVerificationTop.sv', 'IFU.sv', 'PCReg.sv',
                'verification/assert/layers-IFUVerificationTop-Verification-Assert.sv',
                'verification/cover/layers-IFUVerificationTop-Verification-Cover.sv']
    for name in required:
        if not (rtl / name).is_file():
            p.error(f'Missing RTL artifact: {name}')
    if any(f.is_symlink() for f in rtl.rglob('*')):
        p.error('RTL bundle must not contain symlinks')
    tag = datetime.now().strftime('%Y%m%d-%H%M%S-') + uuid.uuid4().hex[:8]
    output = (args.output or ROOT / 'build/verification' / f'ifu-jasper-{tag}').resolve()
    output.mkdir(parents=True, exist_ok=False)
    shutil.copytree(rtl, output / 'rtl')
    shutil.copytree(HERE / 'formal', output / 'formal')
    build_report = rtl.parent / 'report.json'
    if build_report.is_file():
        shutil.copy2(build_report, output / 'emission-report.json')
    plan_file = rtl.parent.parent / 'plan.json'
    if not plan_file.is_file():
        p.error('Expected a product run with plan.json beside the build directory')
    plan = json.loads(plan_file.read_text())
    shutil.copy2(plan_file, output / 'assertion-plan.json')
    hashes = {str(f.relative_to(output)): hashlib.sha256(f.read_bytes()).hexdigest()
              for f in output.rglob('*') if f.is_file()}
    (output / 'inputs.sha256.json').write_text(json.dumps(hashes, indent=2) + '\n')
    archive = output / 'bundle.tar.gz'
    with tarfile.open(archive, 'w:gz') as tar:
        for name in ['rtl', 'formal', 'assertion-plan.json', 'inputs.sha256.json']:
            tar.add(output / name, arcname=name)
    remote = f'seed-ifu-verification/{tag}'
    ssh = ['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', args.host]
    scp = ['scp', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10']
    (output / 'remote.json').write_text(json.dumps({'host': args.host, 'directory': remote,
                                                  'jg': args.jg}, indent=2) + '\n')
    subprocess.run([*ssh, f'mkdir -p seed-ifu-verification && mkdir {shlex.quote(remote)}'], check=True)
    subprocess.run([*scp, str(archive), f'{args.host}:{remote}/bundle.tar.gz'], check=True)
    command = (f'cd {shlex.quote(remote)} && tar xzf bundle.tar.gz && '
               f'{shlex.quote(args.jg)} -batch -proj jgproject formal/run.tcl > console.log 2>&1')
    print(f'Running JasperGold on {args.host}:{remote}', flush=True)
    completed = subprocess.run([*ssh, command])
    for name in ['console.log', 'results.json', 'results.csv', 'report.txt', 'request-stability.vcd']:
        subprocess.run([*scp, f'{args.host}:{remote}/{name}', str(output / name)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if completed.returncode or not (output / 'results.json').is_file():
        print(f'Jasper execution failed; inspect {output / "console.log"}', flush=True)
        return 2
    raw = json.loads((output / 'results.json').read_text())
    result = summarize(raw)
    actual = {(r['name'].split('.')[-1], r['type']) for task in raw['fpv']['task'].values()
              for r in task['results']}
    expected = {(r['assertion_id'], r['assertion_kind']) for r in plan['assertion_plans']}
    expected.update({('CHK_IF_REQUEST_STABILITY', 'assert'), ('CHK_IF_OUTSTANDING_LIMIT', 'assert')})
    result['missing_properties'] = sorted(expected - actual)
    if result['missing_properties']:
        result['status'] = 'invalid'
    (output / 'summary.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'summary': str(output / 'summary.json'),
                      'nonvacuous_proven': len(result['nonvacuous_proven']),
                      'counterexamples': result['counterexamples']}))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
