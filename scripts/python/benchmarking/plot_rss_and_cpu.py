import argparse
import re
from datetime import timedelta
import logging
import os
from typing import List, Tuple, Optional

import matplotlib.pyplot as plt
from matplotlib.colors import TABLEAU_COLORS
import numpy as np

blue_color = TABLEAU_COLORS['tab:blue']
orange_color = TABLEAU_COLORS['tab:orange']
green_color = TABLEAU_COLORS['tab:green']

SECONDS_PER_DAY = 86400

logger = logging.getLogger(__name__)


def parse_rss_file(file_path: str) -> Tuple[np.array, np.array, np.array, int]:
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")

    times = []
    rss_values = []
    cpu_values = []
    start_time = None
    cpus = 0

    with open(file_path, 'r', encoding='utf-8') as file:
        for line in file:
            try:
                # Extract CPU count from the header line: 'Timestamp,RAM (KB),CPU (%) (N logical CPUs/vCPUs)'
                if 'Timestamp' in line:
                    cpus = int(re.search(r'\((\d+)\s+logical', line).group(1))
                    continue

                timestamp_str, rss_str, cpu_str = line.split(',')

                # If the timestamp starts with a date (e.g. '2025-05-19 20:21:24.420'), chop it off
                if ' ' in timestamp_str:
                    timestamp_str = timestamp_str.split(' ')[1]

                h, m, s = map(float, timestamp_str.split(':'))
                current_time = timedelta(hours=h, minutes=m, seconds=s)

                if start_time is None:
                    start_time = current_time

                elapsed_time = (current_time - start_time).total_seconds()

                # To handle the case that it rolls over midnight
                if elapsed_time < 0:
                    elapsed_time += SECONDS_PER_DAY

                rss_value = float(rss_str)
                cpu_value = float(cpu_str)

                times.append(elapsed_time)
                rss_values.append(rss_value)
                cpu_values.append(cpu_value)
            except Exception as e:
                logger.error(f"Error parsing line in {file_path}: {line}. Error: {e}")

    if len(times) == 0 or len(rss_values) == 0:
        raise ValueError(f'The file {file_path} is bad or corrupted!')

    return np.asarray(times), np.asarray(rss_values), np.asarray(cpu_values), cpus


def convert_memory_units(memory_values: np.ndarray) -> Tuple[np.ndarray, str]:
    """
    Convert memory values to appropriate units (KiB, MiB, or GiB).

    Args:
        memory_values: Array of memory values in KiB

    Returns:
        Tuple of (converted values, unit string)
    """
    max_value = np.max(memory_values)

    if max_value > 1024 * 1024:  # More than 1 GiB
        return memory_values / (1024 * 1024), 'GiB'
    elif max_value > 1024:  # More than 1 MiB
        return memory_values / 1024, 'MiB'
    else:
        return memory_values, 'KiB'


def plot_rss_and_cpu(data_set: Tuple[np.ndarray, np.ndarray, np.ndarray, str],
                     key_points: Optional[List[Tuple[float, str]]] = None,
                     save_path: Optional[str] = None,
                     cpu_count: Optional[int] = 0,
                     title: Optional[str] = None,
                     cpu_bounds: Tuple[Optional[int], Optional[int]] = None,
                     rss_bounds: Tuple[Optional[int], Optional[int]] = None) -> None:
    """
    Plot RSS and CPU usage over time.

    Args:
        data_set: Tuple containing (times, rss_values, cpu_values, label)
        key_points: List of tuples containing (time, label) for marking points
        cpu_count: Number of logical CPUs the device the data was captured on
        save_path: Path to save the plot
        title: Title of the plot
        rss_bounds: y-axis overrides (lower, upper) for RSS
        cpu_bounds: y-axis overrides (lower, upper) for CPU
    """
    fig, ax1 = plt.subplots(figsize=(12, 6))
    ax2 = ax1.twinx()

    alpha = 0.5
    marker = None

    times, rss_values, cpu_values, label = data_set

    converted_rss, rss_unit = convert_memory_units(rss_values)

    # Plot RSS on left y-axis
    plot_label = f'{label} RSS'
    ax1.plot(times, converted_rss,
             marker=marker, linestyle='-', color=blue_color, alpha=alpha, label=plot_label)

    # Plot CPU on right y-axis
    cpu_label = f'{label} CPU%'
    ax2.plot(times, cpu_values,
             marker=marker, linestyle='--', color=orange_color, alpha=alpha, label=cpu_label)

    plt.title(title.replace('\\n', '\n'), fontsize='x-large')
    ax1.set_xlabel('Time (seconds since start)', fontsize='large')
    ax1.set_ylabel(f'RSS ({rss_unit})', fontsize='large', color=blue_color)
    ax2.set_ylabel(f'CPU %{f'\n({cpu_count} logical CPUs/vCPUs)' if cpu_count > 0 else ''}', fontsize='large', color=orange_color)

    # Set grid
    ax1.grid(True)

    # Set x-axis bounds
    ax1.set_xlim(left=0)
    ax2.set_xlim(left=0)

    max_x = np.max(times)
    ax1.set_xlim(right=max_x)
    ax2.set_xlim(right=max_x)

    # RSS upper and lower bounds
    if rss_bounds[0] is not None:
        ax1.set_ylim(bottom=rss_bounds[0])
    else:
        ax1.set_ylim(bottom=0)

    if rss_bounds[1] is not None:
        ax1.ylim(top=rss_bounds[1])
    # Otherwise, default top is used

    # CPU upper and lower bounds
    if cpu_bounds[0] is not None:
        ax2.set_ylim(bottom=cpu_bounds[0])
    else:
        ax2.set_ylim(bottom=0)

    if cpu_bounds[1] is not None:
        ax2.set_ylim(top=cpu_bounds[1])
    else:
        # Since it's a percentage, we can max it at 100
        ax2.set_ylim(top=100)

    if key_points:
        # Get the current y-axis limits
        y_min, y_max = ax1.get_ylim()
        usable_range = y_max - y_min

        for time, label in key_points:
            ax1.axvline(x=time, color=green_color, linestyle=':')

            # Find all values within a small window around the vertical line
            window = 2  # seconds
            window_indices = np.where(np.abs(times - time) <= window)[0]
            window_values = converted_rss[window_indices]

            # Define possible positions within the visible graph area
            positions = [
                (y_min + 0.15 * usable_range, 'bottom'),  # Near bottom
                (y_min + 0.3 * usable_range, 'bottom'),  # Lower third
                (y_min + 0.5 * usable_range, 'center'),  # Middle
                (y_max - 0.3 * usable_range, 'top'),  # Upper third
                (y_max - 0.15 * usable_range, 'top')  # Near top
            ]

            # For each position, calculate the distance to the closest data point
            best_position = None
            max_min_distance = -float('inf')

            for pos, alignment in positions:
                # Calculate distances to all points in the window
                distances = np.abs(window_values - pos)
                min_distance = np.min(distances) if len(distances) > 0 else float('inf')

                # Update if this position is further from any data point
                if min_distance > max_min_distance:
                    max_min_distance = min_distance
                    best_position = (pos, alignment)

            label_y, vertical_alignment = best_position

            # Add the text with a white background
            ax1.text(time, label_y, label,
                     rotation=90,
                     verticalalignment=vertical_alignment,
                     horizontalalignment='center',
                     bbox={"facecolor": 'white',
                           "alpha": 0.8,
                           "edgecolor": 'none',
                           "pad": 2})

    plt.gca().xaxis.set_major_formatter(
        plt.FuncFormatter(
            lambda x, _: str(timedelta(seconds=int(x)))
        )
    )

    plt.subplots_adjust(bottom=0.2)  # Adding space for the legend
    lgd1 = ax1.legend(loc='upper center', bbox_to_anchor=(0.3, -0.15))
    lgd2 = ax2.legend(loc='upper center', bbox_to_anchor=(0.7, -0.15))

    if save_path:
        plt.savefig(save_path, bbox_extra_artists=(lgd1, lgd2), bbox_inches='tight')
        logger.info(f"Plot saved to {save_path}")
    else:
        plt.show()


def main():
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s [%(filename)s:%(lineno)s/%(funcName)-s()] [%(levelname)s] %(message)s',
        handlers=[
            logging.StreamHandler()  # Outputs logs to the console
        ]
    )

    parser = argparse.ArgumentParser(description='Plot RSS memory usage over time.')
    parser.add_argument('data_file', help='Input file')
    parser.add_argument('--output', '-o',
                        help='Path to save the output plot (default: None, '
                             'example: rss_memory_usage_plot.png)')
    parser.add_argument('--title', '-t', default='RSS and CPU Memory Usage Over Time',
                        help='Title for the graph (default: "Memory and CPU Usage Over Time")')
    parser.add_argument('--key-points', '-k', nargs=2, action='append',
                        metavar=('TIME', 'LABEL'),
                        help='Key points (in seconds) to mark with vertical labels. '
                             'Can be used multiple times.')
    parser.add_argument('--rss-min', type=int, help='Minimum value for y-axis RSS, default=0', default=0)
    parser.add_argument('--rss-max', type=int, help='Maximum value for y-axis RSS')
    parser.add_argument('--cpu-min', type=int, help='Minimum value for y-axis CPU, default=0', default=0)
    parser.add_argument('--cpu-max', type=int, help='Maximum value for y-axis CPU, default=100', default=100)

    args = parser.parse_args()

    key_points = []
    if args.key_points:
        for time, label in args.key_points:
            key_points.append((float(time), label))

    times, rss_values, cpu_values, cpu_count = parse_rss_file(args.data_file)
    data_set = (times, rss_values, cpu_values, args.data_file)

    plot_rss_and_cpu(data_set=data_set,
                     key_points=key_points,
                     save_path=args.output,
                     cpu_count=cpu_count,
                     title=args.title,
                     cpu_bounds=(args.cpu_min, args.cpu_max),
                     rss_bounds=(args.rss_min, args.rss_max))


if __name__ == "__main__":
    main()
