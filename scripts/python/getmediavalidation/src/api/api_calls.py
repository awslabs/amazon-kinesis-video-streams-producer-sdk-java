from datetime import datetime, timedelta
from typing import List

import boto3
from dateutil.tz import tzlocal
from mypy_boto3_kinesis_video_archived_media import KinesisVideoArchivedMediaClient
from mypy_boto3_kinesis_video_archived_media.literals import FragmentSelectorTypeType
from mypy_boto3_kinesis_video_archived_media.type_defs import TimestampTypeDef, TimestampRangeTypeDef, \
    FragmentSelectorTypeDef, ListFragmentsInputTypeDef, ListFragmentsOutputTypeDef, GetMediaForFragmentListInputTypeDef, \
    FragmentTypeDef
from mypy_boto3_kinesisvideo import KinesisVideoClient
from mypy_boto3_kinesisvideo.literals import APINameType
from mypy_boto3_kinesisvideo.type_defs import GetDataEndpointInputTypeDef

from src.mkv.fragment_util import group_fragments_into_clips, Clip
from .retry_util import do_request_with_retries, do_paginate_request_with_retries

KINESIS_VIDEO_SERVICE_NAME = 'kinesisvideo'
KINESIS_VIDEO_ARCHIVED_MEDIA_SERVICE_NAME = 'kinesis-video-archived-media'


def fetch_data_endpoint(stream_name: str, endpoint_type: APINameType) -> str:
    """
    Fetches a stream's endpoint for the specified API.

    :param stream_name: Stream name
    :param endpoint_type: API action's endpoint to fetch.
    :return: The stream's endpoint for the specified API action.
    """
    kvs_client: KinesisVideoClient = boto3.client(service_name=KINESIS_VIDEO_SERVICE_NAME)

    get_data_endpoint_args: GetDataEndpointInputTypeDef = {'StreamName': stream_name,
                                                           'APIName': endpoint_type}

    return do_request_with_retries(func=kvs_client.get_data_endpoint,
                                   args=get_data_endpoint_args)['DataEndpoint']


def list_fragments(stream_name: str,
                   fragment_selector_type: FragmentSelectorTypeType = "PRODUCER_TIMESTAMP",
                   start_timestamp: datetime = None,
                   end_timestamp: datetime = None) -> List[FragmentTypeDef]:
    # Defaults: Up to 1 day ago
    now = datetime.now(tz=tzlocal())
    if start_timestamp is None:
        start_timestamp: TimestampTypeDef = now - timedelta(days=1)
    if end_timestamp is None:
        end_timestamp: TimestampTypeDef = now

    list_fragments_endpoint: str = fetch_data_endpoint(stream_name=stream_name, endpoint_type='LIST_FRAGMENTS')

    archived_media_client: KinesisVideoArchivedMediaClient \
        = boto3.client(service_name=KINESIS_VIDEO_ARCHIVED_MEDIA_SERVICE_NAME,
                       endpoint_url=list_fragments_endpoint)

    timestamp_range: TimestampRangeTypeDef = {
        'StartTimestamp': start_timestamp,
        'EndTimestamp': end_timestamp,
    }

    fragment_selector: FragmentSelectorTypeDef = {
        'FragmentSelectorType': fragment_selector_type,
        'TimestampRange': timestamp_range
    }

    list_fragments_args: ListFragmentsInputTypeDef = {
        'StreamName': stream_name,
        'FragmentSelector': fragment_selector
    }

    fragments_responses: [ListFragmentsOutputTypeDef] = do_paginate_request_with_retries(
        func=archived_media_client.list_fragments,
        args=list_fragments_args)

    fragments = [fragment
                 for fragments_response in fragments_responses
                 for fragment in fragments_response['Fragments']]

    fragments.sort(key=lambda fragment: fragment['ProducerTimestamp'])

    return fragments


def fetch_fragments(stream_name: str, fragment_numbers: List[str]) -> dict[str, bytes]:
    get_media_for_fragment_list_endpoint: str = fetch_data_endpoint(stream_name=stream_name,
                                                                    endpoint_type='GET_MEDIA_FOR_FRAGMENT_LIST')

    archived_media_client: KinesisVideoArchivedMediaClient \
        = boto3.client(service_name=KINESIS_VIDEO_ARCHIVED_MEDIA_SERVICE_NAME,
                       endpoint_url=get_media_for_fragment_list_endpoint)

    fragments: dict[str, bytes] = {}

    for fragment_number in fragment_numbers:
        get_media_for_fragment_list_args: GetMediaForFragmentListInputTypeDef = {'StreamName': stream_name,
                                                                                 'Fragments': [fragment_number]}

        response = do_request_with_retries(func=archived_media_client.get_media_for_fragment_list,
                                           args=get_media_for_fragment_list_args)

        fragments[fragment_number] = response['Payload'].read()

    return fragments


def fetch_n_clips(stream_name: str, n: int, max_gap_ms=2000) -> List[Clip]:
    """
    Fetch the most recent N clips from the Stream.

    :param stream_name: Stream name
    :param n: Number of clips to fetch
    :param max_gap_ms: Maximum gap between a fragment for it to be considered a clip.
    :return: List of fragments representing the clips
    """

    # Use sliding window backwards in time to fetch the fragments
    window_duration: timedelta = timedelta(hours=1)
    window_end: datetime = datetime.now(tz=tzlocal())
    window_start: datetime = window_end - window_duration

    clips: List[Clip] = []
    current_fragments: List[FragmentTypeDef] = []
    while len(clips) < n:
        fragments_in_window: List[FragmentTypeDef] = list_fragments(stream_name=stream_name,
                                                                    fragment_selector_type="PRODUCER_TIMESTAMP",
                                                                    start_timestamp=window_start,
                                                                    end_timestamp=window_end)

        window_is_empty: bool = len(fragments_in_window) == 0
        current_fragments.extend(fragments_in_window)

        fragments_in_window.sort(key=lambda frag: frag['ProducerTimestamp'])
        current_clips = group_fragments_into_clips(fragments=current_fragments, max_gap_ms=max_gap_ms)

        while window_is_empty and len(current_clips) > 0 and len(clips) < n:
            current_clip = current_clips.pop()
            clips.append(current_clip)

            current_fragments = current_fragments[:-len(current_clip)]

        window_end -= window_duration
        window_start -= window_duration

    return clips
