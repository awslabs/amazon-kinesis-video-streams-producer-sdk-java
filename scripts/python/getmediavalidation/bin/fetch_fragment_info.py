import argparse
import logging
from datetime import datetime, timedelta
from typing import List

from dateutil.tz import tzlocal
from mypy_boto3_kinesis_video_archived_media.type_defs import FragmentTypeDef

from src.api import api_calls
from src.mkv import print_fragments
from src.utils import parse_datetime, parse_duration

logger = logging.getLogger(__name__)


def setup_argparse():
    parser = argparse.ArgumentParser(description='KVS Fragment Info Display')

    # Required arguments
    parser.add_argument('--stream-name', type=str, required=True,
                        help='Name of the stream (e.g., demo-stream)')

    # Optional arguments
    parser.add_argument('--log-level',
                        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'],
                        default='INFO',
                        help='Set the logging level (default: INFO)')

    # Mutually exclusive group for either timestamp range, or last d duration
    group = parser.add_argument_group('clip selection')
    selection = group.add_mutually_exclusive_group(required=False)

    selection.add_argument('--time-range', nargs=2, type=parse_datetime,
                           metavar=('START_TIME', 'END_TIME'),
                           help='Start and end timestamps (ISO format: 2025-05-27T21:30:00-07:00)')
    selection.add_argument('--last', type=parse_duration,
                           help='Duration to look back from now (e.g., "1h" for 1 hour, "30m" for 30 minutes), '
                                'default 1 hour')

    return parser


def main():
    parser = setup_argparse()
    args = parser.parse_args()

    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format='%(asctime)s [%(filename)s:%(lineno)s/%(funcName)-s()] [%(levelname)s] %(message)s',
        handlers=[
            logging.StreamHandler()  # Outputs logs to the console
        ]
    )

    if args.time_range is not None:
        # Use explicit time range
        start_time = args.time_range[0]
        end_time = args.time_range[1]
    else:
        # Default duration: 1 hour
        if args.last is None:
            args.last = timedelta(hours=1)

        # Calculate time range based on duration
        end_time = datetime.now(tz=tzlocal())
        start_time = end_time - args.last

    logger.info(f"Checking time range: {start_time} to {end_time}")

    fragment_list: List[FragmentTypeDef] = api_calls.list_fragments(
        args.stream_name,
        start_timestamp=start_time,
        end_timestamp=end_time
    )

    print_fragments(fragment_list)


if __name__ == '__main__':
    main()
