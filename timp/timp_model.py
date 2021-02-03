"""
TIMP (Time-Inhomogeneous Markov Process) Data_Stall Recovery Trigger Modeling
~~~~~~~~~~~~~~~~~~~~~
TIMP model is for determining proper triggers of Data_Stall recovery

Currently, Android uses 1 minute as the sole trigger for recovery, which is found to be inefficient.
To overcome this, we develop a TIMP a formalize and model the entire Data_Stall-Recovery process.
Based on this, we calculate the most suitable triggers that may result in the global minimum of recovery time.

Note that to run this model, you may need Data_Stall duration data (which we will not disclose yet), as well as
devices' recovery stages

:copyright: (c) 2020 by Cellular Reliability Team.
:license: Apache 2.0, see LICENSE for more details.
"""
import numpy as np
import os
import multiprocessing
from functools import partial


def integrate_over_threshold_for_stage(thresholds, stage):
    """
    Calculate the integration of event duration over different thresholds (triggers) for each recovery stage
    :param thresholds: recovery triggers for entering the next recovery stage
    :param stage: current recovery stage
    :return: integration value
    """
    durations_for_stage = durations_for_stages[stage]
    start = 0
    for i in range(stage):
        start += thresholds[i]
    if stage != 3:
        end = start + thresholds[stage]
    else:
        end = durations_for_stage[-1]

    durations_over_threshold = durations_for_stage[np.where(durations_for_stage <= end)[0]]
    durations_over_threshold = durations_over_threshold[np.where(durations_over_threshold >= start)[0]]
    if len(durations_over_threshold) == 0:
        return 0
    return np.average(durations_over_threshold)


def penalties():
    """
    Penalties for executing each recovery operation
    :return: penalties
    """
    durations_over_threshold = [duration for duration in durations if duration <= 60000]
    penalty = np.average(durations_over_threshold)
    return [0, penalty, 2 * penalty, 3 * penalty]


def cdf_for_stages(thresholds):
    """
    Calculate the CDF of event durations for each recovery stage
    Here recovery stages are divided using given thresholds
    :param thresholds: recovery triggers for entering the next recovery stage
    :return: CDF
    """
    end = 0
    cdf = list()
    for stage in range(3):
        end += thresholds[stage]
        cdf.append(len(np.where(durations <= end)[0]) / len(durations))
    return cdf


def overhead(thresholds, cdf, stage=0):
    """
    The expected recovery time starting at corresponding stage
    This function is in recursive form so that we know the overall expected recovery time when stage is 0
    :param thresholds: recovery triggers for entering the next recovery stage
    :param cdf: CDF for durations in different recovery stages
    :param stage: current recovery stage
    :return: the expected recovery time
    """
    if len(thresholds) != 3 or len(durations_for_stages) != 4:
        raise Exception("Threshold values not enough!")

    if stage == 3:
        return recovery_penalties[stage] + \
               integrate_over_threshold_for_stage(thresholds, stage)

    return recovery_penalties[stage] + \
           integrate_over_threshold_for_stage(thresholds, stage) + \
           (1 - cdf[stage]) * overhead(thresholds, cdf, stage + 1)


def data_processing():
    """
    Dedicated data processing function that turns CSV to NPY file for fast data loading
    :return: None
    """
    for file_name in os.listdir(os.getcwd()):
        if not (file_name.startswith("DATA_STALL") and file_name.endswith(".csv")):
            continue
        file_name = file_name[:-4]
        data = list()
        with open("{}.csv".format(file_name), 'r') as file:
            for line in file:
                line = int(line.strip())
                if line <= 86400000:
                    data.append(line)
        data.reverse()
        np.save("{}-mod.npy".format(file_name), np.array(data))


def prepare_data():
    """
    Prepare all local data
    To run this, you'll need corresponding Data_Stall duration data for each recovery stage
    :return: None
    """
    global durations, recovery_penalties
    files = list()
    for file in os.listdir(os.getcwd()):
        if file.endswith("mod.npy"):
            files.append(file)
    files = np.sort(files)
    for file in files:
        if file.endswith("all-mod.npy"):
            durations = np.load(file)
            continue
        print("Preparing {}...".format(file))
        durations_for_stages.append(np.load(file))

    print("Preparing penalties...")
    recovery_penalties = penalties()
    print("Preparing CDF...")
    print("End data preparations.")


def loss(threshold1, threshold2, threshold3):
    """
    The loss function that should be minimized for different thresholds, i.e., the expected recovery time
    :param threshold1: the first trigger
    :param threshold2: the second trigger
    :param threshold3: the third trigger
    :return: loss
    """
    thresholds = [threshold1, threshold2, threshold3]
    print(thresholds)
    cdf = cdf_for_stages(thresholds)
    result = overhead(thresholds, cdf, 0)
    return result


def brute_force():
    """
    Used for searching the global minimum (can be replaced by dual annealing or others)
    :return: loss values for the entire trigger space
    """
    cores = multiprocessing.cpu_count()
    p = multiprocessing.Pool(processes=cores)
    result = list()
    for i in range(0, 60000, 1000):
        for j in range(0, 60000, 1000):
            par = partial(loss, threshold2=i, threshold3=j)
            res = p.map(par, range(0, 60000, 1000))
            result.extend(res)
    return result


if __name__ == '__main__':
    # overall durations
    durations = list()
    # durations for events recovered in each stage
    durations_for_stages = list()
    recovery_penalties = list()
    prepare_data()
    # currently we use brute force to better present our results (since all search space is considered)
    # we strongly recommend using other global optimization algorithms such as dual annealing for faster iteration
    results = brute_force()
    # print the global minimum and its corresponding index
    print(np.min(results))
    print(np.argmin(results))
    np.save("results.npy", results)
