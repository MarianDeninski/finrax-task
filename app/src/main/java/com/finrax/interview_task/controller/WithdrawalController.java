package com.finrax.interview_task.controller;

import com.finrax.interview_task.dto.ErrorResponse;
import com.finrax.interview_task.dto.WithdrawalRequest;
import com.finrax.interview_task.dto.WithdrawalResponse;
import com.finrax.interview_task.entity.Currency;
import com.finrax.interview_task.service.WithdrawalService;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/wallets/{currency}/withdrawals")
@RequiredArgsConstructor
@Tag(name = "2. Withdrawals", description = "Send funds to an external address.")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a withdrawal")
    @ApiResponse(responseCode = "202", description = "Accepted; funds reserved, payout is asynchronous")
    @ApiResponse(responseCode = "400", description = "Available balance does not cover the amount",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such wallet",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public WithdrawalResponse request(
            @Parameter(example = "alice") @PathVariable String userId,
            @Parameter(description = "One of the supported currencies.", example = "BTC")
            @PathVariable Currency currency,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WithdrawalRequest.class),
                            examples = {
                                    @ExampleObject(name = "Withdraw 4 — succeeds",
                                            value = "{\"address\": \"bc1qexampleaddress\", \"amount\": 4}"),
                                    @ExampleObject(name = "Withdraw 1 — small amount",
                                            value = "{\"address\": \"bc1qexampleaddress\", \"amount\": 1}"),
                                    @ExampleObject(name = "Rejected — more than the balance",
                                            value = "{\"address\": \"bc1qexampleaddress\", \"amount\": 1000}"),
                                    @ExampleObject(name = "Rejected — blank address",
                                            value = "{\"address\": \"\", \"amount\": 1}")
                            }))
            @Valid @RequestBody WithdrawalRequest request) {
        return WithdrawalResponse.from(
                withdrawalService.request(userId, currency, request.address(), request.amount()));
    }
}
