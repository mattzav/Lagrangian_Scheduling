package Integer;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.IncumbentCallback;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

public class IntegerWeighted {
	private static final String EXCEL_FILE_LOCATION = "src\\results_cplex\\results.xls";
	static WritableWorkbook workBook = null;
	static WritableSheet excelSheet;

	public static int n, nA, nB; //
	public static double[] d;//
	public static int p[]; //
	public static double Pa, Pb, P;
	public static IloNumVar[][] x;
	public static IloNumVar[][] w;

	public static long start, elapsed, elapsedFor5;
	public static Random r;
	public static int[] weights;
	public static int seed = 1;

	public static void main(String[] args) {
		r = new Random();

//		createExcelFile();
//		int excelRow = 1;

		for (nA = 20; nA <= 20; nA += 51) {
			for (nB = 20; nB <= 20; nB += 5) {
				System.out.println("\n STARTING nA = " + nA + " - nB = " + nB);
				System.out.println("------------");
				int count = 0;
				for (int scenario = 1; scenario < 2; scenario++) {

					r.setSeed(seed++);
					System.out.println("\n SCENARIO = " + scenario + "\n");

					try {
						n = nA + nB;
//					System.out.println(nA + " " + nB);
						initParam();
						IloCplex cplex = new IloCplex();
						cplex.setOut(null);
						// cplex.setParam(IloCplex.Param.MIP.Tolerances.AbsMIPGap, 0.05);

						createIntegerModel(cplex);

//						cplex.use(new Callback());

						start = System.currentTimeMillis();
						elapsed = -1;
						elapsedFor5 = -1;

//						cplex.exportModel("src\\\\results_cplex\\model.lp");
						if (cplex.solve()) {

//							// add to file excel the resulting time
//							addValueToExcelFile(excelRow, nA, nB, cplex.getObjValue(), elapsed, elapsedFor5);
//							excelRow++;
							System.out.println("\n FO = " + cplex.getObjValue());
							if (cplex.getObjValue() > 0)
								count++;
//
							for (int i = 0; i < n; i++) {
//							// if (d[i] > 0)
//							// System.out.println("d_" + i + " = " + d[i]);
								for (int j = 0; j < n; j++) {
									if (cplex.getValue(x[i][j]) >= 1 - Math.pow(10, -6))
										System.out.println("x_{" + i + "," + j + "} = " + cplex.getValue(x[i][j]));
								}
							}

						}
					} catch (

					IloException e) {
//						closeExcelFile();
						System.out.println("err");
						e.printStackTrace();
					}
				}
				System.out.println(
						"WITH nA = " + nA + " - nB = " + nB + " found " + count + " problem with nonzero objective");
				// add 3 row to leave space between group having different value of n
//				excelRow += 3;
			}

			// close excel file
//			closeExcelFile();

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

	private static void addValueToExcelFile(int excelRow, int nA, int nB, double bestUB, long elapsed,
			long elapsedFor5) {
		try {

			Number number = new Number(0, excelRow, nA);
			excelSheet.addCell(number);

			number = new Number(1, excelRow, nB);
			excelSheet.addCell(number);

			number = new Number(2, excelRow, bestUB);
			excelSheet.addCell(number);

			number = new Number(3, excelRow, elapsed / 1000);
			excelSheet.addCell(number);

			number = new Number(4, excelRow, elapsedFor5 / 1000);
			excelSheet.addCell(number);

			for (int i = 7; i < 7 + n; i++) {
				excelSheet.addCell(new Number(i, excelRow, p[i - 7]));
			}

		} catch (Exception e) {
			throw new RuntimeException("Error adding excel value");
		}
	}

	private static void createExcelFile() {
		try {
			workBook = Workbook.createWorkbook(new File(EXCEL_FILE_LOCATION));

			// create an Excel sheet
			excelSheet = workBook.createSheet("Lagrangian Results", 0);

			// add header into the Excel sheet
			Label label = new Label(0, 0, "nA");
			excelSheet.addCell(label);

			label = new Label(1, 0, "nB");
			excelSheet.addCell(label);

			label = new Label(2, 0, "Best UB");
			excelSheet.addCell(label);

			label = new Label(3, 0, "Time to Optimum");
			excelSheet.addCell(label);

			label = new Label(4, 0, "Time to 5%");
			excelSheet.addCell(label);

		} catch (Exception e) {
			throw new RuntimeException("error creating excel file");
		}

	}

	public static void initParam() {

		p = new int[n];
		d = new double[n];
		weights = new int[n];

		System.out.println("----------------");
		System.out.println("Proc");
		for (int i = 0; i < n; i++) {
			p[i] = r.nextInt(25) + 1;
			System.out.print(p[i] + " ");
		}
		System.out.println("\n---------------\nWeigths");
		for (int i = 0; i < n; i++) {
			weights[i] = r.nextInt(25) + 1;
			System.out.print(weights[i] + " ");
		}

		Pa = 0;
		Pb = 0;
		P = 0;
		for (int i = 0; i < nA; i++) {
			Pa += p[i] * weights[i];
			P += p[i];
		}

		for (int i = nA; i < n; i++) {
			Pb += p[i] * weights[i];
			P += p[i];
		}

		d[0] = 0.;// siamo sicuri? d[t] = (t-1)*(Pa+Pb)

		for (int i = 0; i < n; i++)
			if (i >= 1)
				d[i] = (i) * P;

	}

	private static void createIntegerModel(IloCplex cplex) throws IloException {

		x = new IloNumVar[n][];
		w = new IloNumVar[n][];

		for (int i = 0; i < n; i++) {
			x[i] = cplex.boolVarArray(n);
			w[i] = cplex.numVarArray(n, 0, Double.MAX_VALUE);
		}

		for (int i = 0; i < n; i++)
			for (int j = 0; j < n; j++) {
				x[i][j].setName("x_{" + i + "," + j + "}");

				w[i][j].setName("w_{" + i + "," + j + "}");
			}

		IloNumVar v = cplex.numVar(0, Double.MAX_VALUE);// usare LB = 0 o no
		v.setName("v");
		cplex.addMinimize(v);

		IloLinearNumExpr v_first_constraint = cplex.linearNumExpr();
		v_first_constraint.addTerm(-1, v);

		IloLinearNumExpr v_second_constraint = cplex.linearNumExpr();
		v_second_constraint.addTerm(-1, v);

		for (int t = 0; t < n; t++) {
			for (int j = 0; j < nA; j++) {
				v_first_constraint.addTerm((double) weights[j] / nA, w[j][t]);

				v_second_constraint.addTerm(-(double) weights[j] / nA, w[j][t]);
			}
			for (int j = nA; j < n; j++) {
				v_first_constraint.addTerm(-(double) weights[j] / nB, w[j][t]);
				v_second_constraint.addTerm((double) weights[j] / nB, w[j][t]);

			}
		}
		cplex.addGe(Pb / nB - Pa / nA, v_first_constraint);
		cplex.addGe(Pa / nA - Pb / nB, v_second_constraint);

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

		for (int j = 0; j < n; j++)
			for (int t = 0; t < n; t++) {
				IloLinearNumExpr j_t_1_th_constraint = cplex.linearNumExpr();
				IloLinearNumExpr j_t_2_th_constraint = cplex.linearNumExpr();
				IloLinearNumExpr j_t_3_th_constraint = cplex.linearNumExpr();

				j_t_1_th_constraint.addTerm(d[t], x[j][t]);
				j_t_1_th_constraint.addTerm(-1, w[j][t]);

				j_t_2_th_constraint.addTerm(1, w[j][t]);

				j_t_3_th_constraint.addTerm(1, w[j][t]);
				j_t_3_th_constraint.addTerm(-d[t], x[j][t]);

				for (int l = 0; l < n; l++)
					for (int k = 0; k < t; k++) {
						j_t_1_th_constraint.addTerm(p[l], x[l][k]);
						j_t_2_th_constraint.addTerm(-p[l], x[l][k]);
					}
				cplex.addGe(d[t], j_t_1_th_constraint);
				cplex.addGe(0, j_t_2_th_constraint);
				cplex.addGe(0, j_t_3_th_constraint);
			}

	}

	static class Callback extends IncumbentCallback {

		@Override
		protected void main() throws IloException {

			System.out.println(getObjValue());
			if (Math.abs(getObjValue()) <= 0.05 && elapsedFor5 == -1)
				elapsedFor5 = System.currentTimeMillis() - start;
			elapsed = System.currentTimeMillis() - start;
		}
	}

}