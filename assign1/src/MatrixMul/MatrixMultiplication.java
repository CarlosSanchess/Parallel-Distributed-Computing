import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Scanner;

public class MatrixMultiplication {

    public static void OnMult(int m_ar, int m_br) {
        Instant start, end;
        double temp;
        int i, j, k;

        double[][] pha = new double[m_ar][m_ar];
        double[][] phb = new double[m_br][m_br];
        double[][] phc = new double[m_ar][m_br];

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_ar; j++) {
                pha[i][j] = 1.0;
            }
        }

        for (i = 0; i < m_br; i++) {
            for (j = 0; j < m_br; j++) {
                phb[i][j] = (double) (i + 1);
            }
        }

        start = Instant.now();

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_br; j++) {
                temp = 0;
                for (k = 0; k < m_ar; k++) {
                    temp += pha[i][k] * phb[k][j];
                }
                phc[i][j] = temp;
            }
        }

        end = Instant.now();
        System.out.printf("Time: %.3f seconds\n", Duration.between(start, end).toMillis() / 1000.0);

        // Calculate GFLOPs
        double flops = 2.0 * m_ar * m_ar * m_ar;
        double timeInSeconds = Duration.between(start, end).toMillis() / 1000.0;
        double gflops = flops / (timeInSeconds * 1e9);

        System.out.printf("Performance: %.2f GFLOPS\n", gflops);

        System.out.println("Result matrix: ");
        for (i = 0; i < 1; i++) {
            for (j = 0; j < Math.min(10, m_br); j++) {
                System.out.print(phc[i][j] + " ");
            }
        }
        System.out.println();
    }

    public static void OnMultLine(int m_ar, int m_br) {
        Instant start, end;
        int i, j, k;

        double[][] pha = new double[m_ar][m_ar];
        double[][] phb = new double[m_br][m_br];
        double[][] phc = new double[m_ar][m_br];

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_ar; j++) {
                pha[i][j] = 1.0;
            }
        }

        for (i = 0; i < m_br; i++) {
            for (j = 0; j < m_br; j++) {
                phb[i][j] = (double) (i + 1);
            }
        }

        for (i = 0; i < m_ar; i++) {
            Arrays.fill(phc[i], 0.0);
        }

        start = Instant.now();

        for (i = 0; i < m_ar; i++) {
            for (k = 0; k < m_ar; k++) {
                for (j = 0; j < m_br; j++) {
                    phc[i][j] += pha[i][k] * phb[k][j];
                }
            }
        }

        end = Instant.now();
        System.out.printf("Time: %.3f seconds\n", Duration.between(start, end).toMillis() / 1000.0);

        System.out.println("Result matrix: ");
        for (i = 0; i < 1; i++) {
            for (j = 0; j < Math.min(10, m_br); j++) {
                System.out.print(phc[i][j] + " ");
            }
        }
        System.out.println();
    }

    public static void OnMultBlock(int m_ar, int m_br, int bkSize) {
        Instant start, end;
        int i, j, k, bi, bj, bk;

        double[][] pha = new double[m_ar][m_ar];
        double[][] phb = new double[m_br][m_br];
        double[][] phc = new double[m_ar][m_br];

        for (i = 0; i < m_ar; i++) {
            Arrays.fill(pha[i], 1.0);
        }

        for (i = 0; i < m_br; i++) {
            for (j = 0; j < m_br; j++) {
                phb[i][j] = (double) (i + 1);
            }
        }

        for (i = 0; i < m_ar; i++) {
            Arrays.fill(phc[i], 0.0);
        }

        start = Instant.now();

        for (bi = 0; bi < m_ar; bi += bkSize) {
            for (bj = 0; bj < m_br; bj += bkSize) {
                for (bk = 0; bk < m_ar; bk += bkSize) {
                    for (i = bi; i < Math.min(bi + bkSize, m_ar); i++) {
                        for (j = bj; j < Math.min(bj + bkSize, m_br); j++) {
                            for (k = bk; k < Math.min(bk + bkSize, m_ar); k++) {
                                phc[i][j] += pha[i][k] * phb[k][j];
                            }
                        }
                    }
                }
            }
        }

        end = Instant.now();
        System.out.printf("Time: %.3f seconds\n", Duration.between(start, end).toMillis() / 1000.0);

        System.out.println("Result matrix: ");
        for (i = 0; i < 1; i++) {
            for (j = 0; j < Math.min(10, m_br); j++) {
                System.out.print(phc[i][j] + " ");
            }
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int lin, col, blockSize;
        int op;
        int n_min, n_max, inc;

        do {
            System.out.println("\n1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("3. Block Multiplication");
            System.out.print("Selection?: ");

            while (!scanner.hasNextInt()) {
                System.out.println("Invalid input. Please enter a number (1, 2, or 3).");
                scanner.next(); 
                System.out.print("Selection?: ");
            }
            op = scanner.nextInt();

            if (op == 0) {
                break;
            }

            System.out.print("Enter minimum matrix dimension (e.g. 100 for a 100x100 matrix): ");
            while (!scanner.hasNextInt()) {
                System.out.println("Invalid input. Please enter an integer for dimensions.");
                scanner.next();
                System.out.print("Enter minimum matrix dimension (e.g. 100 for a 100x100 matrix): ");
            }
            n_min = scanner.nextInt();

            System.out.print("Enter maximum matrix dimension (e.g. 1000 for a 1000x1000 matrix): ");
            while (!scanner.hasNextInt()) {
                System.out.println("Invalid input. Please enter an integer for dimensions.");
                scanner.next();
                System.out.print("Enter maximum matrix dimension (e.g. 1000 for a 1000x1000 matrix): ");
            }
            n_max = scanner.nextInt();

            System.out.print("Increment?: ");
            while (!scanner.hasNextInt()) {
                System.out.println("Invalid input. Please enter an integer for Increment.");
                scanner.next(); 
                System.out.print("Increment?: ");
            }
            inc = scanner.nextInt();

            switch (op) {
                case 1:
                    for (int n = n_min; n <= n_max; n += inc) {
                        System.out.println("\nDimensions: " + n + "*" + n);
                        lin = n;
                        col = n;
                        OnMult(lin, col);
                    }
                    break;
                case 2:
                    for (int n = n_min; n <= n_max; n += inc) {
                        System.out.println("\nDimensions: " + n + "*" + n);
                        lin = n;
                        col = n;
                        OnMultLine(lin, col);
                    }
                    break;
                case 3:
                    System.out.print("Block Size?: ");
                    while (!scanner.hasNextInt()) {
                        System.out.println("Invalid input. Please enter an integer for block size.");
                        scanner.next(); 
                        System.out.print("Block Size?: ");
                    }
                    blockSize = scanner.nextInt();
                    for (int n = n_min; n <= n_max; n += inc) {
                        System.out.println("\nDimensions: " + n + "*" + n);
                        lin = n;
                        col = n;
                        OnMultBlock(lin, col, blockSize);
                    }
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        } while (op != 0);

        scanner.close();
    }
}