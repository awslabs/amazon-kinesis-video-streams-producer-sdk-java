import random
import time
from typing import TypeVar, Callable, Any, Optional, List

import botocore.exceptions

# https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_ListStreams.html#API_ListStreams_Errors
RETRYABLE_EXCEPTIONS = ['ClientLimitExceededException', 'InternalFailure']
DEFAULT_MAX_RETRY_COUNT = 5

# https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/limits.html
DEFAULT_MAX_REQUESTS_PER_SECOND = 20

T = TypeVar('T')  # Return type


def do_request_with_retries(
        func: Callable[..., T],
        args: dict[str, Any],
        max_retry_count: Optional[int] = DEFAULT_MAX_RETRY_COUNT,
        max_requests_per_second: Optional[int] = DEFAULT_MAX_REQUESTS_PER_SECOND
) -> T:
    for i in range(max_retry_count):
        try:
            response = func(**args)
            return response

        except botocore.exceptions.ClientError as e:
            print('Error Code: {}'.format(e.response['Error']['Code']))
            print('Error Message: {}'.format(e.response['Error']['Message']))
            print('Request ID: {}'.format(e.response['ResponseMetadata']['RequestId']))
            print('Http status code: {}'.format(e.response['ResponseMetadata']['HTTPStatusCode']))

            if not e.response['Error']['Code'] in RETRYABLE_EXCEPTIONS:
                # Print and exit on non-retryable errors, such as InvalidArgumentException
                raise e

            # Retry delay - At most 5 seconds
            print('Calling too fast... backing off', e)
            time.sleep(random.uniform(1 / max_requests_per_second, 5))


def do_paginate_request_with_retries(
        func: Callable[..., T],
        args: dict[str, Any],
        max_retry_count: Optional[int] = DEFAULT_MAX_RETRY_COUNT,
        max_requests_per_second: Optional[int] = DEFAULT_MAX_REQUESTS_PER_SECOND
) -> List[T]:
    responses: List[T] = []
    while True:
        response = do_request_with_retries(func=func,
                                           args=args,
                                           max_retry_count=max_retry_count,
                                           max_requests_per_second=max_requests_per_second)
        responses.append(response)

        if 'NextToken' not in response:
            return responses

        args['NextToken'] = response['NextToken']

        time.sleep(1 / max_requests_per_second)
