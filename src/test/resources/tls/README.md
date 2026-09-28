This certificate and unencrypted private key are public, test-only fixtures for the
`fixture.test` hostname. They must never be used outside tests. The long certificate
lifetime avoids routine expiry failures. Only the test HTTP client trusts this
certificate; production continues using its normal trust store and hostname checks.
