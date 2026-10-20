"""Large ordered fetch with one prefetch thread and with several.

``client_prefetch_threads`` is the chunk download pool. Python conversion
still runs on the thread that calls ``fetchall``. The check is that those
values match a one-thread fetch of the same query.
"""

from __future__ import annotations

import datetime

from decimal import Decimal

import numpy as np
import pytest


CLIENT_PREFETCH_THREADS = 4


@pytest.mark.timeout(600)
def test_should_match_serial_conversion_when_prefetch_pool_converts_chunks(connection_factory):
    """A several-thread prefetch download matches a one-thread fetch."""
    rowcount = 200_000
    query = f"""
        select seq4() as n,
               dateadd(day, seq4(), to_date('2020-01-01')) as d,
               time_from_parts(mod(seq4(), 24), mod(seq4(), 60), mod(seq4(), 60)) as t,
               (seq4() / 1000.0)::decimal(18, 3) as dec,
               'row_' || seq4()::string as s
        from table(generator(rowcount => {rowcount}))
        order by n
    """
    # When one prefetch thread and then several download the same ordered result
    with connection_factory(client_prefetch_threads=1) as connection:
        with connection.cursor() as cursor:
            reference = cursor.execute(query).fetchall()
    assert len(reference) == rowcount
    assert reference[0][0] == 0
    assert reference[-1][0] == rowcount - 1
    first = reference[0]
    assert isinstance(first[1], datetime.date)
    assert isinstance(first[2], datetime.time)
    assert isinstance(first[3], Decimal)
    assert isinstance(first[4], str)

    for _ in range(3):
        with connection_factory(client_prefetch_threads=CLIENT_PREFETCH_THREADS) as connection:
            with connection.cursor() as cursor:
                rows = cursor.execute(query).fetchall()
        # Then the downloaded rows match the one-thread fetch
        assert rows == reference


@pytest.mark.timeout(600)
def test_should_match_serial_numpy_conversion_when_prefetch_pool_converts_chunks(connection_factory):
    """The numpy fetch matches a one-thread fetch of the same query."""
    rowcount = 200_000
    query = f"""
        select seq4() as n,
               (seq4() / 7.0)::double as f,
               dateadd(second, seq4(), '2020-01-01'::timestamp_ntz) as ts
        from table(generator(rowcount => {rowcount}))
        order by n
    """
    # When one prefetch thread and then several download the same numpy result
    with connection_factory(numpy=True, client_prefetch_threads=1) as connection:
        with connection.cursor() as cursor:
            reference = cursor.execute(query).fetchall()
    assert len(reference) == rowcount
    assert reference[0][0] == 0
    assert reference[-1][0] == rowcount - 1
    first = reference[0]
    assert isinstance(first[0], np.int64)
    assert isinstance(first[1], np.float64)
    assert isinstance(first[2], np.datetime64)

    with connection_factory(numpy=True, client_prefetch_threads=CLIENT_PREFETCH_THREADS) as connection:
        with connection.cursor() as cursor:
            rows = cursor.execute(query).fetchall()
    # Then the numpy rows match the one-thread fetch
    assert len(rows) == rowcount
    assert rows == reference
