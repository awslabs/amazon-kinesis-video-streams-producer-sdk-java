#!/usr/bin/env python3

"""
Process Monitor Script

This script monitors CPU and RAM usage of a specified process using its PID.
Metrics are recorded to a CSV file until the process terminates.
"""

import argparse
import csv
import os
import sys
import time

from datetime import datetime
from psutil import Process, NoSuchProcess, pid_exists


class ProcessMonitor:
    """Class to monitor process metrics including CPU and RAM usage."""

    def __init__(self, pid: int, interval: float = 0.1):
        """
        Initialize the ProcessMonitor.

        Args:
            pid (int): Process ID to monitor
            interval (float): Sampling interval in seconds
        """
        self.pid = pid
        self.interval = interval
        self.output_file = f'process_{pid}_metrics.txt'

    def get_process_metrics(self) -> tuple[str, float, float] | None:
        """
        Collect current process metrics.

        Returns:
            tuple: (timestamp, memory_mb, cpu_percent) or None if process not found
        """
        try:
            process = Process(self.pid)
            cpu_percent = process.cpu_percent(interval=0.1)
            memory_kb = process.memory_info().rss / 1024
            timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]

            return timestamp, memory_kb, cpu_percent

        except NoSuchProcess:
            print(f"Process with PID {self.pid} no longer exists")
            return None
        except Exception as e:
            print(f"Error: {e}")
            return None

    def write_to_csv(self, data: tuple[str, float, float]) -> None:
        """
        Write metrics to CSV file.

        Args:
            data (tuple): (timestamp, memory_kb, cpu_percent)
        """
        file_exists = os.path.isfile(self.output_file)

        with open(self.output_file, 'a', newline='', encoding='utf-8') as csvfile:
            writer = csv.writer(csvfile)

            if not file_exists:
                writer.writerow(['Timestamp', 'RAM (KB)', 'CPU (%)'])

            writer.writerow(data)

    def record_metrics_until_process_ends(self) -> None:
        """Start monitoring the process and recording metrics."""
        print(f"Starting monitoring of PID {self.pid}")
        print(f"Writing data to {self.output_file}")

        try:
            while True:
                metrics = self.get_process_metrics()

                if metrics is None:
                    print("Process monitoring ended")
                    break

                self.write_to_csv(metrics)

                time.sleep(self.interval)

        except KeyboardInterrupt:
            print("\nInterrupted by user")
        except Exception as e:
            print(f"Error: {e}")


def parse_arguments() -> argparse.Namespace:
    """
    Parse command line arguments.

    Returns:
        argparse.Namespace: Parsed command line arguments
    """
    parser = argparse.ArgumentParser(
        description='Monitor CPU and RAM usage of a process.',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )

    parser.add_argument(
        'pid',
        type=int,
        help='Process ID to monitor'
    )

    parser.add_argument(
        '-i', '--interval',
        type=float,
        default=0.1,
        help='Sampling interval in seconds'
    )

    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> None:
    """
    Checks the validity of arguments.

    Raises an error if any of the args are invalid.
    """
    # Validate PID
    if not pid_exists(args.pid):
        raise ValueError(f"Error: Process with PID {args.pid} does not exist")

    # Validate interval
    if args.interval <= 0:
        raise ValueError("Error: Interval must be greater than 0")


def main() -> int:
    """
    Main function to run the process monitor.

    Returns:
        int: Exit code (0 for success, 1 for error)
    """
    try:
        args = parse_arguments()

        validate_args(args)

        monitor = ProcessMonitor(args.pid, args.interval)
        monitor.record_metrics_until_process_ends()
        return 0

    except Exception as e:
        print(f"Error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
