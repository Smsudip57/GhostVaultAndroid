package com.example.ghostvault.security;

/** Pure-Java, deterministic state transition policy. This class is host-testable. */
public final class VaultRules {
    private VaultRules() {}

    public enum Outcome { REAL, DECOY, DECOY_CREATED, WRONG }

    public static final class Decision {
        public final Outcome outcome;
        public final int failedAttempts;
        private Decision(Outcome outcome, int failedAttempts) {
            this.outcome = outcome;
            this.failedAttempts = failedAttempts;
        }
    }

    public static Decision resolve(boolean realMatches, boolean decoyExists,
                                   boolean decoyMatches, int failedSoFar) {
        return resolve(realMatches, decoyExists, decoyMatches, failedSoFar, false);
    }

    public static Decision resolve(boolean realMatches, boolean decoyExists,
                                   boolean decoyMatches, int failedSoFar, boolean isDuplicateWrong) {
        // The genuine password is ALWAYS checked first, including on attempt six.
        if (realMatches) return new Decision(Outcome.REAL, 0);
        if (decoyExists) {
            // Once a decoy exists, no later incorrect guess can replace its password.
            return new Decision(decoyMatches ? Outcome.DECOY : Outcome.WRONG, failedSoFar);
        }
        if (isDuplicateWrong) {
            // Duplicate wrong attempts within the same cycle do not advance toward decoy creation.
            return new Decision(Outcome.WRONG, failedSoFar);
        }
        if (failedSoFar >= 5) return new Decision(Outcome.DECOY_CREATED, 5);
        return new Decision(Outcome.WRONG, failedSoFar + 1);
    }
}
