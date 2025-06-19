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

import psutil
from psutil import Process, NoSuchProcess, pid_exists, cpu_count


class ProcessMonitor:
    """Class to monitor process metrics including CPU and RAM usage."""

    def __init__(self, pid: int | None = None, interval: float = 0.1, output_filename: str = None):
        """
        Initialize the ProcessMonitor.

        Args:
            pid (int|None): Process ID to monitor. None for system-wide monitoring
            interval (float): Sampling interval in seconds
        """
        self.pid = pid
        self.interval = interval

        if output_filename is None:
            if pid is None:
                self.output_file = f'system_metrics.txt'
            else:
                self.output_file = f'process_{pid}_metrics.txt'
        else:
            # Placeholder text: PID
            # process_PID_metrics.txt --> process_1234_metrics.txt
            self.output_file = output_filename.replace('PID', str(pid))

        # logical cores (including hyperthreading)
        # in cloud, this is known as vCPUs
        self.cpu_count_logical = cpu_count()

        cpu_count_physical = cpu_count(logical=False)  # physical cores only
        self.cpu_percentage_max = self.cpu_count_logical * 100

        print(f"Number of CPU cores: {cpu_count_physical} physical, {self.cpu_count_logical} logical")
        print(f"Maximum possible CPU%: {self.cpu_percentage_max}%")

        if self.cpu_percentage_max > 100:
            print(f"It will be normalized to 100%")

    def get_process_metrics(self) -> tuple[str, float, float] | None:
        """
        Collect current process metrics.
        The CPU percent is on 0-1 range.

        Returns:
            tuple: (timestamp, memory_mb, cpu_percent) or None if process not found
        """
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]

        try:

            if self.pid is None:
                # System-wide monitoring
                # cpu_percent() returns value between 0-100. We need 0-1 value
                cpu_percent = psutil.cpu_percent(interval=self.interval)

                total_rss = 0
                for proc in psutil.process_iter(['memory_info']):
                    try:
                        if proc.info['memory_info'] is not None:
                            total_rss += proc.info['memory_info'].rss
                    except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                        continue

                memory_kb = total_rss / 1024
                normalized_cpu_percent = cpu_percent / 100
                return timestamp, memory_kb, normalized_cpu_percent
            else:
                # Process-specific monitoring
                # process.cpu_percent() needs to be normalized on multicore systems
                process = Process(self.pid)
                cpu_percent = process.cpu_percent(interval=self.interval)

                normalized_cpu_percent = cpu_percent / self.cpu_percentage_max
                memory_kb = process.memory_info().rss / 1024
                return timestamp, memory_kb, normalized_cpu_percent

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
                writer.writerow(['Timestamp', 'RAM (KB)', f'CPU (%) ({self.cpu_count_logical} logical CPUs/vCPUs)'])

            writer.writerow(data)

    def record_metrics_until_process_ends(self) -> None:
        """Start monitoring the process and recording metrics."""
        if self.pid is None:
            print("Capturing the RSS and CPU for the entire system")
        else:
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

    # Optional arguments
    parser.add_argument(
        'pid',
        type=int,
        nargs='?',
        help='Process ID to monitor. If not provided, monitors the entire system.'
    )

    parser.add_argument(
        '-i', '--interval',
        type=float,
        default=0.25,
        help='Sampling interval in seconds'
    )

    parser.add_argument(
        '-o', '--output',
        type=str,
        help='Name of the output file. Default: process_PID_metrics.txt'
    )

    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> None:
    """
    Checks the validity of arguments.

    Raises an error if any of the args are invalid.
    """
    # Validate PID if provided
    if args.pid is not None and not pid_exists(args.pid):
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

        monitor = ProcessMonitor(args.pid, args.interval, args.output)
        monitor.record_metrics_until_process_ends()
        return 0

    except Exception as e:
        print(f"Error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
