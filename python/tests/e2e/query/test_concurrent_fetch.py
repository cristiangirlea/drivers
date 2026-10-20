"""Concurrent fetch of one typed result set.

DATE, TIME, DECIMAL, and TEXT are fetched from several threads and compared
with a serial fetch of the same ordered query. The serial fetch runs first,
so the process-global converter caches are already filled.
"""

from __future__ import annotations

import threading

import pytest


N_THREADS = 12


def _typed_query(rowcount: int) -> str:
    return f"""
        select seq4() as n,
               dateadd(day, seq4(), to_date('2020-01-01')) as d,
               time_from_parts(mod(seq4(), 24), mod(seq4(), 60), mod(seq4(), 60)) as t,
               (seq4() / 1000.0)::decimal(18, 3) as dec,
               'row_' || seq4()::string as s
        from table(generator(rowcount => {rowcount}))
        order by n
    """


def _barrier_map(fn, n=N_THREADS):
    """Run ``fn(i, barrier)`` on ``n`` threads released together."""
    results: list = [None] * n
    errors: list = [None] * n
    barrier = threading.Barrier(n)

    def wrapped(i):
        try:
            results[i] = fn(i, barrier)
        except Exception as exc:
            errors[i] = repr(exc)

    threads = [threading.Thread(target=wrapped, args=(i,)) for i in range(n)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    return results, [err for err in errors if err is not None]


@pytest.mark.timeout(300)
def test_should_fetch_same_rows_from_independent_connections(connection_factory):
    """Each thread opens its own connection and fetches the same typed result."""
    # When several connections fetch the same ordered DATE/TIME/DECIMAL/TEXT result
    query = _typed_query(3000)
    with connection_factory() as connection:
        with connection.cursor() as cursor:
            reference = cursor.execute(query).fetchall()
    assert reference

    def worker(_index, barrier):
        barrier.wait()
        with connection_factory() as connection:
            with connection.cursor() as cursor:
                return cursor.execute(query).fetchall()

    results, errors = _barrier_map(worker)
    # Then every thread returns the serial reference rows
    assert not errors, f"errors in concurrent independent connections: {errors}"
    for index, rows in enumerate(results):
        assert rows == reference, f"thread {index} differs from reference"


@pytest.mark.timeout(300)
def test_should_fetch_same_rows_from_cursors_on_one_connection(connection_factory):
    """Many cursors on one connection execute and fetch concurrently."""
    # When several cursors on one connection fetch the same ordered result
    query = _typed_query(1500)
    with connection_factory() as connection:
        with connection.cursor() as cursor:
            reference = cursor.execute(query).fetchall()

        def worker(_index, barrier):
            barrier.wait()
            with connection.cursor() as cursor:
                cursor.execute(query)
                return cursor.fetchall()

        results, errors = _barrier_map(worker)
    # Then every cursor returns the serial reference rows
    assert not errors, f"errors on shared-connection cursors: {errors}"
    for index, rows in enumerate(results):
        assert rows == reference, f"cursor {index} differs from reference"
