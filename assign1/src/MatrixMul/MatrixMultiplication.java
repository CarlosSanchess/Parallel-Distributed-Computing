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

        System.out.println("Result matrix: ");
        for (i = 0; i < 1; i++) {
            for (j = 0; j < Math.min(10, m_br); j++) {
                System.out.print(phc[i][j] + " ");
            }
        }
        System.out.println();
    }

    public static void OnMultLine(int m_ar, int m_br) {
        // to complete
    }

    public static void OnMultBlock(int m_ar, int m_br, int bkSize) {
        // to complete
    }

    public static void main(String[] args) {
        // to complete
    }
}