CXX = g++
CXXFLAGS = -O2
LDFLAGS = -lpapi

TARGET = matrixproduct
SOURCE = matrixproduct.cpp

all: $(TARGET)

$(TARGET): $(SOURCE)
	$(CXX) $(CXXFLAGS) $(SOURCE) -o $(TARGET) $(LDFLAGS)

clean:
	rm -f $(TARGET)

.PHONY: all clean
