"""Cold-cache conversion of DATE, TIME, DECIMAL, and TEXT.

Those converters remember their Python constructors in process-global
state. Pytest has already imported the connector, so an in-process fetch
finds the caches full. This test converts in a subprocess, and every
thread builds its iterator only after a shared barrier. The expected rows
come from ``datetime`` and ``Decimal``, not from an earlier connector fetch.
"""

from __future__ import annotations

import ctypes
import subprocess
import sys
import threading
import traceback

from datetime import date, time, timedelta
from decimal import Decimal

import pytest


_N_THREADS = 8
_N_ROWS = 32
_DECIMAL_SCALE = 3
_TIME_STEP_SECONDS = 3723
_SECONDS_PER_DAY = 86_400


class _ArrowArrayStream(ctypes.Structure):
    """Mirrors the Arrow C Data Interface ``ArrowArrayStream`` struct layout."""

    _fields_ = [
        ("get_schema", ctypes.c_void_p),
        ("get_next", ctypes.c_void_p),
        ("get_last_error", ctypes.c_void_p),
        ("release", ctypes.c_void_p),
        ("private_data", ctypes.c_void_p),
    ]


def _column_values() -> list[tuple[int, int, int, str]]:
    return [
        (
            index,
            (index * _TIME_STEP_SECONDS) % _SECONDS_PER_DAY,
            index * 1000 - 250,
            f"row_{index}",
        )
        for index in range(_N_ROWS)
    ]


def _expected_rows() -> list[tuple[date, time, Decimal, str]]:
    rows = []
    for day_offset, seconds, coefficient, text in _column_values():
        rows.append(
            (
                date(1970, 1, 1) + timedelta(days=day_offset),
                time(seconds // 3600, (seconds % 3600) // 60, seconds % 60),
                Decimal(coefficient).scaleb(-_DECIMAL_SCALE),
                text,
            )
        )
    return rows


def _record_batch(pa):
    days, seconds, coefficients, texts = zip(*_column_values(), strict=True)
    schema = pa.schema(
        [
            pa.field("d", pa.date32(), metadata={"logicalType": "DATE"}),
            pa.field("t", pa.int64(), metadata={"logicalType": "TIME", "scale": "0"}),
            pa.field(
                "dec",
                pa.int64(),
                metadata={"logicalType": "FIXED", "scale": str(_DECIMAL_SCALE), "precision": "18"},
            ),
            pa.field("s", pa.string(), metadata={"logicalType": "TEXT"}),
        ]
    )
    return pa.record_batch(
        [
            pa.array([date(1970, 1, 1) + timedelta(days=day) for day in days], type=pa.date32()),
            pa.array(seconds, type=pa.int64()),
            pa.array(coefficients, type=pa.int64()),
            pa.array(list(texts), type=pa.string()),
        ],
        schema=schema,
    )


def _export_stream(pa, batch):
    reader = pa.RecordBatchReader.from_batches(batch.schema, [batch])
    stream = _ArrowArrayStream()
    reader._export_to_c(ctypes.addressof(stream))
    return stream, reader


def _convert_together(pa, batch, *, use_core_arrow: bool) -> list[str]:
    from snowflake.connector._internal.arrow_context import ArrowConverterContext
    from snowflake.connector._internal.arrow_stream_utils import create_row_iterator

    expected = _expected_rows()
    streams = [_export_stream(pa, batch) for _ in range(_N_THREADS)]
    results: list = [None] * _N_THREADS
    errors: list[str | None] = [None] * _N_THREADS
    barrier = threading.Barrier(_N_THREADS)

    def worker(index: int) -> None:
        try:
            barrier.wait()
            stream, _reader = streams[index]
            iterator = create_row_iterator(
                ctypes.addressof(stream),
                context=ArrowConverterContext(timezone="UTC"),
                use_core_arrow=use_core_arrow,
            )
            results[index] = list(iterator)
        except Exception as exc:
            errors[index] = f"{type(exc).__name__}: {exc}"

    threads = [threading.Thread(target=worker, args=(index,)) for index in range(_N_THREADS)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    problems = [f"thread {index}: {err}" for index, err in enumerate(errors) if err is not None]
    for index, rows in enumerate(results):
        if errors[index] is not None:
            continue
        if rows != expected:
            problems.append(f"thread {index}: got {rows!r}, expected {expected!r}")
    return problems


def _child_main() -> None:
    import pyarrow as pa

    from snowflake.connector._core import sf_core_python

    batch = _record_batch(pa)
    problems = [f"cython {problem}" for problem in _convert_together(pa, batch, use_core_arrow=False)]
    if not problems and sf_core_python.native_arrow_enabled():
        problems.extend(f"native {problem}" for problem in _convert_together(pa, batch, use_core_arrow=True))
    if problems:
        print("\n".join(problems), file=sys.stderr)
        sys.exit(1)


def test_should_match_python_values_when_threads_convert_on_a_cold_cache() -> None:
    """Threads convert DATE, TIME, and DECIMAL before any connector fetch has filled the caches."""
    pytest.importorskip("pyarrow")
    proc = subprocess.run(
        [sys.executable, __file__],
        capture_output=True,
        text=True,
        timeout=60,
        check=False,
    )
    assert proc.returncode == 0, (
        f"Cold-cache conversion failed (subprocess exit {proc.returncode}).\n"
        f"--- subprocess stderr ---\n{proc.stderr}\n"
        f"--- subprocess stdout ---\n{proc.stdout}"
    )


if __name__ == "__main__":
    try:
        _child_main()
    except Exception:
        traceback.print_exc()
        sys.exit(1)
