import ebmlite


def verify_first_frame_matches(first: str | bytes, second: str | bytes):
    """
    Returns whether the first frame in the MKV matches.

    :param first: The first file. Can either be a file path or the bytes of the MKV.
    :param second: The second file. Can either be a file path or the bytes of the MKV.
    :return:
    """
    a: bytes = MkvFile(mkv_file=first).extract_first_frame()
    b: bytes = MkvFile(mkv_file=second).extract_first_frame()

    # Note: == will compare all the bytes for equality (deep)
    return a == b


class MkvFile:
    def __init__(self, mkv_file: str | bytes):
        """
        Parses through the specified MKV file.

        :param mkv_file: Path to the MKV file, or the byte contents of the file.
        """
        mkv_schema = ebmlite.loadSchema("matroska.xml")

        if isinstance(mkv_file, bytes):
            self.doc = mkv_schema.loads(mkv_file).dump()
        else:
            self.doc = mkv_schema.load(mkv_file).dump()

    def get_first_frame_bytes(self) -> bytes:
        """
        Return only the first frame's bytes.
        When putKinesisVideoFrame is called, this will match the data buffer exactly.

        :return: The bytes of the first frame, excluding any simple block metadata.

        See Also
        --------
        extract_first_frame
        """
        return self.extract_first_frame()[4:]

    def extract_first_frame(self) -> bytes:
        """
        Return the bytes of the first frame of the MKV.
        The first frame is located in Document->Segment->Cluster->SimpleBlock
        Note: The first 4 bytes of the returned value contain the simple block metadata,
        usually (\x81\x00\x00\x80), which is:
        - \x81 = Track #; 1
        - \x00\x00 = Timestamp offset from Cluster timestamp; 0
        - \x80 = Frame flags; this frame is a keyframe

        ```
        + EBML head
        |...
        + Segment: size unknown
        | ...
        |+ Cluster
        | + Cluster timestamp: 00:00:00.000000000
        | + Cluster position: 0
        | + Simple block: key, track number 1, 1 frame(s), timestamp 00:00:00.000000000
        |  + Frame with size 23917
        ```

        :return: The bytes of the Simple Block contents
        """
        segment = self.doc['Segment'][0]
        cluster = segment['Cluster'][0]
        simple_block = cluster['SimpleBlock']
        return simple_block[0]

    def extract_cluster_timestamp(self) -> bytes:
        """
        Extract the timestamp from the cluster.

        :return: The cluster timestamp
        """
        segment = self.doc['Segment'][0]
        cluster = segment['Cluster'][0]
        return cluster['Timecode']
