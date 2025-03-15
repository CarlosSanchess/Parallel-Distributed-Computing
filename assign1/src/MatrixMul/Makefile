CXX = g++
CXXFLAGS = -O2
LDFLAGS = -lpapi

# Targets
TARGETS = matrixproduct parallel

# Default rule to build all targets
all: $(TARGETS)

# Rule to build matrixproduct (no OpenMP)
matrixproduct: matrixproduct.cpp
	$(CXX) $(CXXFLAGS) matrixproduct.cpp -o matrixproduct $(LDFLAGS)

# Rule to build parallel (with OpenMP)
parallel: parallel.cpp
	$(CXX) $(CXXFLAGS) parallel.cpp -o parallel $(LDFLAGS) -fopenmp

# Clean up
clean:
	rm -f $(TARGETS)

.PHONY: all clean