#!/usr/bin/env python3
"""Launch the existing product with the functional or known-failure IFU job."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

if __name__ == '__main__':
    args = sys.argv[1:]
    protocol = '--protocol' in args
    if protocol:
        args.remove('--protocol')
    job = 'job-protocol.json' if protocol else 'job.json'
    raise SystemExit(subprocess.call([
        sys.executable, str(ROOT / 'tools/chisel-iabv/src/product/cli.py'), 'verify',
        '--project-root', str(ROOT), '--job', str(ROOT / 'verification/ifu' / job),
        *args,
    ]))
