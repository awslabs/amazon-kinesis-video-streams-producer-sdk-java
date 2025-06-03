from typing import List
import logging
import glob
from pathlib import Path

logger = logging.getLogger(__name__)


def read_frame_files(path_pattern: str) -> List[bytes]:
    """
    Read binary content from files matching the path pattern.
    If path_pattern is a directory, read all files within it (1 level deep).

    :param path_pattern: File path pattern or directory path
    :returns: List of binary content from all matching files
    """
    files: List[bytes] = []

    path = Path(path_pattern)

    # If path is a directory, get all files in it
    if path.is_dir():
        # Get all files in directory
        all_files = [str(p) for p in path.iterdir() if p.is_file()]
    else:
        # Use glob for pattern matching if not a directory
        all_files = glob.glob(path_pattern)

    # Sort files for consistent ordering
    all_files.sort()

    for file_path in all_files:
        try:
            with open(file_path, 'rb') as file:
                files.append(file.read())
        except (PermissionError, IOError) as e:
            logger.error(f"Error reading file {file_path}: {str(e)}")
            continue

    return files
