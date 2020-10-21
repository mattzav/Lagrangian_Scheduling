import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class IntegerSolver {
	public static int n = 110, nA = 100, nB = 10; //
	public static double[] d;//
	public static int p[]; //
	public static double Pa, Pb;
	public static IloNumVar[][] x;
	public static IloNumVar[][] w;

	public static void main(String[] args) {

		initParam();
		IloCplex cplex;
		try {
			cplex = new IloCplex();
			cplex.setOut(null);
			createIntegerModel(cplex);

			if (cplex.solve()) {
				System.out.println(cplex.getObjValue());
				for (int i = 0; i < n; i++) {
					if (d[i] > 0)
						System.out.println("d_" + i + " = " + d[i]);
					for (int j = 0; j < n; j++) {
						if (cplex.getValue(x[i][j]) == 1)
							System.out.println("x_{" + i + "," + j + "}");
						if (cplex.getValue(w[i][j]) > 0)
							System.out.println("w_{" + i + "," + j + "} = " + cplex.getValue(w[i][j]));
					}
				}

			}
		} catch (IloException e) {
			System.out.println("err");
			e.printStackTrace();
		}

	}

	public static void initParam() {
		p = new int[n];
		d = new double[n];

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

		d[0] = 0.;// siamo sicuri? d[t] = (t-1)*(Pa+Pb)

		for (int i = 0; i < n; i++)
			if (i >= 1)
				d[i] = (i) * (Pa + Pb);

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
				v_first_constraint.addTerm(1. / nA, w[j][t]);

				v_second_constraint.addTerm(-1. / nA, w[j][t]);
			}
			for (int j = nA; j < n; j++) {
				v_first_constraint.addTerm(-1. / nB, w[j][t]);
				v_second_constraint.addTerm(1. / nB, w[j][t]);

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

		cplex.exportModel("model.lp");

	}

}