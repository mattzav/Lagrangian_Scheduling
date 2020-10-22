package LagrangianSolver;

import ilog.concert.*;
import ilog.cplex.*;
import ilog.cplex.IloCplex.UnknownObjectException;

// controllare simmetria nA ed nB
//1 - aplha - beta parte gia <0

//controllare esecuzione nA = 3, nB = 1
public class LagrangianSingle {

	public static double[] d;//
	public static double alpha, beta, v;
	public static double[][] gamma, delta, epsilon;
	public static int p[]; //
	public static double w[][];

	public static int n = 120, nA = 60, nB = 60;
	public static double init = 100;

	public static double Pa, Pb;
	public static double x_val[][];

	public static double subgrad_alpha = 0., subgrad_beta = 0;
	public static double[][] subgrad_gamma, subgrad_delta, subgrad_epsilon;
	public static int numIter = 200, countIter = 0, lastIter = 0;

	public static Double bestUB = Double.MAX_VALUE, bestLB = -Double.MAX_VALUE, currUB, currLB, maxBound;

	public static void main(String[] args) throws IloException {

		initParam();
		computeMaxBound();

		IloCplex cplex = new IloCplex();
		cplex.setOut(null);

		while (countIter < numIter && (Math.abs(bestUB - getExpected(nA, nB)) > Math.pow(10, -6))) {
//						System.out.println(countIter);
			countIter++;

			IloNumVar[][] x = new IloNumVar[n][];
			for (int i = 0; i < n; i++)
				x[i] = cplex.numVarArray(n, 0, Double.MAX_VALUE);

			createRelaxationModel(cplex, x, n);

//			System.out.println("START");
			if (cplex.solve()) {
				// printMultipliers();
				updateOptimalWandV(cplex, x);
				updateSubGrad();
				updateBounds(cplex);
				updateMultipliers();
				printParam();
			}
			cplex.clearModel();
			System.out.println("Iter " + countIter + ", currUb " + currUB + ", currLb " + currLB);
		}
	}

	private static double getExpected(int nA, int nB) {
		double expected = -1;
		if (nA % 2 == 0 || nB % 2 == 0)
			expected = 0;
		else
			expected = ((double) (nA + nB)) / (2 * nA * nB);
		return expected;
	}

	private static void createRelaxationModel(IloCplex cplex, IloNumVar[][] x, int n) throws IloException {

		IloLinearNumExpr fo = cplex.linearNumExpr();

		for (int t = 0; t < n; t++)
			for (int j = 0; j < n; j++) {
				fo.addTerm((gamma[j][t] - epsilon[j][t]) * d[t], x[j][t]);
				for (int l = 0; l < n; l++)
					for (int k = 0; k < t; k++) {
						fo.addTerm((gamma[j][t] - delta[j][t]) * p[l], x[l][k]);
					}
			}

		cplex.addMinimize(fo);

		// assignment
		for (int j = 0; j < n; j++) {
			cplex.addEq(cplex.sum(x[j]), 1);
		}

		for (int t = 0; t < n; t++) {
			IloLinearNumExpr t_th_constraint = cplex.linearNumExpr();
			for (int j = 0; j < n; j++)
				t_th_constraint.addTerm(1, x[j][t]);
			cplex.addEq(1, t_th_constraint);
		}
		// end assignment
	}

	private static void computeMaxBound() {

		double current = 0;

		double sumA = 0, sumB = 0;
		int countA = 0;
		int countB = 0;

		for (int i = 0; i < n; i++) {
			if (countB == nB) {
				current += p[countA];
				sumA += current;
				countA++;
			} else if (countA == nA) {
				current += p[nA + countB];
				sumB += current;
				countB++;
			} else if (i % 2 == 1) {
				current += p[countA];
				sumA += current;
				countA++;
			} else {
				current += p[nA + countB];
				sumB += current;
				countB++;
			}

		}

		maxBound = Math.max(sumA / nA - sumB / nB, sumB / nB - sumA / nA);
		System.out.println(maxBound);
	}

	private static void printMultipliers() {
		for (int i = 0; i < n; i++)
			for (int j = 0; j < n; j++) {
				System.out.println("gamma_{" + i + "," + j + "} = " + gamma[i][j]);
				System.out.println("delta{" + i + "," + j + "} = " + delta[i][j]);
				System.out.println("eps{" + i + "," + j + "} = " + epsilon[i][j]);
			}
	}

	private static void printParam() {
//		System.out.println("alpha = " + alpha);
//		System.out.println("beta = " + beta);
//		System.out.println("v = " + v);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (x_val[i][j] == 1.)
					System.out.println("x_{" + i + "," + j + "} = " + x_val[i][j]);
//				System.out.println("w_{" + i + "," + j + "} = " + w[i][j]);
//
//				System.out.println("subgamma_{" + i + "," + j + "} = " + subgrad_gamma[i][j]);
//				System.out.println("subdelta{" + i + "," + j + "} = " + subgrad_delta[i][j]);
//				System.out.println("subeps{" + i + "," + j + "} = " + subgrad_epsilon[i][j]);
//
//				System.out.println("gamma_{" + i + "," + j + "} = " + gamma[i][j] + " ");
//				System.out.print("delta{" + i + "," + j + "} = " + delta[i][j] + " ");
//				System.out.println("eps{" + i + "," + j + "} = " + epsilon[i][j] + " ");
			}
//			System.out.println();
		}

//		System.out.println("grad norm " + computeGradientLength());
//		System.out.println("LB" + bestLb);
//		System.out.println("UB" + bestUb);
	}

	private static void updateBounds(IloCplex cplex) throws IloException {

		double lb = cplex.getObjValue();
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++) {
				lb += (w[j][t] * (alpha / nA - beta / nA - gamma[j][t] + delta[j][t] + epsilon[j][t]));
				lb -= (d[t] * gamma[j][t]);
			}
			for (int j = nA; j < n; j++) {
				lb += (w[j][t] * (beta / nB - alpha / nB - gamma[j][t] + delta[j][t] + epsilon[j][t]));
				lb -= (d[t] * gamma[j][t]);
			}
		}

		lb += ((Pa / nA) * (alpha - beta));

		lb -= ((Pb / nB) * (alpha - beta));

//		System.out.println(lb);
		currLB = lb;
		if (lb > bestLB) {
			bestLB = lb;
		}

		// implementare incrementando la somma corrente
		double completionA = 0, completionB = 0;
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++) {
				if (x_val[j][t] == 1.) {// controllare che non ci siano problemi di approssimazione
					completionA += p[j];
					for (int l = 0; l < n; l++) {
						for (int k = 0; k < t; k++) {
							completionA += p[l] * x_val[l][k];
						}
					}
				} else if (x_val[j][t] > 0)
					throw new RuntimeException("errore");
			}

			for (int j = nA; j < n; j++) {
				if (x_val[j][t] == 1.) {// controllare che non ci siano problemi di approssimazione
					completionB += p[j];
					for (int l = 0; l < n; l++) {
						for (int k = 0; k < t; k++) {
							completionB += p[l] * x_val[l][k];
						}
					}
				}
			}
		}
		double ub = Math.max(completionA / nA - completionB / nB, completionB / nB - completionA / nA);
//		System.out.println(ub);
//		System.out.println();
		currUB = ub;
		if (ub < bestUB) {
			// countIter = 0;
			lastIter = countIter;
			bestUB = ub;
		}
	}

	private static void updateMultipliers() {
		double gradientLength = computeGradientLength();
		// System.out.println("Norma = " + Math.sqrt(gradientLength));
		double factor = (currUB - currLB) / gradientLength;
//		System.out.println("Passo = " + factor);
//		System.out.println("---------");
//		System.out.println();

		alpha = Math.max(0, alpha + subgrad_alpha * factor);
		beta = Math.max(0, beta + subgrad_beta * factor);

//		if (alpha + beta > 1) {
//			double sum = alpha + beta;
//			alpha /= sum;
//			beta /= sum;
//		}

		for (int j = 0; j < n; j++)
			for (int t = 0; t < n; t++) {
				gamma[j][t] = Math.max(0, gamma[j][t] + subgrad_gamma[j][t] * factor);
				delta[j][t] = Math.max(0, delta[j][t] + subgrad_delta[j][t] * factor);
				epsilon[j][t] = Math.max(0, epsilon[j][t] + subgrad_epsilon[j][t] * factor);
			}

	}

	/*
	 * invece di ricalcolarla si pu� calcolare in modo iterativo: se ho x^2 e
	 * diventa (x+y)^2 posso calcolarla come x^2 +y^2+2xy. Quindi aggiornarla quando
	 * aggiornarla quando aggiorno il subgrad
	 */
	public static double computeGradientLength() {
		double toReturn = 0;
		toReturn += Math.pow(subgrad_alpha, 2);
		toReturn += Math.pow(subgrad_beta, 2);
		for (int j = 0; j < n; j++)
			for (int t = 0; t < n; t++) {
				toReturn += Math.pow(subgrad_gamma[j][t], 2);
				toReturn += Math.pow(subgrad_delta[j][t], 2);
				toReturn += Math.pow(subgrad_epsilon[j][t], 2);

			}

		return toReturn;
	}

	private static void updateSubGrad() {

		subgrad_alpha = 0;
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++)
				subgrad_alpha += w[j][t] / nA;
			for (int j = nA; j < n; j++)
				subgrad_alpha -= w[j][t] / nB;

		}
		subgrad_alpha += Pa / nA;
		subgrad_alpha -= Pb / nB;
		subgrad_alpha -= v;

		subgrad_beta = 0;
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++)
				subgrad_beta -= w[j][t] / nA;
			for (int j = nA; j < n; j++)
				subgrad_beta += w[j][t] / nB;

		}
		subgrad_beta -= Pa / nA;
		subgrad_beta += Pb / nB;
		subgrad_beta -= v;

		for (int t = 0; t < n; t++)
			for (int j = 0; j < n; j++) {
				subgrad_gamma[j][t] = d[t] * x_val[j][t];
				subgrad_gamma[j][t] -= (w[j][t] + d[t]);

				subgrad_delta[j][t] = w[j][t];

				subgrad_epsilon[j][t] = w[j][t] - d[t] * x_val[j][t];

				for (int l = 0; l < n; l++)
					for (int k = 0; k < t; k++) {
						subgrad_gamma[j][t] += p[l] * x_val[l][k];

						subgrad_delta[j][t] -= p[l] * x_val[l][k];
					}
			}

	}

	private static void updateOptimalWandV(IloCplex cplex, IloNumVar[][] x)
			throws UnknownObjectException, IloException {
		for (int i = 0; i < n; i++)
			x_val[i] = cplex.getValues(x[i]);

		if (1 - alpha - beta >= 0)
			v = 0;
		else
			v = maxBound;

//		System.out.println("V" + v);

		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++)
				if (alpha / nA - beta / nA - gamma[j][t] + delta[j][t] + epsilon[j][t] >= 0)
					w[j][t] = 0.;
				else
					w[j][t] = d[t];
			for (int j = nA; j < n; j++)
				if (beta / nB - alpha / nB - gamma[j][t] + delta[j][t] + epsilon[j][t] >= 0)
					w[j][t] = 0.;
				else
					w[j][t] = d[t];
		}

	}

	private static void initParam() {

		p = new int[n];
		d = new double[n];
		gamma = new double[n][n];
		delta = new double[n][n];
		epsilon = new double[n][n];
		w = new double[n][n];
		x_val = new double[n][n];
		subgrad_gamma = new double[n][n];
		subgrad_delta = new double[n][n];
		subgrad_epsilon = new double[n][n];

		for (int i = 0; i < nA; i++)
			p[i] = 1;
		for (int i = nA; i < n; i++)
			p[i] = 1;

		Pa = 0;
		Pb = 0;
		for (int i = 0; i < nA; i++)
			Pa += p[i];

		for (int i = nA; i < n; i++)
			Pb += p[i];

		alpha = init;
		beta = init;

		for (int i = 0; i < n; i++) {
			d[i] = i * (Pa + Pb);
			for (int j = 0; j < n; j++) {
				gamma[i][j] = init;
				delta[i][j] = init;
				epsilon[i][j] = init;
			}
		}

	}
}
