package com.finrax.interview_task.controller;

import com.finrax.interview_task.entity.Currency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks the REST surface, including every way a caller can get it wrong. Also asserts the negative
 * requirement: no response body ever carries a stack trace or an exception class name.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WalletApiTest {

    @Autowired
    private MockMvc mockMvc;

    private String newUser() {
        return "api-" + UUID.randomUUID();
    }

    private void createBtcWallet(String userId) throws Exception {
        mockMvc.perform(post("/users/{u}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"BTC"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("BTC"))
                .andExpect(jsonPath("$.available").value(0))
                .andExpect(jsonPath("$.reserved").value(0));
    }

    private void deposit(String userId, String amount) throws Exception {
        mockMvc.perform(post("/users/{u}/wallets/BTC/deposits", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":%s}".formatted(amount)))
                .andExpect(status().isOk());
    }

    @Test
    void createsListsAndDepositsIntoAWallet() throws Exception {
        String userId = newUser();
        createBtcWallet(userId);
        deposit(userId, "7.5");

        mockMvc.perform(get("/users/{u}/wallets", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currency").value("BTC"))
                .andExpect(jsonPath("$[0].available").value(7.5));
    }

    @Test
    void acceptsAWithdrawalWith202AndMovesFundsToReserved() throws Exception {
        String userId = newUser();
        createBtcWallet(userId);
        deposit(userId, "10");

        mockMvc.perform(post("/users/{u}/wallets/BTC/withdrawals", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"bc1qexampleaddress","amount":4}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/users/{u}/wallets", userId))
                .andExpect(jsonPath("$[0].available").value(6))
                .andExpect(jsonPath("$[0].reserved").value(4));
    }

    @Test
    void rejectsASecondWalletInTheSameCurrencyWith409() throws Exception {
        String userId = newUser();
        createBtcWallet(userId);

        mockMvc.perform(post("/users/{u}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"BTC"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));
    }

    @Test
    void rejectsOverdraftWith400() throws Exception {
        String userId = newUser();
        createBtcWallet(userId);
        deposit(userId, "1");

        String body = mockMvc.perform(post("/users/{u}/wallets/BTC/withdrawals", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"bc1qexampleaddress","amount":100}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception").doesNotContain("com.finrax");
    }

    @Test
    void returns404ForAWalletTheUserDoesNotHave() throws Exception {
        mockMvc.perform(post("/users/{u}/wallets/BTC/deposits", newUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void returns400ForANonPositiveAmount() throws Exception {
        String userId = newUser();
        createBtcWallet(userId);

        mockMvc.perform(post("/users/{u}/wallets/BTC/deposits", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":-5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returns400ForAnUnsupportedCurrency() throws Exception {
        mockMvc.perform(post("/users/{u}/wallets/DOGE/deposits", newUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void listsTheAllowedCurrenciesWhenAnUnknownOneIsSentInTheBody() throws Exception {
        mockMvc.perform(post("/users/{u}/wallets", newUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"DOGE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(containsString("BTC, ETH, XRP, XLM")));
    }

    @Test
    void listsTheAllowedCurrenciesWhenAnUnknownOneIsSentInThePath() throws Exception {
        mockMvc.perform(post("/users/{u}/wallets/DOGE/deposits", newUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(containsString("BTC, ETH, XRP, XLM")));
    }

    @Test
    void acceptsEverySupportedCurrency() throws Exception {
        String userId = newUser();
        for (Currency currency : Currency.values()) {
            mockMvc.perform(post("/users/{u}/wallets", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\":\"%s\"}".formatted(currency)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.currency").value(currency.name()));
        }
        mockMvc.perform(get("/users/{u}/wallets", userId))
                .andExpect(jsonPath("$.length()").value(Currency.values().length));
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        String userId = newUser();
        createBtcWallet(userId);

        mockMvc.perform(post("/users/{u}/wallets/BTC/deposits", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
