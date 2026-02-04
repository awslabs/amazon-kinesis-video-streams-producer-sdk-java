from datetime import datetime, timedelta
from dateutil.tz import tzlocal
from typing import List, TypeAlias

from mypy_boto3_kinesis_video_archived_media.type_defs import FragmentTypeDef
from tabulate import tabulate

# A clip is an ordered list of fragments where the gap between
# any two adjacent fragments does not exceed a certain threshold value
Clip: TypeAlias = List[FragmentTypeDef]

# Format string placeholders for fragment info
FRAGMENT_NUMBER_FORMAT_STR = "{fragment_number}"
FRAGMENT_SIZE_FORMAT_STR = "{fragment_size}"
PRODUCER_TIMESTAMP_FORMAT_STR = "{producer_timestamp}"
SERVER_TIMESTAMP_FORMAT_STR = "{server_timestamp}"
FRAGMENT_DURATION_FORMAT_STR = "{fragment_duration}"


def format_fragment_str(fragment_str: str, fragment: FragmentTypeDef) -> str:
    f"""
    Formats a string template with fragment metadata values.

    :param fragment_str: A template string containing placeholders for fragment metadata
    :param fragment: The fragment to be printed.
    :return: The formatted string with all placeholders replaced with their corresponding values

    Available format strings:
        {FRAGMENT_NUMBER_FORMAT_STR}: Fragment sequence number
        {FRAGMENT_SIZE_FORMAT_STR}: Size of the fragment in bytes
        {PRODUCER_TIMESTAMP_FORMAT_STR}: Timestamp when the fragment was produced
        {SERVER_TIMESTAMP_FORMAT_STR}: Timestamp when the fragment was received by the server
        {FRAGMENT_DURATION_FORMAT_STR}: Duration of the fragment in milliseconds    

    Example
    -------
    >>> template = "Fragment #{{fragment_number}} (Size: {{fragment_size}} bytes)"
    >>> fragment_data = {{
    ...     'FragmentNumber': '123',
    ...     'FragmentSizeInBytes': 1024,
    ...     'ProducerTimestamp': '2023-01-01T00:00:00Z',
    ...     'ServerTimestamp': '2023-01-01T00:00:01Z',
    ...     'FragmentLengthInMilliseconds': 1000
    ... }}
    >>> format_fragment_str(template, fragment_data)
    'Fragment #123 (Size: 1024 bytes)'
    """
    return (fragment_str
            .replace(FRAGMENT_NUMBER_FORMAT_STR, fragment['FragmentNumber'])
            .replace(FRAGMENT_SIZE_FORMAT_STR, str(fragment['FragmentSizeInBytes']))
            .replace(PRODUCER_TIMESTAMP_FORMAT_STR, str(fragment['ProducerTimestamp']))
            .replace(SERVER_TIMESTAMP_FORMAT_STR, str(fragment['ServerTimestamp']))
            .replace(FRAGMENT_DURATION_FORMAT_STR, str(fragment['FragmentLengthInMilliseconds']))
            )


def write_fragments_to_files(fragments: dict[str, (FragmentTypeDef, bytes)], file_pattern: 'str') -> None:
    """
    Writes fragments to the specified file pattern.

    If writing more than 1 fragment, the pattern should include placeholders.
    See :func:`format_fragment_str` for available placeholders.

    :param fragments: Dictionary mapping fragment numbers to tuples of fragment properties and fragment data
    :param file_pattern: Pattern for output filenames. Must include placeholders when writing multiple fragments.
    :return: None
    :raises ValueError: If more than one fragment is passed in and the file pattern does not contain placeholders.
    """
    for fragment_num, fragment_item in fragments.items():
        fragment_info, fragment_bytes = fragment_item
        file_name = format_fragment_str(file_pattern, fragment_info)
        if file_pattern == file_name and not len(fragments) == 1:
            raise ValueError(f"The file name {file_pattern} is not unique (missing placeholders)")

        with open(file_name, 'wb') as file:
            file.write(fragment_bytes)


def group_fragments_into_clips(fragments: List[FragmentTypeDef], max_gap_ms=2000) -> List[Clip]:
    """
    Groups a list of fragments into clips based on temporal proximity.

    This function sorts fragments by their producer timestamp and groups them into clips.
    A new clip is created when the time gap between consecutive fragments exceeds the
    specified maximum gap threshold.

    :param fragments: List of fragments to group into clips.
    :param max_gap_ms: Maximum allowed gap between fragments in milliseconds before starting a new clip.
    The time gap is calculated as the duration between the current fragment's `Producer Timestamp + Duration`
    and the next fragment's `Producer Timestamp`. Defaults to 2000ms (2 seconds).

    :return: List of clips containing every fragment once from the input list.
             Returns an empty list if input fragments list is empty.

    :raises ValueError: If the max_gap_ms is negative.
    """
    if max_gap_ms < 0:
        raise ValueError("max_gap_ms cannot be negative")

    if not fragments:
        return []

    fragments = sorted(fragments, key=lambda fragment: fragment['ProducerTimestamp'])

    clips = []
    current_clip = [fragments[0]]

    for i in range(1, len(fragments)):
        current_fragment = fragments[i]
        previous_fragment = fragments[i - 1]

        # Calculate the end time of the previous fragment
        previous_end_time = previous_fragment['ProducerTimestamp'] + \
                            timedelta(milliseconds=previous_fragment['FragmentLengthInMilliseconds'])

        # Calculate the gap between fragments
        time_gap = (current_fragment['ProducerTimestamp'] - previous_end_time).total_seconds() * 1000

        if time_gap <= max_gap_ms:
            # Add to current clip if gap is small enough
            current_clip.append(current_fragment)
        else:
            # Gap is too large, start a new clip
            clips.append(current_clip)
            current_clip = [current_fragment]

    # Add the last clip
    if current_clip:
        clips.append(current_clip)

    return clips


def print_clips(clips: List[Clip]) -> None:
    """
    Print basic information about the clip.

    :param clips: List of clips to print.
    :return: None
    """
    # Note: Could make the Clips its own class and override __str__ method
    for i, clip in enumerate(clips):
        start_time = clip[0]['ProducerTimestamp']
        end_time = clip[-1]['ProducerTimestamp'] + \
                   timedelta(milliseconds=clip[-1]['FragmentLengthInMilliseconds'])
        duration = (end_time - start_time).total_seconds()
        first_fragment_number = clip[0]['FragmentNumber']

        print(f"\nClip {i + 1}:")
        print(f"Number of fragments: {len(clip)}")
        print(f"Start time: {start_time}")
        print(f"End time: {end_time}")
        print(f"Duration: {duration:.2f} seconds")
        print(f"First fragment: {first_fragment_number}")


def print_fragments(fragments: List[FragmentTypeDef]) -> None:
    """
    Print a list of fragments in a pretty table.

    :param fragments: List of fragments to print.
    :return: None
    """
    table_data = []

    if len(fragments) == 0:
        table_data.append([
            'No fragments at this time',
            'N/A',
            'N/A',
            'N/A',
            'N/A'
        ])
    else:
        for fragment in fragments:
            table_data.append([
                fragment['FragmentNumber'],
                fragment['FragmentSizeInBytes'],
                fragment['ProducerTimestamp'].strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],  # Format time with milliseconds
                fragment['ServerTimestamp'].strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
                fragment['FragmentLengthInMilliseconds']
            ])

    # Define headers
    headers = [
        'FragmentNumber',
        'Size (B)',
        f'Producer Time ({datetime.now(tz=tzlocal()).strftime("%z")})',
        f'Server Time ({datetime.now(tz=tzlocal()).strftime("%z")})',
        'Length (ms)'
    ]

    print(tabulate(table_data, headers, tablefmt='grid'))
