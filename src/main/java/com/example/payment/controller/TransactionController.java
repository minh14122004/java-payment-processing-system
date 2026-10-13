package com.example.payment.controller;

import com.example.payment.dto.TransactionDtos.*;
import com.example.payment.exception.ApiException;
import com.example.payment.security.AuthPrincipal;
import com.example.payment.service.FinancialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TransactionController {
    private final FinancialService service;
    public TransactionController(FinancialService service) { this.service = service; }

    @Operation(summary = "Deposit simulated VND into an owned account", description = "Each successful call deposits again; this route is not idempotent.")
    @ApiResponse(responseCode = "201", description = "Deposit committed")
    @PostMapping("/transactions/deposit")
    public ResponseEntity<TransactionView> deposit(@Valid @RequestBody Deposit input, @AuthenticationPrincipal AuthPrincipal principal) {
        var result = service.deposit(input, principal);
        return ResponseEntity.created(location(result)).body(result);
    }

    @Operation(summary = "Check a transfer and resolve its default message", description = "Read-only preview; does not reserve money or a key. Execution checks everything again.")
    @PostMapping("/transactions/transfer/preview")
    public Preview preview(@Valid @RequestBody Transfer input, @AuthenticationPrincipal AuthPrincipal principal) {
        return service.preview(input, principal);
    }

    @Operation(summary = "Transfer VND to an account number", description = "Keep the same key and body when retrying. Edit the message only before submitting a new transfer.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
        schema = @Schema(type = "string", minLength = 1, maxLength = 128, pattern = "[!-~]{1,128}"))
    @ApiResponse(responseCode = "201", description = "Transfer committed")
    @ApiResponse(responseCode = "200", description = "Original committed result replayed")
    @PostMapping("/transactions/transfer")
    public ResponseEntity<TransactionView> transfer(@Valid @RequestBody Transfer input,
            HttpServletRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        var keys = Collections.list(request.getHeaders("Idempotency-Key"));
        if (keys.size() != 1) throw new ApiException(400, "INVALID_REQUEST", "Exactly one Idempotency-Key is required");
        var result = service.transfer(input, keys.getFirst(), principal);
        return result.replay() ? ResponseEntity.ok(result.transaction())
            : ResponseEntity.created(location(result.transaction())).body(result.transaction());
    }

    @GetMapping("/transactions/{id}")
    public TransactionView detail(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) { return service.detail(id, principal); }

    @GetMapping("/accounts/{id}/transactions")
    public TransactionPage history(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal AuthPrincipal principal) {
        return service.history(id, page, size, principal);
    }
    private static URI location(TransactionView view) { return URI.create("/api/transactions/" + view.id()); }
}
