package com.finrax.interview_task.controller;

import com.finrax.interview_task.dto.CreateWalletRequest;
import com.finrax.interview_task.dto.DepositRequest;
import com.finrax.interview_task.dto.ErrorResponse;
import com.finrax.interview_task.dto.WalletResponse;
import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/wallets")
@RequiredArgsConstructor
@Tag(name = "1. Wallets", description = "Open wallets, read balances, and pay money in.")
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a wallet",
            description = "Step 1. Starts at zero. Pick the currency from the Examples dropdown above the body.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "User already holds a wallet in this currency",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public WalletResponse create(
            @Parameter(description = "Any string. Authentication is assumed to be handled elsewhere.",
                    example = "alice")
            @PathVariable String userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Choose a currency from the Examples dropdown.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateWalletRequest.class),
                            examples = {
                                    @ExampleObject(name = "BTC — Bitcoin", value = "{\"currency\": \"BTC\"}"),
                                    @ExampleObject(name = "ETH — Ethereum", value = "{\"currency\": \"ETH\"}"),
                                    @ExampleObject(name = "XRP — Ripple", value = "{\"currency\": \"XRP\"}"),
                                    @ExampleObject(name = "XLM — Stellar", value = "{\"currency\": \"XLM\"}")
                            }))
            @Valid @RequestBody CreateWalletRequest request) {
        return WalletResponse.from(walletService.create(userId, request.currency()));
    }

    @GetMapping
    @Operation(summary = "List wallets with balances",
            description = "Use this after every step to watch available and reserved move.")
    public List<WalletResponse> list(
            @Parameter(example = "alice") @PathVariable String userId) {
        return walletService.findAllFor(userId).stream()
                .map(WalletResponse::from)
                .toList();
    }

    @PostMapping("/{currency}/deposits")
    @Operation(summary = "Deposit funds",
            description = "Step 2. Credits the available balance so there is something to withdraw.")
    @ApiResponse(responseCode = "200", description = "Deposited")
    @ApiResponse(responseCode = "404", description = "No such wallet",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "Amount missing or not positive",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public WalletResponse deposit(
            @Parameter(example = "alice") @PathVariable String userId,
            @Parameter(description = "One of the supported currencies.", example = "BTC")
            @PathVariable Currency currency,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DepositRequest.class),
                            examples = {
                                    @ExampleObject(name = "Deposit 10", value = "{\"amount\": 10}"),
                                    @ExampleObject(name = "Deposit with 8 decimals", value = "{\"amount\": 0.12345678}"),
                                    @ExampleObject(name = "Rejected — zero", value = "{\"amount\": 0}"),
                                    @ExampleObject(name = "Rejected — negative", value = "{\"amount\": -5}")
                            }))
            @Valid @RequestBody DepositRequest request) {
        return WalletResponse.from(walletService.deposit(userId, currency, request.amount()));
    }
}
