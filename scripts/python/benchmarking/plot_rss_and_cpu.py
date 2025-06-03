import argparse
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


def parse_rss_file(file_path: str) -> Tuple[np.array, np.array, np.array]:
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"File not found: {file_path}")

    times = []
    rss_values = []
    cpu_values = []
    start_time = None

    with open(file_path, 'r', encoding='utf-8') as file:
        for line in file:
            try:
                # Skip the header line: 'Timestamp,RAM (KB),CPU (%)'
                if 'Timestamp' in line:
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

    return np.asarray(times), np.asarray(rss_values), np.asarray(cpu_values)


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
                     title: Optional[str] = None,
                     x_max: Optional[float] = None,
                     y_min: Optional[float] = None,
                     y_max: Optional[float] = None) -> None:
    """
    Plot RSS and CPU usage over time.

    Args:
        data_set: Tuple containing (times, rss_values, cpu_values, label)
        key_points: List of tuples containing (time, label) for marking points
        save_path: Path to save the plot
        title: Title of the plot
        x_max: Maximum value for x-axis
        y_min: Minimum value for y-axis
        y_max: Maximum value for y-axis
    """
    fig, ax1 = plt.subplots(figsize=(12, 6))
    ax2 = ax1.twinx()

    alpha = 0.5
    marker = None

    times, rss_values, cpu_values, label = data_set

    converted_rss, rss_unit = convert_memory_units(rss_values)

    if x_max is not None:
        # Truncate data beyond the maximum x-value
        within_x_max = times <= x_max
        times = times[within_x_max]
        rss_values = rss_values[within_x_max]

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
    ax2.set_ylabel('CPU %', fontsize='large', color=orange_color)

    # Set grid
    ax1.grid(True)

    # Set x-axis limits
    ax1.set_xlim(left=0)
    if x_max is not None:
        ax1.set_xlim(right=x_max)

    # Set y-axis limits if provided
    if y_min is not None:
        ax1.ylim(bottom=y_min)
    if y_max is not None:
        ax1.ylim(top=y_max)

    if key_points:
        min_rss = np.min(rss_values)
        max_rss = np.max(rss_values)
        y_range = max_rss - min_rss
        for time, label in key_points:
            ax1.axvline(x=time, color=green_color, linestyle=':')

            # Find the nearest data point in the first dataset for positioning
            nearest_index = np.argmin(np.abs(data_set[0] - time))
            nearest_value = data_set[1][nearest_index]

            # Determine best label position
            label_positions = [
                (max_rss - 0.1 * y_range, 'center_baseline'),  # Near top
                (min_rss + 0.1 * y_range, 'baseline'),  # Near bottom
                (nearest_value + 0.1 * y_range, 'baseline'),  # Above nearest point
                (nearest_value - 0.1 * y_range, 'baseline')  # Below nearest point
            ]

            if y_min is not None:
                # Just above y-min
                label_positions.append((y_min + 0.1 * y_range, 'baseline'))

            if y_max is not None:
                # Just below y-max
                label_positions.append((y_max - 0.1 * y_range, 'center_baseline'))

            # Choose the position furthest from the nearest data point
            label_y, vertical_alignment = max(label_positions,
                                              key=lambda y: abs(y[0] - nearest_value))

            ax1.text(time, label_y, label,
                     rotation=90,
                     verticalalignment=vertical_alignment,
                     horizontalalignment='center',
                     bbox={"facecolor": 'white', "alpha": 0.7, "edgecolor": 'none'})

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
    parser.add_argument('--y-min',
                        type=int, help='Minimum value for y-axis')
    parser.add_argument('--y-max',
                        type=int, help='Maximum value for y-axis')
    parser.add_argument('--x-max',
                        type=int, help='Maximum value for x-axis (seconds).')

    parser.add_argument('--same-color', action='store_true',
                        help='Plot all files with the same color and lower '
                             'opacity with a single legend label')

    args = parser.parse_args()

    key_points = []
    if args.key_points:
        for time, label in args.key_points:
            key_points.append((float(time), label))

    times, rss_values, cpu_values = parse_rss_file(args.data_file)
    data_set = (times, rss_values, cpu_values, args.data_file)

    plot_rss_and_cpu(data_set=data_set,
                     key_points=key_points,
                     save_path=args.output,
                     title=args.title,
                     x_max=args.x_max,
                     y_min=args.y_min,
                     y_max=args.y_max)


if __name__ == "__main__":
    main()
