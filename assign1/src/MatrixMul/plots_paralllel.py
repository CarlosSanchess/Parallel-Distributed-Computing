import matplotlib.pyplot as plt

# Matrix sizes
matrix_sizes = [600, 1000, 1400, 1800, 2200, 2600, 3000, 4096, 6144, 8192, 10240]

# Execution times (in seconds) for outer and inner loop parallelization
outer_times = [0.027, 0.116, 0.277, 0.528, 1.131, 2.086, 3.559, 8.299, 30.345, 73.170, 216.851]
inner_times = [0.170, 0.458, 1.340, 2.950, 5.079, 7.828, 11.612, 27.848, 73.143, 152.773, 285.668]

# L1 DCM for outer and inner loop parallelization
outer_l1_dcm = [1924091, 9370417, 41090728, 90382230, 167081583, 280300693, 429083491, 1091464928, 3683033942, 8723285628, 16994047992]
inner_l1_dcm = [4303194, 19228602, 47916291, 93257787, 150167551, 225078374, 327859145, 856407327, 2636101874, 6077988446, 12285236091]

# L2 DCM for outer and inner loop parallelization
outer_l2_dcm = [17330, 60217, 104111, 149096, 236023, 555219, 898157, 2332638, 9221214, 27789743, 131991061]
inner_l2_dcm = [2220916, 5246945, 13397481, 34408060, 125759014, 197407259, 303356737, 657927564, 1002581155, 1439078099, 1986263686]

# FP OPS for outer and inner loop parallelization
outer_fp_ops = [27360001, 126000001, 344960001, 732240001, 1335840001, 2203760001, 3384000001, 8589934593, 28991029286, 68719476737, 134217736591]
inner_fp_ops = [27360001, 126000001, 344960001, 732240001, 1335840001, 2203760001, 3384000001, 8589934593, 28991029249, 68719476737, 134217728001]

# TOT INS for outer and inner loop parallelization
outer_tot_ins = [113622169, 511795717, 1392538475, 2949387953, 5372806495, 8855308697, 13588892279, 34454331821, 116178953875, 275258134329, 537461781358]
inner_tot_ins = [176516701, 622628615, 1649689186, 3495964625, 6020016060, 9385227382, 14234349974, 35654285153, 116461371453, 269361045658, 514585201807]

# Plotting Execution Time Comparison
plt.figure(figsize=(12, 6))
plt.plot(matrix_sizes, outer_times, label='Outer Loop Parallelization', marker='o', linestyle='-', color='blue')
plt.plot(matrix_sizes, inner_times, label='Inner Loop Parallelization', marker='s', linestyle='--', color='red')
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Execution Time (seconds)')
plt.title('Execution Time Comparison: Outer Loop vs Inner Loop Parallelization')
plt.legend()
plt.grid(True)
plt.show()

# Plotting L1 vs L2 DCM for Outer Loop Parallelization
plt.figure(figsize=(12, 6))
plt.plot(matrix_sizes, outer_l1_dcm, label='Outer Loop L1 DCM', marker='o', linestyle='-', color='blue')
plt.plot(matrix_sizes, outer_l2_dcm, label='Outer Loop L2 DCM', marker='s', linestyle='--', color='red')
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Data Cache Misses (DCM)')
plt.title('L1 vs L2 Data Cache Misses: Outer Loop Parallelization')
plt.legend()
plt.grid(True)
plt.show()

# Plotting L1 vs L2 DCM for Inner Loop Parallelization
plt.figure(figsize=(12, 6))
plt.plot(matrix_sizes, inner_l1_dcm, label='Inner Loop L1 DCM', marker='o', linestyle='-', color='blue')
plt.plot(matrix_sizes, inner_l2_dcm, label='Inner Loop L2 DCM', marker='s', linestyle='--', color='red')
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Data Cache Misses (DCM)')
plt.title('L1 vs L2 Data Cache Misses: Inner Loop Parallelization')
plt.legend()
plt.grid(True)
plt.show()

# Plotting FP OPS Comparison
plt.figure(figsize=(12, 6))
plt.plot(matrix_sizes, outer_fp_ops, label='Outer Loop FP OPS', marker='o', linestyle='-', color='blue')
plt.plot(matrix_sizes, inner_fp_ops, label='Inner Loop FP OPS', marker='s', linestyle='--', color='red')
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Floating Point Operations (FP OPS)')
plt.title('Floating Point Operations Comparison: Outer Loop vs Inner Loop Parallelization')
plt.legend()
plt.grid(True)
plt.show()

# Plotting TOT INS Comparison
plt.figure(figsize=(12, 6))
plt.plot(matrix_sizes, outer_tot_ins, label='Outer Loop TOT INS', marker='o', linestyle='-', color='blue')
plt.plot(matrix_sizes, inner_tot_ins, label='Inner Loop TOT INS', marker='s', linestyle='--', color='red')
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Total Instructions (TOT INS)')
plt.title('Total Instructions Comparison: Outer Loop vs Inner Loop Parallelization')
plt.legend()
plt.grid(True)
plt.show()