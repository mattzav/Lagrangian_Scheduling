
import ilog.concert.*;
import ilog.cplex.*;
import ilog.cplex.IloCplex.UnknownObjectException;

public class Main {

	public static int n = 8, nA = 4, nB = 4; //
	public static double[] d;//
	public static double alpha, beta;
	public static double[][] gamma, delta, epsilon;
	public static int p[]; //
	public static double w[][];

	public static int Pa, Pb;
	public static double x_val[][];

	public static double subgrad_alpha = 0., subgrad_beta = 0;
	public static double[][] subgrad_gamma, subgrad_delta, subgrad_epsilon;

	public static double bestUb = Double.MAX_VALUE, bestLb = Double.MIN_VALUE;

	public static int numIter = 10;

	public static void main(String[] args) throws IloException {

		initParam();

		for (int iter = 0; iter < numIter; iter++) {

			IloCplex cplex = new IloCplex();
			cplex.setOut(null);
			IloNumVar[][] x = new IloNumVar[n][];
			for (int i = 0; i < n; i++)
				x[i] = cplex.numVarArray(n, 0, Double.MAX_VALUE);

			IloLinearNumExpr fo = cplex.linearNumExpr();

			for (int t = 0; t < n; t++)
				for (int j = 0; j < n; j++) {
					fo.addTerm(gamma[j][t] * d[t], x[j][t]);
					fo.addTerm(-epsilon[j][t] * d[t], x[j][t]);
					for (int l = 0; l < n; l++)
						for (int k = 0; k < t - 1; k++) {
							fo.addTerm(gamma[j][t] * p[l], x[l][k]);
							fo.addTerm(-delta[j][t] * p[l], x[l][k]);
						}
				}
			cplex.addMinimize(fo);

			// assignment
			for (int j = 0; j < n; j++) {
				cplex.addEq(cplex.sum(x[j]), 1);
			}

			for (int t = 0; t < n; t++) {
				IloLinearNumExpr t_th_constraints = cplex.linearNumExpr();
				for (int j = 0; j < n; j++)
					t_th_constraints.addTerm(1, x[j][t]);
				cplex.addEq(1, t_th_constraints);
			}
			// end assignment

			System.out.println("START");
			if (cplex.solve()) {

				System.out.println("END");
				updateOptimalW(cplex, x);
				System.out.println("1");
				updateSubGrad();
				System.out.println("2");
				updateBounds(cplex);
				System.out.println("3");
				updateMultipliers();
				System.out.println("4");

				for (int i = 0; i < n; i++)
					for (int j = 0; j < n; j++)
						System.out.println(x_val[i][j]);
				System.out.println("LB"+bestLb);
				System.out.println("UB"+bestUb);
			}
		}
//            System.out.println("Solution status = " + cplex.getStatus());
//            System.out.println("Solution value  = " + cplex.getObjValue());
//
//            for (int j = 0; j < x.length; ++j) {
//               System.out.println("Variable " + j + ": Value = " + x[j]);
//            }
//
//            for (int i = 0; i < slack.length; ++i) {
//               System.out.println("Constraint " + i + ": Slack = " + slack[i]);
//            }
//         }
// 
//         cplex.exportModel("mipex1.lp");

	}

	private static void updateBounds(IloCplex cplex) throws IloException {
		double lb = cplex.getObjValue();
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++) {
				lb += (w[j][t] * (alpha / nA - beta / nA - gamma[j][t] + delta[j][t] + epsilon[j][t]));
				lb -= d[t] * gamma[j][t];
			}
			for (int j = nA; j < n; j++) {
				lb += (w[j][t] * (beta / nB - alpha / nB - gamma[j][t] + delta[j][t] + epsilon[j][t]));
				lb -= d[t] * gamma[j][t];
			}
		}

		lb += (Pa / nA) * (alpha - beta);
		lb -= (Pb / nB) * (beta - alpha);

		if (lb > bestLb)
			bestLb = lb;

		double completionA = 0, completionB = 0;
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++) {
				if (x_val[j][t] == 1.) {// controllare che non ci siano problemi di approssimazione
					completionA += p[j];
					for (int l = 0; l < n; l++) {
						for (int k = 0; k < t - 1; k++) {
							completionA += p[l] * x_val[l][k];
						}
					}
				}
			}

			for (int j = nA; j < n; j++) {
				if (x_val[j][t] == 1.) {// controllare che non ci siano problemi di approssimazione
					completionB += p[j];
					for (int l = 0; l < n; l++) {
						for (int k = 0; k < t - 1; k++) {
							completionB += p[l] * x_val[l][k];
						}
					}
				}
			}
		}

		double ub = Math.max(completionA / nA - completionB / nB, completionB / nB - completionA / nA);
		if (ub < bestUb)
			bestUb = ub;
	}

	private static void updateMultipliers() {
		double gradientLength = computeGradientLength();
		double factor = (bestUb - bestLb) / computeGradientLength();
		alpha += subgrad_alpha * factor;
		beta += subgrad_beta * factor;
		for (int j = 0; j < n; j++)
			for (int t = 0; t < n; t++) {
				gamma[j][t] += subgrad_gamma[j][t] * factor;
				delta[j][t] += subgrad_delta[j][t] * factor;
				epsilon[j][t] += subgrad_epsilon[j][t] * factor;
			}
	}

	/*
	 * invece di ricalcolarla si pu� calcolare in modo iterativo: se ho x^2 e
	 * diventa (x+y)^2 posso calcolarla come x^2 +y^2+2xy. Quindi aggiornarla quando
	 * aggiornarla quando aggiorno il subgrad
	 */
	private static double computeGradientLength() {
		double toReturn = 0;
		toReturn += Math.pow(subgrad_alpha, 2);
		toReturn += Math.pow(subgrad_beta, 2);
		for (int j = 0; j < n; j++)
			for (int t = 0; t < n; t++) {
				toReturn += Math.pow(gamma[j][t], 2);
				toReturn += Math.pow(delta[j][t], 2);
				toReturn += Math.pow(epsilon[j][t], 2);

			}

		return Math.sqrt(toReturn);
	}

	private static void updateSubGrad() {
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++)
				subgrad_alpha += w[j][t] / nA;
			for (int j = nA; j < n; j++)
				subgrad_alpha -= w[j][t] / nB;

		}
		subgrad_alpha += Pa / nA;
		subgrad_alpha -= Pb / nB;

		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++)
				subgrad_beta -= w[j][t] / nA;
			for (int j = nA; j < n; j++)
				subgrad_beta += w[j][t] / nB;

		}
		subgrad_beta -= Pa / nA;
		subgrad_beta += Pb / nB;

		for (int t = 0; t < n; t++)
			for (int j = 0; j < n; j++) {
				subgrad_gamma[j][t] = d[t] * x_val[j][t];
				subgrad_gamma[j][t] -= (w[j][t] + d[t]);

				subgrad_delta[j][t] = w[j][t];

				subgrad_epsilon[j][t] = w[j][t] - d[t] * x_val[j][t];

				for (int l = 0; l < n; l++)
					for (int k = 0; k < t - 1; k++) {
						subgrad_gamma[j][t] += p[l] * x_val[l][k];

						subgrad_delta[j][t] -= p[l] * x_val[l][k];
					}
			}

	}

	private static void updateOptimalW(IloCplex cplex, IloNumVar[][] x) throws UnknownObjectException, IloException {
		for (int i = 0; i < n; i++)
			x_val[i] = cplex.getValues(x[i]);

		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++)
				if (alpha / nA + beta / nB - gamma[j][t] + delta[j][t] + epsilon[j][t] > 0)
					w[j][t] = 0.;
				else
					w[j][t] = d[t];
			for (int j = nA; j < n; j++)
				if (beta / nB - alpha / nA - gamma[j][t] + delta[j][t] + epsilon[j][t] > 0)
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
			p[i] = 2;
		for (int i = nA; i < n; i++)
			p[i] = 2;

		Pa = 0;
		Pb = 0;
		for (int i = 0; i < nA; i++)
			Pa += p[i];

		for (int i = nA; i < nA + nB; i++)
			Pb += p[i];

		alpha = 0.;
		beta = 0.;

		d[0] = 0.;// siamo sicuri? d[t] = (t-1)*(Pa+Pb)

		for (int i = 0; i < n; i++) {
			if (i >= 1)
				d[i] = (i - 1) * (Pa + Pb);
			for (int j = 0; j < n; j++) {
				gamma[i][j] = 0.;
				delta[i][j] = 0.;
				epsilon[i][j] = 0.;
			}
		}

	}
}