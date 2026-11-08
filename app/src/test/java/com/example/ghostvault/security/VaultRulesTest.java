package com.example.ghostvault.security;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VaultRulesTest {
    @Test public void sixthWrongAttemptCreatesDecoy() {
        int failures = 0;
        for (int i = 1; i <= 5; i++) {
            VaultRules.Decision d = VaultRules.resolve(false, false, false, failures);
            assertEquals(VaultRules.Outcome.WRONG, d.outcome);
            assertEquals(i, d.failedAttempts);
            failures = d.failedAttempts;
        }
        assertEquals(VaultRules.Outcome.DECOY_CREATED,
                VaultRules.resolve(false, false, false, failures).outcome);
    }

    @Test public void existingDecoyPasswordRemainsFixed() {
        assertEquals(VaultRules.Outcome.DECOY,
                VaultRules.resolve(false, true, true, 5).outcome);
        assertEquals(VaultRules.Outcome.WRONG,
                VaultRules.resolve(false, true, false, 5).outcome);
        assertEquals(VaultRules.Outcome.DECOY,
                VaultRules.resolve(false, true, true, 5).outcome);
    }

    @Test public void realPasswordAlwaysWinsEvenOnSixthAttempt() {
        assertEquals(VaultRules.Outcome.REAL,
                VaultRules.resolve(true, false, false, 5).outcome);
        assertEquals(VaultRules.Outcome.REAL,
                VaultRules.resolve(true, true, true, 5).outcome);
        assertEquals(0, VaultRules.resolve(true, true, false, 5).failedAttempts);
    }

    @Test public void duplicateWrongAttemptsDoNotAdvanceCounterOrTriggerDecoy() {
        // First wrong attempt (unique)
        VaultRules.Decision d1 = VaultRules.resolve(false, false, false, 0, false);
        assertEquals(VaultRules.Outcome.WRONG, d1.outcome);
        assertEquals(1, d1.failedAttempts);

        // Same wrong attempt repeated (duplicate) -> counter stays 1
        VaultRules.Decision dup1 = VaultRules.resolve(false, false, false, 1, true);
        assertEquals(VaultRules.Outcome.WRONG, dup1.outcome);
        assertEquals(1, dup1.failedAttempts);

        // At 5 failures, repeating a duplicate wrong input does NOT create decoy
        VaultRules.Decision dupAt5 = VaultRules.resolve(false, false, false, 5, true);
        assertEquals(VaultRules.Outcome.WRONG, dupAt5.outcome);
        assertEquals(5, dupAt5.failedAttempts);

        // Only a 6th UNIQUE wrong attempt triggers decoy creation
        VaultRules.Decision unique6th = VaultRules.resolve(false, false, false, 5, false);
        assertEquals(VaultRules.Outcome.DECOY_CREATED, unique6th.outcome);
    }
}
