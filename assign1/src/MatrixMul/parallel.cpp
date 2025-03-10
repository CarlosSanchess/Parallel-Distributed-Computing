#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <papi.h>
#include <chrono>

using namespace std;

#define SYSTEMTIME clock_t

// Parallel matrix multiplication (outer loop parallelized)
void OnMultLineParallelOuter(int m_ar, int m_br)
{
    SYSTEMTIME Time1, Time2;
    char st[100];
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i * m_ar + j] = (double)1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i * m_br + j] = (double)(i + 1);

    std::fill_n(phc, m_ar * m_ar, 0.0);

    Time1 = clock();

    #pragma omp parallel for
    for(i = 0; i < m_ar; i++) {
        for(k = 0; k < m_ar; k++) {
            for(j = 0; j < m_br; j++) {
                phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
            }
        }
    }

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++) {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

// Parallel matrix multiplication (inner loop parallelized)
void OnMultLineParallelInner(int m_ar, int m_br)
{
    SYSTEMTIME Time1, Time2;
    char st[100];
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i * m_ar + j] = (double)1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i * m_br + j] = (double)(i + 1);

    std::fill_n(phc, m_ar * m_ar, 0.0);

    Time1 = clock();

    #pragma omp parallel
    {
        for(i = 0; i < m_ar; i++) {
            for(k = 0; k < m_ar; k++) {
                #pragma omp for
                for(j = 0; j < m_br; j++) {
                    phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
                }
            }
        }
    }

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++) {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

int main() {
    char c;
	int lin, col, blockSize;
	int op;
	int n_min, n_max, inc;
	
	int EventSet = PAPI_NULL;
  	long long values[5];
  	int ret;
	
	ret = PAPI_library_init( PAPI_VER_CURRENT );
	if ( ret != PAPI_VER_CURRENT )
		std::cout << "FAIL" << endl;


	ret = PAPI_create_eventset(&EventSet);
	if (ret != PAPI_OK) cout << "ERROR: create eventset" << endl;

	ret = PAPI_add_event(EventSet,PAPI_L1_DCM );
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L1_DCM" << endl;

	ret = PAPI_add_event(EventSet,PAPI_L2_DCM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L2_DCM" << endl;
	
	ret = PAPI_add_event(EventSet,PAPI_L3_LDM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L3_LDM" << endl;
	
	ret = PAPI_add_event(EventSet,PAPI_DP_OPS);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_DP_OPS" << endl;
	
	ret = PAPI_add_event(EventSet,PAPI_TOT_INS);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_TOT_INS" << endl;

	op=1;
	do {
		cout << endl << "1. 1st parallel solution" << endl;
		cout << "2. 2nd parallel solution" << endl;
		cout << "0. Exit" << endl;
		cout << "Selection?: ";
		cin >> op;
		if (op == 0)
			break;

		cout << endl << "Minimum dimension: lins=cols?: ";
		cin >> n_min;
		cout << endl << "Maximum dimension: lins=cols?: ";
		cin >> n_max;
		cout << endl << "Step?: ";
		cin >> inc;
		cout << endl;

		switch (op){
			case 1: 
				for (int n = n_min; n <= n_max; n+=inc) {
					ret = PAPI_start(EventSet);
					if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
					
					cout << endl << "Dimensions: " << n << '*' << n << endl;
					lin = n;
					col = n;
					OnMultLineParallelOuter(lin, col);
					
					ret = PAPI_stop(EventSet, values);
			  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
			  		printf("L1 DCM: %lld \n",values[0]);
			  		printf("L2 DCM: %lld \n",values[1]);
			  		printf("L3 LDM: %lld \n",values[2]);
			  		printf("DP OPS: %lld \n",values[3]);
			  		printf("TOT INS: %lld \n", values[4]);

					ret = PAPI_reset( EventSet );
					if ( ret != PAPI_OK )
						std::cout << "FAIL reset" << endl; 
				}
				break;
			case 2:
				for (int n = n_min; n <= n_max; n+=inc) {
					ret = PAPI_start(EventSet);
					if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
					
					cout << endl << "Dimensions: " << n << '*' << n << endl;
					lin = n;
					col = n;
					OnMultLineParallelInner(lin,col);
										
					ret = PAPI_stop(EventSet, values);
			  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
			  		printf("L1 DCM: %lld \n",values[0]);
			  		printf("L2 DCM: %lld \n",values[1]);
			  		printf("L3 LDM: %lld \n",values[2]);
			  		printf("DP OPS: %lld \n",values[3]);
			  		printf("TOT INS: %lld \n", values[4]);

					ret = PAPI_reset( EventSet );
					if ( ret != PAPI_OK )
						std::cout << "FAIL reset" << endl; 
				}
				break;
		}
	} while (op != 0);

	ret = PAPI_remove_event( EventSet, PAPI_L1_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_L2_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl;
	
	ret = PAPI_remove_event( EventSet, PAPI_L3_LDM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl;
	
	ret = PAPI_remove_event( EventSet, PAPI_DP_OPS );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl;
	
	ret = PAPI_remove_event( EventSet, PAPI_TOT_INS );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl;

	ret = PAPI_destroy_eventset( &EventSet );
	if ( ret != PAPI_OK )
		std::cout << "FAIL destroy" << endl;
}