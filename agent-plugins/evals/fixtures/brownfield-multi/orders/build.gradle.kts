plugins { id("java-conventions") }

// Orders only has to hold what it covers today.
tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.40".toBigDecimal() } } }
}
