#!/bin/bash

# ==========================================
# AWS Kinesis Video Stream Preparation Script
# ==========================================
# This script checks for the existence of an AWS Kinesis Video Stream.
# If the stream does not exist, it creates it with a retention period.
# If the stream exists but has no retention period set, it updates it.
# The script also waits for the stream to become available after creation.
#
# Dependencies:
# - AWS CLI: Used to interact with Kinesis Video Streams.
# - jq: Used for processing JSON responses.
#
# Usage:
#   ./prepare_stream.sh <stream-name>
#
# Example:
#   ./prepare_stream.sh my-video-stream
#
# - Region -
# To set the region, you can do:
#   AWS_DEFAULT_REGION=us-west-2 ./prepare_stream.sh my-video-stream
# Or you can set it in AWS CLI using `aws configure`
#
# Exit Codes:
#   1 - Missing dependencies or incorrect usage.
#   Non-zero exit codes from AWS CLI commands indicate failures.
#
# ==========================================

set -euo pipefail

# Constants
MAX_RETRIES=5                # Maximum retries for checking stream creation
DATA_RETENTION_HOURS=2       # Retention period in hours

# Function to check if required dependencies are installed
check_dependencies() {
    if ! command -v aws &>/dev/null; then
        echo "Error: AWS CLI is not installed. Please install it and configure credentials."
        exit 1
    fi

    if ! command -v jq &>/dev/null; then
        echo "Error: jq is not installed. Please install jq to process JSON output."
        exit 1
    fi
}

# Function to check if a stream exists
# Arguments:
#   $1 - Stream name
# Returns:
#   0 if stream exists, 1 otherwise
check_stream_exists() {
    local stream_name="$1"

    if aws kinesisvideo describe-stream --stream-name "$stream_name" --no-cli-pager >/dev/null 2>&1; then
        return 0  # Stream exists
    else
        return 1  # Stream does not exist
    fi
}

# Function to retrieve stream information as JSON
# Arguments:
#   $1 - Stream name
# Outputs:
#   JSON string containing stream details
get_stream_info() {
    local stream_name="$1"
    aws kinesisvideo describe-stream --stream-name "$stream_name" --output json
}

# Function to update data retention if it is currently 0
# Arguments:
#   $1 - Stream name
#   $2 - Stream information JSON
update_retention_if_needed() {
    local stream_name="$1"
    local stream_info="$2"

    local retention_hours
    retention_hours=$(echo "$stream_info" | jq -r '.StreamInfo.DataRetentionInHours')

    if [[ "$retention_hours" -eq 0 ]]; then
        echo "Stream '$stream_name' has no retention set. Updating to ${DATA_RETENTION_HOURS} hours..."

        # Extract the stream version required for updating retention
        local version
        version=$(echo "$stream_info" | jq -r '.StreamInfo.Version')

        # Update the data retention period
        aws kinesisvideo update-data-retention \
            --stream-name "$stream_name" \
            --current-version "$version" \
            --operation INCREASE_DATA_RETENTION \
            --data-retention-change-in-hours "$DATA_RETENTION_HOURS" \
            --no-cli-pager

        echo "Retention updated successfully."

         # Fetch stream details again to verify retention settings were actually updated
        sleep 1
        local stream_info
        stream_info=$(get_stream_info "$stream_name")
        echo "Stream '$stream_name' exists: $(echo "$stream_info" | jq -r '.StreamInfo.StreamARN')"
        update_retention_if_needed "$stream_name" "$stream_info"
    else
       echo "Stream '$stream_name' has data retention: ${DATA_RETENTION_HOURS} hours."
    fi
}

# Function to create a new stream with the defined retention period
# Arguments:
#   $1 - Stream name
create_stream() {
    local stream_name="$1"
    echo "Stream '$stream_name' does not exist. Creating..."
    aws kinesisvideo create-stream --stream-name "$stream_name" --data-retention-in-hours "$DATA_RETENTION_HOURS" --no-cli-pager
}

# Function to wait for the stream to become available, using exponential backoff
# Arguments:
#   $1 - Stream name
# Returns:
#   0 if stream becomes available, non-zero exit code otherwise
wait_for_stream_creation() {
    local stream_name="$1"

    echo "Waiting for stream '$stream_name' to be ready..."
    for i in $(seq 0 $((MAX_RETRIES - 1))); do
        sleep $((2 ** i))  # Exponential backoff (1s, 2s, 4s, 8s, 16s)

        if check_stream_exists "$stream_name"; then
            echo "Stream '$stream_name' is now available."
            return 0
        fi

        echo "Stream is still being created... (attempt $((i + 1))/$MAX_RETRIES)"
    done

    echo "Stream creation timed out."
    return 1
}

# Arguments:
#   $1 - Stream name
main() {
    if [[ $# -ne 1 ]]; then
        echo "Usage: $0 <stream-name>"
        exit 1
    fi

    local stream_name="$1"

    # Check dependencies before proceeding
    check_dependencies

    if check_stream_exists "$stream_name"; then
        # Fetch stream details and check retention settings
        local stream_info
        stream_info=$(get_stream_info "$stream_name")
        echo "Stream '$stream_name' exists: $(echo "$stream_info" | jq -r '.StreamInfo.StreamARN')"
        update_retention_if_needed "$stream_name" "$stream_info"
    else
        # Create the stream and wait for it to be ready
        create_stream "$stream_name"
        wait_for_stream_creation "$stream_name"
    fi
}

# Execute main function with arguments
main "$@"
