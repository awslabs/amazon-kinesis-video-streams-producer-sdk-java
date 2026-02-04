import argparse
import logging
import os

from src.api import fetch_fragments

logger = logging.getLogger(__name__)


def setup_argparse():
    parser = argparse.ArgumentParser(description='KVS Fragment Info Fetcher')

    # Required arguments
    parser.add_argument('--stream-name', type=str, required=True,
                        help='Name of the stream (e.g., demo-stream)')

    parser.add_argument('--fragment-number', type=str, required=True,
                        help='Fragment number to download')

    # Optional arguments
    parser.add_argument('--log-level',
                        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'],
                        default='INFO',
                        help='Set the logging level (default: INFO)')

    parser.add_argument('--output-directory', type=str, required=False,
                        default=os.getcwd(),
                        help='Directory to output the downloaded fragment. Default: This directory.')

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

    fragment_number: str = args.fragment_number

    # Result should only be one fragment
    fetched_fragments_map: dict[str, bytes] = fetch_fragments(stream_name=args.stream_name,
                                                              fragment_numbers=[args.fragment_number])

    fragment_bytes: bytes = fetched_fragments_map[fragment_number]

    file_path = f'{args.output_directory}/{args.stream_name}-{fragment_number}.mkv'

    with open(file=file_path, mode='wb') as file:
        file.write(fragment_bytes)

    logger.info(f'Saved {fragment_number} from {args.stream_name} at {file_path} ({len(fragment_bytes)} bytes)')


if __name__ == '__main__':
    main()
