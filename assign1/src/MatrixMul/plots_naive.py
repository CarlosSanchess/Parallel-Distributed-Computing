import matplotlib.pyplot as plt

# Matrix sizes
matrix_sizes = [600, 1000, 1400, 1800, 2200, 2600, 3000]

# Execution times (in seconds)
cpp_times = [0.154, 0.702, 2.846, 6.711, 16.870, 41.912, 67.189]
java_times = [0.254, 1.581, 8.502, 23.912, 51.042, 104.170, 175.885]

# Plotting
plt.figure(figsize=(10, 6))
plt.plot(matrix_sizes, cpp_times, label='C++', marker='o')
plt.plot(matrix_sizes, java_times, label='Java', marker='s')

# Labels and title
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Execution Time (seconds)')
plt.title('Execution Time Comparison: C++ vs Java')
plt.legend()
plt.grid()

# Show plot
plt.show()

# GFLOPS values
cpp_gflops = [2.80, 2.85, 1.93, 1.74, 1.26, 0.84, 0.80]
java_gflops = [1.70, 1.27, 0.65, 0.49, 0.42, 0.34, 0.31]

# Plotting
plt.figure(figsize=(10, 6))
plt.plot(matrix_sizes, cpp_gflops, label='C++', marker='o')
plt.plot(matrix_sizes, java_gflops, label='Java', marker='s')

# Labels and title
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('GFLOPS')
plt.title('GFLOPS Comparison: C++ vs Java')
plt.legend()
plt.grid()

# Show plot
plt.show()

# Data for C++ L1 and L2 DCM
cpp_l1_dcm = [241678983, 1136969844, 3125017136, 7059740261, 15939144633, 30910168160, 50421145206]
cpp_l2_dcm = [448582, 3488117, 58169452, 94416650, 558295715, 2305966036, 6043903995]

# Plotting L1 and L2 DCM Comparison for C++
plt.figure(figsize=(12, 6))

# Plotting C++ L1 DCM in blue
plt.plot(matrix_sizes, cpp_l1_dcm, label='C++ L1 DCM', marker='o', linestyle='-', color='blue')

# Plotting C++ L2 DCM in red
plt.plot(matrix_sizes, cpp_l2_dcm, label='C++ L2 DCM', marker='s', linestyle='--', color='red')

# Labels and title
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Data Cache Misses (DCM)')
plt.title('C++ L1 and L2 Data Cache Misses Comparison')
plt.legend()
plt.grid(True)

# Show plot
plt.show()