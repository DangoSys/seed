"""Reproduce the assertion binding gap on copied Seed inputs; never edits the project.
Run: python3 <this-file> --project-root /path/to/seed --output /tmp/new-review-run
Current reviewed version: unbound case passes; bound case fails on the injected property.
"""
import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--project-root',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();root=a.project_root.resolve();out=a.output.resolve()
if out==root or root.is_relative_to(out): raise SystemExit('Output must not contain the project')
out.mkdir(parents=True,exist_ok=False)
job=json.loads((root/'verification/pc-reg/job.json').read_text())
cli=root/'tools/chisel-iabv/src/product/cli.py'
results={}
for name,unbound in [('unbound',True),('bound',False)]:
 project=out/name/'project'; project.mkdir(parents=True)
 for value in job['snapshot_inputs']:
  src=(root/value).resolve()
  if not src.is_relative_to(root):raise SystemExit('Input escapes project')
  if src==out or out.is_relative_to(src):raise SystemExit('Output overlaps snapshot input')
  dst=project/value;dst.parent.mkdir(parents=True,exist_ok=True)
  if src.is_dir():shutil.copytree(src,dst,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
  else:shutil.copy2(src,dst)
 config=copy.deepcopy(job);config['mutations']=[]
 if unbound: config['backend']['layers']=[x for x in config['backend']['layers'] if '/assert/' not in x]
 job_file=project/'verification/pc-reg/job.json'
 job_file.write_text(json.dumps(config,indent=2)+'\n')
 plan_file=project/config['assertions']['plan'];plan=json.loads(plan_file.read_text())
 row=next(r for r in plan['assertion_plans'] if r['assertion_id']=='REQ_PC_003_ADVANCE')
 row['check']='io.pc === 1.U'
 plan_file.write_text(json.dumps(plan,indent=2)+'\n')
 result_dir=out/name/'result'
 cmd=[sys.executable,str(cli),'verify','--project-root',str(project),'--job',str(job_file),'--output',str(result_dir)]
 with (out/name/'cli.log').open('w') as log:
  run=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT)
 report=json.loads((result_dir/'report.json').read_text())
 results[name]={'exit_code':run.returncode,'status':report['status'],'error':report.get('error'),'generated_sha256':report.get('assertion_source',{}).get('generated_sha256')}
 print(name,results[name],flush=True)
(out/'summary.json').write_text(json.dumps(results,indent=2)+'\n')
