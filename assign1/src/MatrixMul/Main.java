package g11.assign1.src.MatrixMul;
class Main {
  
  
    static int[][] arr = { { 1, 2 }, { 3, 4 } };    
    public static void main(String args[]) {
        
        int [][] arr1 = setMatr(3000,3000);
        int[] s1 = {3000, 3000};
        int [][] arr2 = setMatr(3000, 3000);
        int[] s2 = {3000, 3000};
        printMatrix(multiplyMatrix(arr1, arr2, s1, s2));

    }

    private static int [][] setMatr(int noRows, int noCol){
        int [][] matr =  new int[noRows][noCol];
        for (int i = 0; i < noRows; i++) {  // Loop over rows of m1
            for (int j = 0; j < noCol; j++) {  // Loop over columns of m2
               matr[i][j] = i * 1 + j;
            }
        }
        return matr;
    }
     // = 58
    private static int multiplyLines(int[] l1, int[] l2, int sizeL1, int sizeL2){

        if(sizeL1 != sizeL2){
            return -1;
        }
        int result = 0;
        
        for(int i = 0; i < sizeL1; i++){
            result += l1[i] * l2[i];
        }
        
        return result;
    }
    private static void printMatrix(int[][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }
    public static int[][] multiplyMatrix(int[][] m1, int[][] m2, int[] s1, int[]s2){
        if(s1[1] != s2[0]){
            return null;
        }
        int[][] result = new int[s1[0]][s2[1]];

        for (int i = 0; i < s1[0]; i++) {  // Loop over rows of m1
            for (int j = 0; j < s2[1]; j++) {  // Loop over columns of m2
                int[] row = m1[i];
                int[] col = new int[s2[0]];
                for (int k = 0; k < s2[0]; k++) {
                    col[k] = m2[k][j];
                }
                result[i][j] = multiplyLines(row, col, s1[1], s2[0]);
            }
        }
        return result;
    }

}
