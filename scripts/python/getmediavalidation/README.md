# GetMediaValidation

Tools for validating, viewing, and downloading Kinesis Video Stream media fragments.

## Installation

1. Clone the repository.

2. Change directories to the project root:
    ```shell
    cd getmediavalidation
    ```

3. Create a virtual environment.
    ```shell
    python -m venv .venv
    source .venv/bin/activate  # On Windows: venv\Scripts\activate
    ```

4. Install the project's dependencies. The `-e` flag is optional but recommended. Instead of creating a static copy of
   this package in site-packages, it will create a symlink. This lets modifications made to the source be reflected immediately without
   needing to reinstall this package.
    ```shell
    pip install -e .
    ```

5. Use the scripts.
    ```shell
    python3 ./bin/download_fragment.py --help
    python3 ./bin/fetch_fragment_info.py --help
    python3 ./bin/validate_media.py --help
    ```

## Project structure

You can find the scripts in the `bin` folder.

```shell
getmediavalidation/
├── bin/              # Scripts
├── src/
│   ├── api/          # API interaction functions
│   ├── mkv/          # MKV processing functions
│   └── utils/        # Utility functions
├── requirements.txt  # Project dependencies
├── setup.py          # Package configuration
└── README.md         # This file
```

## About the scripts

### _Fetch Fragment Info_ and _Download Fragment_

These scripts are intended to be used in tandem. First, use the Fetch Fragment Info script, which will display a table
of the fragments in a stream during the specified time range. One of the columns displayed is the fragment number, which
is that fragment's ID.

You can use the Download Fragment script with that specified ID to download just that fragment. You can then use
open-source tools like `mkvtoolnix`, `ffmpeg`, or the KVS Parser Library to interact with the MKV.

Example with `mkvtoolnix` (`mkvinfo`):

```shell
sudo apt-get update
sudo apt-get install mkvtoolnix
mkvinfo -v ./downloaded-fragment.mkv
```

### _Validate Media_

This script validates frame data integrity between the original input frames and the uploaded MKV content.

The validation process ensures that frame data buffer written via `putFrame()` maintains byte-for-byte equality when
stored in the MKV container's `Simple Block`s.

In the MKV, the frame data in organized in the following hierarchical structure:

```shell
+ EBML head
|...
+ Segment: size unknown
| ...
|+ Cluster
| + Cluster timestamp: 00:00:00.000000000
| + Cluster position: 0
| + Simple block: key, track number 1, 1 frame(s), timestamp 00:00:00.000000000
|  + Frame with size 23917  # The frame is written here
```

Fragmentation Rules

- Media content is segmented into fragments
- Each fragment begins with a keyframe (boundaries are determined by keyframe occurrence)

If a continuous frame insertion loop is used with a fixed frameset, we expect the media uploaded to follow the pattern.

Given:

- A fixed keyframe interval $k$
- A finite set of $f$ frames, indexed $1$ through $f$

The first frame $f_i$ in the $i$th fragment in an uploading session $F_i$ can be calculated using:

$$ f_i = \left( \left( \left( F_i - 1 \right) * k \right) \bmod f \right) + 1 $$

Example:

- Using a keyframe interval of 25
- A set of 45 frames

The expected first frame of the fragments are: 1, 26, 6, 31, ...

| Fragment number | Calculation                  | Result   |
|-----------------|------------------------------|----------|
| 1               | ((1-1) × 25 mod 45) + 1 = 1  | Frame 1  |
| 2               | ((2-1) × 25 mod 45) + 1 = 26 | Frame 26 |
| 3               | ((3-1) × 25 mod 45) + 1 = 6  | Frame 6  |
| 4               | ((4-1) × 25 mod 45) + 1 = 31 | Frame 31 |
| 5               | ((5-1) × 25 mod 45) + 1 = 11 | Frame 11 |

We can then simply compare the bytes of the original file for equality.

If any fragments are uploaded out of order, or fragments are dropped, this script will catch it.

## Uninstalling

The name of the package is specified in the `setup.py`: `getmediautils`

To uninstall globally:

```shell
pip uninstall getmediautils
```
