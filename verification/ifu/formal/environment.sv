// Project-owned IMEM protocol assumptions and independent interface monitor.
// The functional assertions are generated from Chisel by iabv and bound separately.
module IFUFormalEnvironment (
  input logic clock, reset,
  input logic io_imem_req_valid, io_imem_req_ready,
  input logic [63:0] io_imem_req_bits_addr,
  input logic io_imem_resp_valid, io_imem_resp_ready,
  input logic [31:0] io_imem_resp_bits_data,
  input logic io_out_valid
);
  wire req_fire = io_imem_req_valid && io_imem_req_ready;
  wire resp_fire = io_imem_resp_valid && io_imem_resp_ready;
  logic [2:0] credits;
  always @(posedge clock) begin
    if (reset) credits <= 0;
    else begin
      case ({req_fire, resp_fire})
        2'b10: credits <= credits + 1'b1;
        2'b01: credits <= credits - 1'b1;
        default: credits <= credits;
      endcase
    end
  end

  // ENV-IF-01/02: no unsolicited or zero-latency response. There is no
  // assumption bounding response delay, request ready, stall, or downstream ready.
  ENV_IF_RESPONSE_CREDIT: assume property (@(posedge clock)
    !reset && io_imem_resp_valid |-> credits != 0);
  // ENV-IF-03: an offered response survives backpressure, including redirect.
  ENV_IF_RESPONSE_HOLD: assume property (@(posedge clock) disable iff (reset)
    io_imem_resp_valid && !io_imem_resp_ready
    |=> io_imem_resp_valid && $stable(io_imem_resp_bits_data));

  // DUT obligations must remain assertions, never environment assumptions.
  CHK_IF_OUTSTANDING_LIMIT: assert property (@(posedge clock)
    !reset |-> credits <= 1);
  CHK_IF_REQUEST_STABILITY: assert property (@(posedge clock) disable iff (reset)
    io_imem_req_valid && !io_imem_req_ready
    |=> io_imem_req_valid && $stable(io_imem_req_bits_addr));
  COV_ENV_TRANSACTION: cover property (@(posedge clock) disable iff (reset)
    req_fire ##1 resp_fire && io_out_valid);
  COV_ENV_RESPONSE_WAIT: cover property (@(posedge clock) disable iff (reset)
    io_imem_resp_valid && !io_imem_resp_ready ##1 resp_fire);
endmodule

bind IFUVerificationTop IFUFormalEnvironment formal_environment (
  .clock(clock), .reset(reset),
  .io_imem_req_valid(io_imem_req_valid), .io_imem_req_ready(io_imem_req_ready),
  .io_imem_req_bits_addr(io_imem_req_bits_addr),
  .io_imem_resp_valid(io_imem_resp_valid), .io_imem_resp_ready(io_imem_resp_ready),
  .io_imem_resp_bits_data(io_imem_resp_bits_data), .io_out_valid(io_out_valid)
);
