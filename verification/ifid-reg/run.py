#!/usr/bin/env python3
"""Run the Seed IFIDReg generated-assertion simulation job."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

if __name__ == '__main__':
    raise SystemExit(subprocess.call([
        sys.executable, str(ROOT / 'tools/chisel-iabv/src/product/cli.py'), 'verify',
        '--project-root', str(ROOT), '--job', str(ROOT / 'verification/ifid-reg/job.json'),
        *sys.argv[1:]
    ]))
