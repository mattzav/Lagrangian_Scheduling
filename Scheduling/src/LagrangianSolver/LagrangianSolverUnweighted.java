package LagrangianSolver;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import ilog.concert.*;
import ilog.cplex.*;
import ilog.cplex.IloCplex.UnknownObjectException;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

//confronti con cplex per dimostrare che anche 5/600 secondi sono comunque pochi

public class LagrangianSolverUnweighted {

	private static final String EXCEL_FILE_LOCATION = "C:\\Users\\Matte\\Dropbox\\Scheduling\\Job diversi\\Articolo\\Risultati Numerici\\lambda = ";
	static WritableWorkbook workBook = null;
	static WritableSheet excelSheet;

	public static double[] d;//
	public static double alpha, beta, v;
	public static double[][] gamma, delta, epsilon;
	public static int p[]; //
	public static double w[][];

	public static double Pa, Pb;
	public static double x_val[][];

	public static double subgradientLength;
	public static double subgrad_alpha = 0., subgrad_beta = 0;
	public static double[][] subgrad_gamma, subgrad_delta, subgrad_epsilon;
	public static int n, nA, nB;
	public static int countIter, lastIter;

	public static long start, timeToBest;
	public static double timeLimit;
	public static int seed = 1;

	public static Random r;

	public static double bestUB = Double.MAX_VALUE, bestLB = -Double.MAX_VALUE, currUB, currLB, maxBound;

	public static void main(String[] args) throws IloException {

		r = new Random();

		// create CPLEX environment
		IloCplex cplex = new IloCplex();
		cplex.setOut(null);

		for (int pow = 1; pow <= 4; pow++) {
			for (nA = 10; nA <= 20; nA += 10) {
				for (nB = nA; nB <= nA + 30; nB += 10) {
					n = nA + nB;

					// create Excel file
					createExcelFile(pow, nA, nB);
					int excelRow = 1;

					// create binary x variables
					IloNumVar[][] x = new IloNumVar[n][];
					for (int i = 0; i < n; i++)
						x[i] = cplex.numVarArray(n, 0, Double.MAX_VALUE);

					for (int scenario = 0; scenario < 50; scenario++) {

						initParam(Math.pow(10, pow)); // init parameters
						double timeSol[] = new double[100];

						start = System.currentTimeMillis();
						int interval = 1;

						computeMaxBound(); // compute an upper bound on the value of V

						while (Math.abs(bestUB) > 0 && (System.currentTimeMillis() - start) / 1000 < timeLimit) {

							countIter++;

							createRelaxationModel(cplex, x, n); // create the relaxation model

							if (cplex.solve()) {
								// printMultipliers();
								computeOptimalWandV(cplex, x); // compute the optimal value of W and V w.r.t. x

								computeSubGrad(); // compute the value of subgradient

								updateBounds(cplex); // update current/best lower/upper bound

								if ((System.currentTimeMillis() - start) / 1000 >= 18 * interval) {
									if (interval < 101) {
										timeSol[interval - 1] = bestUB;
										interval++;
									} else {
										if (timeSol[99] > bestUB) {
											timeSol[99] = bestUB;
										}
									}
								}

								updateMultipliers(); // update multipliers

								// printParam();
							}

							cplex.clearModel();
						}

						long timeToExit = System.currentTimeMillis() - start;

						// add to file Excel the results
						addValueToExcelFile(excelRow, nA, nB, Math.pow(10, pow), lastIter, bestUB, timeToExit, timeSol);
						excelRow++;

						System.out.println("n = " + n + ", nA = " + nA + ", nB = " + nB + ", lambda_i = "
								+ Math.pow(10, pow) + ", N.iter = " + lastIter + " , Obj = " + bestUB + " ,"
								+ (double) timeToBest / 1000 + ", " + (double) timeToExit / 1000 + ", " + (seed - 1));
					}

					// close excel file
					closeExcelFile();
				}
			}
			seed = 1;
		}

	}

	private static void closeExcelFile() {
		if (workBook != null) {
			try {
				workBook.write();
				workBook.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (WriteException e) {
				e.printStackTrace();
			}
		}
	}

	private static void addValueToExcelFile(int excelRow, int nA, int nB, double lambda, int lastIter, double bestUB,
			long timeToExit, double[] timeSol) {
		try {

			Number number = new Number(0, excelRow, nA);
			excelSheet.addCell(number);

			number = new Number(1, excelRow, nB);
			excelSheet.addCell(number);

			number = new Number(2, excelRow, lambda);
			excelSheet.addCell(number);

			number = new Number(3, excelRow, lastIter);
			excelSheet.addCell(number);

			number = new Number(4, excelRow, bestUB);
			excelSheet.addCell(number);

			String optimum = "YES";
			if (Math.abs(bestUB) > Math.pow(10, -6))
				optimum = "-";

			Label label = new Label(5, excelRow, optimum);
			excelSheet.addCell(label);

			number = new Number(6, excelRow, (double) timeToBest / 1000);
			excelSheet.addCell(number);

			number = new Number(7, excelRow, (double) timeToExit / 1000);
			excelSheet.addCell(number);

			number = new Number(8, excelRow, seed - 1);
			excelSheet.addCell(number);

			for (int i = 15; i < 15 + n; i++)
				excelSheet.addCell(new Number(i, excelRow, p[i - 15]));

			for (int i = 0; i < 100; i++)
				excelSheet.addCell(new Number(i, 61 + excelRow, timeSol[i]));

		} catch (Exception e) {
			throw new RuntimeException("Error adding excel value");
		}
	}

	private static void createExcelFile(int pow, int nA, int nB) {
		try {
			String path = EXCEL_FILE_LOCATION + String.valueOf((int) Math.pow(10, pow)) + "\\" + nA + "_" + nB + ".xls";
			System.out.println("\n \n");
			System.out.println("-----------------------------------");
			System.out.println("PATH = " + path);
			System.out.println();

			workBook = Workbook.createWorkbook(new File(path));

			// create an Excel sheet
			excelSheet = workBook.createSheet("Lagrangian Results", 0);

			// add header into the Excel sheet
			Label label = new Label(0, 0, "nA");
			excelSheet.addCell(label);

			label = new Label(1, 0, "nB");
			excelSheet.addCell(label);

			label = new Label(2, 0, "Lambda_0");
			excelSheet.addCell(label);

			label = new Label(3, 0, "N. Iter");
			excelSheet.addCell(label);

			label = new Label(4, 0, "Best UB");
			excelSheet.addCell(label);

			label = new Label(5, 0, "Optimum");
			excelSheet.addCell(label);

			label = new Label(6, 0, "Time To Best");
			excelSheet.addCell(label);

			label = new Label(7, 0, "Time To Exit");
			excelSheet.addCell(label);

			label = new Label(8, 0, "Seed");
			excelSheet.addCell(label);

			label = new Label(0, 60, "Solution in time");
			excelSheet.addCell(label);

		} catch (Exception e) {
			throw new RuntimeException("error creating excel file");
		}

	}

	// I VINCOLI DI ASSEGNAMENTO INSERIRLI NEL PRIMO DOPPIO FOR- IL PRIMO DOPPIO FOR
	// SI PUO' ELIMINARE
	private static void createRelaxationModel(IloCplex cplex, IloNumVar[][] x, int n) throws IloException {

		IloLinearNumExpr fo = cplex.linearNumExpr();

		double coefficients[][] = new double[n][n];

//		for (int i = 0; i < n; i++)
//			for (int j = 0; j < n; j++)
//				coefficients[i][j] = 0.;

		// perche se inverto i due for cambia il risultato ?
		for (int t = 0; t < n; t++)
			for (int j = 0; j < n; j++) {
				coefficients[j][t] += ((gamma[j][t] - epsilon[j][t]) * d[t]);
				for (int l = 0; l < n; l++)
					for (int k = 0; k < t; k++) {
						coefficients[l][k] += ((gamma[j][t] - delta[j][t]) * p[l]);
					}
			}

		for (int t = 0; t < n; t++) {
			cplex.addEq(cplex.sum(x[t]), 1);
			IloLinearNumExpr t_th_constraint = cplex.linearNumExpr();

			for (int j = 0; j < n; j++) {
				fo.addTerm(coefficients[t][j], x[t][j]);
				t_th_constraint.addTerm(1, x[j][t]);

			}
			cplex.addEq(1, t_th_constraint);

		}

		cplex.addMinimize(fo);

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
			} else if (i % 2 == 0) {
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
//		for (int i = 0; i < n; i++) {
//			for (int j = 0; j < n; j++) {
//				if (x_val[i][j] == 1.)
//					System.out.println("x_{" + i + "," + j + "} = " + x_val[i][j]);
//				System.out.println("w_{" + i + "," + j + "} = " + w[i][j]);
//
//				System.out.println("subgamma_{" + i + "," + j + "} = " + subgrad_gamma[i][j]);
//				System.out.println("subdelta{" + i + "," + j + "} = " + subgrad_delta[i][j]);
//				System.out.println("subeps{" + i + "," + j + "} = " + subgrad_epsilon[i][j]);
//
//				System.out.println("gamma_{" + i + "," + j + "} = " + gamma[i][j] + " ");
//				System.out.print("delta{" + i + "," + j + "} = " + delta[i][j] + " ");
//				System.out.println("eps{" + i + "," + j + "} = " + epsilon[i][j] + " ");
//			}
//			System.out.println();
//		}

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

		currLB = lb;
		if (lb > bestLB) {
			bestLB = lb;
		}

		double completionA = 0, completionB = 0;
		double current = 0;

		for (int t = 0; t < n; t++)
			for (int j = 0; j < n; j++)
				if (x_val[j][t] >= 1 - Math.pow(10, -6)) {
					current += p[j];
					if (j < nA)
						completionA += current;
					else
						completionB += current;
					break;
				}

		double ub = Math.max(completionA / nA - completionB / nB, completionB / nB - completionA / nA);
		currUB = ub;
		if (ub < bestUB) {
			lastIter = countIter;
			bestUB = ub;
			timeToBest = System.currentTimeMillis() - start;
		}
	}

	private static void updateMultipliers() {
		double factor = (-currLB) / subgradientLength;

		alpha = Math.max(0, alpha + subgrad_alpha * factor);
		beta = Math.max(0, beta + subgrad_beta * factor);

		for (int j = 0; j < n; j++)
			for (int t = 0; t < n; t++) {
				gamma[j][t] = Math.max(0, gamma[j][t] + subgrad_gamma[j][t] * factor);
				delta[j][t] = Math.max(0, delta[j][t] + subgrad_delta[j][t] * factor);
				epsilon[j][t] = Math.max(0, epsilon[j][t] + subgrad_epsilon[j][t] * factor);
			}

	}

	private static void computeSubGrad() {

		subgrad_alpha = 0;
		subgrad_beta = 0;

		subgradientLength = 0;

		double toAdd = 0;
		for (int t = 0; t < n; t++) {
			for (int j = 0; j < n; j++) {

				if (j < nA) {
					subgrad_alpha += w[j][t] / nA;
					subgrad_beta -= w[j][t] / nA;
				} else {
					subgrad_alpha -= w[j][t] / nB;
					subgrad_beta += w[j][t] / nB;
				}

				subgrad_gamma[j][t] = d[t] * x_val[j][t];
				subgrad_gamma[j][t] -= (w[j][t] + d[t]);

				subgrad_delta[j][t] = w[j][t];

				subgrad_epsilon[j][t] = w[j][t] - d[t] * x_val[j][t];

				subgrad_gamma[j][t] += toAdd;

				subgrad_delta[j][t] -= toAdd;

				subgradientLength += Math.pow(subgrad_gamma[j][t], 2);
				subgradientLength += Math.pow(subgrad_delta[j][t], 2);
				subgradientLength += Math.pow(subgrad_epsilon[j][t], 2);

			}
			for (int l = 0; l < n; l++)
				if (x_val[l][t] == 1.)
					toAdd += p[l];

		}

		subgrad_alpha += Pa / nA;
		subgrad_alpha -= Pb / nB;
		subgrad_alpha -= v;

		subgrad_beta -= Pa / nA;
		subgrad_beta += Pb / nB;
		subgrad_beta -= v;

		subgradientLength += Math.pow(subgrad_alpha, 2);
		subgradientLength += Math.pow(subgrad_beta, 2);
	}

	private static void computeOptimalWandV(IloCplex cplex, IloNumVar[][] x)
			throws UnknownObjectException, IloException {
		for (int i = 0; i < n; i++)
			x_val[i] = cplex.getValues(x[i]);

		if (1 - alpha - beta >= 0)
			v = 0;
		else
			v = maxBound;

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

	private static void initParam(Double init) {

		subgrad_alpha = 0.;
		subgrad_beta = 0;
		timeLimit = 1800;
		subgradientLength = 0;

		countIter = 0;
		lastIter = 0;
		bestUB = Double.MAX_VALUE;
		bestLB = -Double.MAX_VALUE;
		currUB = 0.;
		currLB = 0.;
		maxBound = 0.;

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

		r.setSeed(seed);
		seed++;

		for (int i = 0; i < n; i++) {
			p[i] = r.nextInt(25) + 1;
			r.nextInt();
		}

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
