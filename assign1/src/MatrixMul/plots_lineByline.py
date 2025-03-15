import matplotlib.pyplot as plt

# Matrix sizes
matrix_sizes = [600, 1000, 1400, 1800, 2200, 2600, 3000]

# Execution times (in seconds) for line-by-line multiplication
cpp_times = [0.109, 0.474, 1.487, 3.175, 5.891, 9.736, 14.951]
java_times = [0.091, 0.399, 1.342, 2.947, 5.505, 10.947, 16.040]

# GFLOPS values for line-by-line multiplication
cpp_gflops = [3.96, 4.22, 3.69, 3.67, 3.61, 3.61, 3.61]
java_gflops = [4.75, 5.01, 4.09, 3.96, 3.87, 3.21, 3.37]

# Data for C++ L1 and L2 DCM
cpp_l1_dcm = [27610902, 128270508, 356961253, 772784793, 2014640122, 4294621566, 6698316610]
cpp_l2_dcm = [197852, 958787, 4028340, 9800958, 4182780, 6984457, 10414106]

# Plotting Execution Time Comparison
plt.figure(figsize=(10, 6))
plt.plot(matrix_sizes, cpp_times, label='C++', marker='o')
plt.plot(matrix_sizes, java_times, label='Java', marker='s')

# Labels and title
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Execution Time (seconds)')
plt.title('Line-by-Line Execution Time Comparison: C++ vs Java')
plt.legend()
plt.grid()

# Show plot
plt.show()

# Plotting GFLOPS Comparison
plt.figure(figsize=(10, 6))
plt.plot(matrix_sizes, cpp_gflops, label='C++', marker='o')
plt.plot(matrix_sizes, java_gflops, label='Java', marker='s')

# Labels and title
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('GFLOPS')
plt.title('Line-by-Line GFLOPS Comparison: C++ vs Java')
plt.legend()
plt.grid()

# Show plot
plt.show()

# Plotting C++ L1 and L2 DCM Comparison
plt.figure(figsize=(12, 6))

# Plotting C++ L1 DCM in blue
plt.plot(matrix_sizes, cpp_l1_dcm, label='C++ L1 DCM', marker='o', linestyle='-', color='blue')

# Plotting C++ L2 DCM in red
plt.plot(matrix_sizes, cpp_l2_dcm, label='C++ L2 DCM', marker='s', linestyle='--', color='red')

# Labels and title
plt.xlabel('Matrix Size (NxN)')
plt.ylabel('Data Cache Misses (DCM)')
plt.title('C++ Line-by-Line L1 and L2 Data Cache Misses Comparison')
plt.legend()
plt.grid(True)

# Show plot
plt.show()