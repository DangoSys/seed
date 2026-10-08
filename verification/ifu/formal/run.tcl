# Execute from an isolated bundle containing rtl/ and formal/.
clear -all
set_proofgrid_max_local_jobs 2
set_prove_time_limit 60s
set includes [list -incdir rtl -incdir rtl/verification \
  -incdir rtl/verification/assert -incdir rtl/verification/cover]
set fh [open rtl/filelist.f r]
set files [split [string trim [read $fh]] "\n"]
close $fh
foreach file $files {
  analyze -sv12 {*}$includes [file join rtl [string trim $file]]
}
analyze -sv12 {*}$includes rtl/verification/assert/layers-IFUVerificationTop-Verification-Assert.sv
analyze -sv12 {*}$includes rtl/verification/cover/layers-IFUVerificationTop-Verification-Cover.sv
analyze -sv12 formal/environment.sv
elaborate -top IFUVerificationTop
clock clock
# Initialize through synchronous reset, then verify the post-reset state space.
# REQ_IF_001_RESET_STATE's reset antecedent is inactive during this proof;
# its dynamic-reset evidence comes from simulation, not this Jasper task.
reset reset
prove -all
report -all -file report.txt -force
report -csv -include_type -file results.csv -force
report -json -file results.json -force
if {[catch {
  visualize -violation -property IFUVerificationTop.formal_environment.CHK_IF_REQUEST_STABILITY -new_window issue_if_001
  visualize -save -vcd request-stability.vcd -window issue_if_001 -force
} trace_error]} {
  puts "Counterexample export unavailable: $trace_error"
}
exit
