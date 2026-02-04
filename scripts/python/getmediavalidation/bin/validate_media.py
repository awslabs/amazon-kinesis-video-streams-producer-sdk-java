import argparse
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import List, Literal, TypeAlias

from dateutil.tz import tzlocal
from mypy_boto3_kinesis_video_archived_media.type_defs import FragmentTypeDef

from src.api import list_fragments, fetch_fragments, fetch_n_clips
from src.mkv.fragment_util import Clip, group_fragments_into_clips, print_clips
from src.mkv.frame_reader import read_frame_files
from src.mkv.mkv_validator import MkvFile
from src.utils import parse_datetime, parse_duration

logger = logging.getLogger(__name__)

TIMESTAMP_RELATIVE = 'relative'
TIMESTAMP_ABSOLUTE = 'absolute'

TimestampType: TypeAlias = Literal[TIMESTAMP_ABSOLUTE, TIMESTAMP_RELATIVE] | None


@dataclass
class FrameFilesConfig:
    file_pattern: str
    frames_per_keyframe: int
    frames_per_second: int

    # Percentage difference from the expected framerate which is allowed
    acceptable_fps_threshold: float


def validate_clip_against_files(stream_name: str, clip: Clip, file_config: FrameFilesConfig,
                                timestamp_type: TimestampType):
    """
    Validates a clip against original frame files by comparing frame contents and checking for increasing cluster
    timestamps.

    The method performs two validations:
      1. Compares the byte content of uploaded frames against original frame files
      2. Verifies that cluster timestamps are monotonically increasing between fragments in a clip

    :param stream_name: Name of the stream to validate
    :param clip: The clip containing fragments to validate
    :param file_config: Configuration for frame files
    :param timestamp_type: Type of timestamp to validate against
    :return: True if all validations pass, False if any validation fails
    """
    frame_contents: List[bytes] = read_frame_files(file_config.file_pattern)
    number_of_frames = len(frame_contents)

    logger.info(f"Validating {clip}")

    are_all_valid = True
    previous_cluster_timestamp = -1
    for i, fragment in enumerate(clip):
        fragment_number = fragment['FragmentNumber']
        logger.info(f"Validating fragment {i + 1}/{len(clip)}: {fragment_number}")
        first_frame_of_fragment_idx = (i * file_config.frames_per_keyframe) % number_of_frames

        fragment_bytes = fetch_fragments(stream_name=stream_name, fragment_numbers=[fragment_number])[fragment_number]

        downloaded_file = MkvFile(mkv_file=fragment_bytes)
        uploaded_frame_bytes = downloaded_file.get_first_frame_bytes()
        original_frame_bytes = frame_contents[first_frame_of_fragment_idx]

        # Validate the byte content
        if not uploaded_frame_bytes == original_frame_bytes:
            are_all_valid = False

            logger.error(f"{fragment} does not match! File index: {first_frame_of_fragment_idx}, "
                         f"len: {len(original_frame_bytes)}, fragment len: {len(uploaded_frame_bytes)}")

        # Validate the cluster timecodes are monotonically increasing
        cluster_timestamp = downloaded_file.extract_cluster_timestamp()
        if timestamp_type == TIMESTAMP_RELATIVE and i == 0:
            # The first fragment in a session should always have 0 timecode
            if not cluster_timestamp == 0:
                are_all_valid = False
                logger.error(f"The first fragment in the session should have 0 cluster timestamp! "
                             f"Fragment: {fragment}, timecode: {cluster_timestamp}")

        elif timestamp_type is not None:
            if previous_cluster_timestamp >= cluster_timestamp:
                are_all_valid = False
                logger.error(f"Two adjacent fragments do not have monotonically increasing timestamps! "
                             f"Fragment1: {clip[i - 1]}, timecode: {previous_cluster_timestamp} "
                             f"Fragment2: {fragment}, timecode: {cluster_timestamp}")

        previous_cluster_timestamp = cluster_timestamp

    logger.info(f"Clip is {'valid' if are_all_valid else 'invalid'}!")
    return are_all_valid


def validate_framerate(clips: List[Clip], file_config: FrameFilesConfig) -> bool:
    """
    Validates that the fragments in the clips maintain the expected framerate within an acceptable threshold.

    The method calculates an acceptable duration range for fragments based on:
      - The configured frames per keyframe
      - The configured frames per second
      - The acceptable threshold percentage

    It then verifies that each fragment's duration falls within this acceptable range.

    Note: The last fragment of each clip is excluded from validation as it may be incomplete.

    :param clips: List of clips to validate
    :param file_config: Configuration containing framerate settings and thresholds
    :return: True if all fragments maintain the expected framerate, False otherwise
    """
    expected_fragment_duration_ms = file_config.frames_per_keyframe / file_config.frames_per_second * 1000
    acceptable_threshold_percentage = file_config.acceptable_fps_threshold
    range_min_duration_ms = round(max(expected_fragment_duration_ms * (1 - acceptable_threshold_percentage), 0), 2)
    range_max_duration_ms = round(expected_fragment_duration_ms * (1 + acceptable_threshold_percentage), 2)

    are_all_valid = True
    for clip in clips:

        # Process all fragments except the last one - since it may not be a full fragment's worth
        # depending on where the media stopped uploading
        for fragment in clip[:-1]:

            current_fragment_duration_ms = fragment['FragmentLengthInMilliseconds']

            if not (range_min_duration_ms < current_fragment_duration_ms < range_max_duration_ms):
                logger.error(f"Fragment {fragment} has duration outside of the expected range! "
                             f"({range_min_duration_ms} - {range_max_duration_ms})")
                are_all_valid = False

    return are_all_valid


def validate_no_fragment_overlap(clips: List[Clip]) -> bool:
    """
    Checks that all the fragments in a clip do not have overlapping timestamps.

    :param clips: List of clips to validate.
    :return: True if none of the fragments in any of the clips overlap. False if there are any overlaps.
    """
    are_all_valid = True
    for clip in clips:

        prev_fragment_end = None
        for i, fragment in enumerate(clip):
            fragment_start: datetime = fragment['ProducerTimestamp']

            fragment_end: datetime = fragment_start + timedelta(milliseconds=fragment['FragmentLengthInMilliseconds'])

            if prev_fragment_end is not None and prev_fragment_end >= fragment_start:
                logger.error(f"Fragment {fragment} overlaps with {clip[i - 1]}!")
                are_all_valid = False

            prev_fragment_end = fragment_end

    return are_all_valid


def setup_argparse():
    parser = argparse.ArgumentParser(description='KVS uploaded media validator')

    # Required arguments
    parser.add_argument('--stream-name', type=str, required=True,
                        help='Name of the stream (e.g., demo-stream)')
    parser.add_argument('--frames-path', type=str, required=True,
                        help='File pattern path (e.g., /path/to/frame-*.h264)')
    parser.add_argument('-k', '--keyframe-interval', type=int, required=True,
                        help='Number of frames per keyframe')
    parser.add_argument('-fps', '--frames-per-second', type=int, required=True,
                        help='Number of frames per keyframe')

    # Optional arguments
    parser.add_argument('--log-level',
                        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'],
                        default='INFO',
                        help='Set the logging level (default: INFO)')

    parser.add_argument('--timestamp-type', choices=[TIMESTAMP_ABSOLUTE, TIMESTAMP_RELATIVE],
                        required=False,
                        help='When provided, checks the timestamp of the clips against this value')

    parser.add_argument('--acceptable-fps-threshold', type=float,
                        required=False, default=0.2,
                        help='Maximum percentage jitter allowed in the framerate (default 20 percent)')

    # Mutually exclusive group for either n number of clips, timestamp range, or last d duration
    group = parser.add_argument_group('clip selection')
    selection = group.add_mutually_exclusive_group(required=True)

    selection.add_argument('-n', type=int,
                           help='Number of clips to fetch')
    selection.add_argument('--time-range', nargs=2, type=parse_datetime,
                           metavar=('START_TIME', 'END_TIME'),
                           help='Start and end timestamps (ISO format: 2025-05-27T21:30:00-07:00)')
    selection.add_argument('--last', type=parse_duration,
                           help='Duration to look back from now (e.g., "1h" for 1 hour, "30m" for 30 minutes)')

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

    if args.acceptable_fps_threshold < 0:
        parser.error('Acceptable FPS threshold cannot be negative!')

    # Set up the configuration
    config = FrameFilesConfig(
        file_pattern=args.frames_path,
        frames_per_keyframe=args.keyframe_interval,
        frames_per_second=args.frames_per_second,
        acceptable_fps_threshold=args.acceptable_fps_threshold,
    )

    # Get clips based on the provided arguments
    if args.n is not None:
        clips: List[Clip] = fetch_n_clips(args.stream_name, args.n)
    else:
        if args.last is not None:
            # Calculate time range based on duration
            end_time = datetime.now(tz=tzlocal())
            start_time = end_time - args.last
        else:
            # Use explicit time range
            start_time = args.time_range[0]
            end_time = args.time_range[1]

        logger.info(f"Checking time range: {start_time} to {end_time}")

        fragment_list: List[FragmentTypeDef] = list_fragments(
            args.stream_name,
            start_timestamp=start_time,
            end_timestamp=end_time
        )
        clips: List[Clip] = group_fragments_into_clips(fragment_list)

        logger.info(f"Found {len(fragment_list)} fragments in {args.stream_name}"
                    f" ({len(clips)} clip{'' if len(clips) == 1 else 's'}) "
                    f"between {start_time} and {end_time}")

    print_clips(clips)

    are_all_clips_valid = True
    for clip in clips:
        if not validate_clip_against_files(stream_name=args.stream_name, clip=clip, file_config=config,
                                           timestamp_type=args.timestamp_type):
            are_all_clips_valid = False
            logger.error(f"{clip} does not match!!")

    are_all_clips_valid &= validate_no_fragment_overlap(clips=clips)
    are_all_clips_valid &= validate_framerate(clips=clips, file_config=config)

    if are_all_clips_valid:
        exit(0)
    else:
        exit(1)


if __name__ == '__main__':
    main()
