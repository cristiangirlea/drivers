"""SNOWFLAKE_TLS_CIPHERS is read by the core when a Python connection builds its TLS client."""

import pytest


@pytest.mark.skip_reference(
    reason=(
        "The legacy connector reports an unrecognized cipher when OpenSSL wraps the socket, "
        "not when the client is built"
    )
)
def test_unknown_tls_cipher_fails_connection(monkeypatch, int_test_connection_factory):
    # Given SNOWFLAKE_TLS_CIPHERS names a cipher this build does not offer
    monkeypatch.setenv("SNOWFLAKE_TLS_CIPHERS", "DHE-RSA-AES256-GCM-SHA384")
    # When a connection is opened
    with pytest.raises(Exception) as exc_info:
        int_test_connection_factory()
    # Then client creation fails and names the variable and the cipher
    assert "SNOWFLAKE_TLS_CIPHERS" in str(exc_info.value)
    assert "DHE-RSA-AES256-GCM-SHA384" in str(exc_info.value)
