plugins { id("java-conventions") }

tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.85".toBigDecimal() } } }
}
