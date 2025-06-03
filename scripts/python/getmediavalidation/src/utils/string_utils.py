import argparse
import re
from datetime import datetime, timedelta


def parse_datetime(datetime_str: str) -> datetime:
    """Convert string to datetime object."""
    return datetime.fromisoformat(datetime_str)


def parse_duration(duration_str: str) -> timedelta:
    """Convert duration string (e.g., '1h', '30m', '24h') to timedelta."""
    match = re.match(r'^(\d+)([dhm])$', duration_str)
    if not match:
        raise argparse.ArgumentTypeError(
            "Duration must be in format <number>m, <number>h, or <number>d (e.g., '1h' or '30m')")

    value, unit = match.groups()
    value = int(value)

    if unit == 's':
        return timedelta(seconds=value)
    if unit == 'h':
        return timedelta(hours=value)
    elif unit == 'm':
        return timedelta(minutes=value)
    elif unit == 'd':
        return timedelta(days=value)
