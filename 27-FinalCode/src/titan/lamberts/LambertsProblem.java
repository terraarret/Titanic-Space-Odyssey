package titan.lamberts;

import titan.math.Vector3d;

/*****************************************************************************
 *   Copyright (C) 2004-2018 The pykep development team,                     *
 *   Advanced Concepts Team (ACT), European Space Agency (ESA)               *
 *                                                                           *
 *   https://gitter.im/esa/pykep                                             *
 *   https://github.com/esa/pykep                                            *
 *                                                                           *
 *   act@esa.int                                                             *
 *                                                                           *
 *   This program is free software; you can redistribute it and/or modify    *
 *   it under the terms of the GNU General Public License as published by    *
 *   the Free Software Foundation; either version 2 of the License, or       *
 *   (at your option) any later version.                                     *
 *                                                                           *
 *   This program is distributed in the hope that it will be useful,         *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of          *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the           *
 *   GNU General Public License for more details.                            *
 *                                                                           *
 *   You should have received a copy of the GNU General Public License       *
 *   along with this program; if not, write to the                           *
 *   Free Software Foundation, Inc.,                                         *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.               *
 *****************************************************************************/
public class LambertsProblem {

    private double[] r1, r2;
    private double R1, R2;
    private double m_c, m_s;
    private double m_lambda, lambda2;
    private double[] ir1, ir2, ih, it1, it2;
    double Pi = Math.PI;
    double[] x0;
    double  DT = 0.0, DDT = 0.0, DDDT = 0.0;
    double tof;
    double[][] m_v1, m_v2;

    public LambertsProblem(Vector3d r1V, Vector3d r2V, double tofi, double mu, int cw, int m_multi_revs){

        tof = tofi;
        r1 = toMatrixVector(r1V);
        r2 = toMatrixVector(r2V);
        R1 = r1V.norm();
        R2 = r2V.norm();

        m_c = sqrt((r2[0] - r1[0]) * (r2[0] - r1[0]) + (r2[1] - r1[1]) * (r2[1] - r1[1])  + (r2[2] - r1[2]) * (r2[2] - r1[2]));

        m_s =  (m_c + R1 + R2) / 2.0;
        initalizeVectors();
        vers(ir1, r1);
        vers(ir2, r2);
        cross(ih, ir1, ir2);
        vers(ih, ih);

        lambda2 = 1.0 - m_c / m_s;
        m_lambda = sqrt(lambda2);

        if (ih[2] < 0.0) // Transfer angle is larger than 180 degrees as seen from abive the z axis
        {
            m_lambda = -m_lambda;
            cross(it1, ir1, ih);
            cross(it2, ir2, ih);
        } else {
            cross(it1, ih, ir1);
            cross(it2, ih, ir2);
        }
        vers(it1, it1);
        vers(it2, it2);

        double lambda3 = m_lambda * lambda2;
        double T = sqrt(2.0 * mu / m_s / m_s / m_s) * tof;

        int m_Nmax = (int)(T / Pi);

        double T00 = Math.acos(m_lambda) + m_lambda * sqrt(1.0 - lambda2);
        double T0 = (T00 + m_Nmax * Pi);
        double T1 = 2.0 / 3.0 * (1.0 - lambda3);
/*        if (m_Nmax > 0) {
            if (T < T0) { // We use Halley iterations to find xM and TM
                int it = 0;
                double err = 1.0;
                double T_min = T0;
                double x_old = 0.0, x_new = 0.0;
                while (true) {
                    dTdx(x_old, T_min);
                    if (DT != 0.0) {
                        x_new = x_old - DT * DDT / (DDT * DDT - DT * DDDT / 2.0);
                    }
                    err = Math.abs(x_old - x_new);
                    if ((err < 1e-13) || (it > 12)) {
                        break;
                    }
                    x2tof(T_min, x_new, m_Nmax);
                    x_old = x_new;
                    it++;
                }
                if (T_min > T) {
                    m_Nmax -= 1;
                }
            }
        }*/
        m_Nmax = Math.min(m_multi_revs, m_Nmax);


        // 2.2 We now allocate the memory for the output variables
        m_v1 = new double [m_Nmax * 2 + 1][3];
        m_v2 = new double [m_Nmax * 2 + 1][3];
        double[] m_iters = new double[m_Nmax * 2 + 1];
        x0 = new double[m_Nmax * 2 + 1];

        // 3 - We may now find all solutions in x,y
        // 3.1 0 rev solution
        // 3.1.1 initial guess
        if (T >= T00) {
            x0[0] = -(T - T00) / (T - T00 + 4);
        } else if (T <= T1) {
            x0[0] = T1 * (T1 - T) / (2.0 / 5.0 * (1 - lambda2 * lambda3) * T) + 1;
        } else {
            x0[0] = Math.pow((T / T00), 0.69314718055994529 / Math.log(T1 / T00)) - 1.0;
        }
        // 3.1.2 Householder iterations
        m_iters[0] = householder(T, x0[0], 0, 1e-5, 15);
        // 3.2 multi rev solutions
        double tmp;
        for (int i = 1; i < m_Nmax + 1; ++i) {
            // 3.2.1 left Householder iterations
            tmp = Math.pow((i * Pi + Pi) / (8.0 * T), 2.0 / 3.0);
            x0[2 * i - 1] = (tmp - 1) / (tmp + 1);
            m_iters[2 * i - 1] = householder(T, x0[2 * i - 1], i, 1e-8, 15);
            // 3.2.1 right Householder iterations
            tmp = Math.pow((8.0 * T) / (i * Pi), 2.0 / 3.0);
            x0[2 * i] = (tmp - 1) / (tmp + 1);
            m_iters[2 * i] = householder(T, x0[2 * i], i, 1e-8, 15);
        }
        // 4 - For each found x value we reconstruct the terminal velocities
        double gamma = sqrt(mu * m_s / 2.0);
        double rho = (R1 - R2) / m_c;
        double sigma = sqrt(1 - rho * rho);
        double vr1, vt1, vr2, vt2, y;
        for (int i = 0; i < x0.length; ++i) {
            y = sqrt(1.0 - lambda2 + lambda2 * x0[i] * x0[i]);
            vr1 = gamma * ((m_lambda * y - x0[i]) - rho * (m_lambda * y + x0[i])) / R1;
            vr2 = -gamma * ((m_lambda * y - x0[i]) + rho * (m_lambda * y + x0[i])) / R2;
            double vt = gamma * sigma * (y + m_lambda * x0[i]);
            vt1 = vt / R1;
            vt2 = vt / R2;
            for (int j = 0; j < 3; ++j)
                m_v1[i][j] = vr1 * ir1[j] + vt1 * it1[j];
            for (int j = 0; j < 3; ++j)
                m_v2[i][j] = vr2 * ir2[j] + vt2 * it2[j];
        }

    }

    private double[] toMatrixVector(Vector3d vec){
        double[] out = new double[3];
        out[0] = vec.getX();
        out[1] = vec.getY();
        out[2] = vec.getZ();
        return out;
    }

    private void initalizeVectors(){
        ir1 = new double[]{0, 0, 0};
        ir2 = new double[]{0, 0, 0};
        ih = new double[]{0, 0, 0};
        it1 = new double[]{0, 0, 0};
        it2 = new double[]{0, 0, 0};
    }

    private void vers(double[] out, double[] in){
        double c = norm(in);
        for (int i = 0; i < 3; ++i)
            out[i] = in[i] / c;
    }

    private void cross(double[] out, double[] v1, double[] v2){
            out[0] = v1[1] * v2[2] - v1[2] * v2[1];
            out[1] = v1[2] * v2[0] - v1[0] * v2[2];
            out[2] = v1[0] * v2[1] - v1[1] * v2[0];
    }

    private double norm(double[] v1){
        return sqrt(v1[0] * v1[0] + v1[1] * v1[1] + v1[2] * v1[2]);
    }

    private double sqrt(double in){
        return Math.sqrt(in);
    }

    private void dTdx(double x, double T){
        double l2 = m_lambda * m_lambda;
        double l3 = l2 * m_lambda;
        double umx2 = 1.0 - x * x;
        double y = sqrt(1.0 - l2 * umx2);
        double y2 = y * y;
        double y3 = y2 * y;
        DT = 1.0 / umx2 * (3.0 * T * x - 2.0 + 2.0 * l3 * x / y);
        DDT = 1.0 / umx2 * (3.0 * T + 5.0 * x * DT + 2.0 * (1.0 - l2) * l3 / y3);
        DDDT = 1.0 / umx2 * (7.0 * x * DDT + 8.0 * DT - 6.0 * (1.0 - l2) * l2 * l3 * x / y3 / y2);
    }

    private void x2tof(double x, int N){
        double battin = 0.01;
        double lagrange = 0.2;
        double dist = Math.abs(x - 1);
        if (dist < lagrange && dist > battin) { // We use Lagrange tof expression
            x2tof2(x, N);
            return;
        }
        double K = m_lambda * m_lambda;
        double E = x * x - 1.0;
        double rho = Math.abs(E);
        double z = sqrt(1 + K * E);
        if (dist < battin) { // We use Battin series tof expression
            double eta = z - m_lambda * x;
            double S1 = 0.5 * (1.0 - m_lambda - x * eta);
            double Q = hypergeometricF(S1, 1e-11);
            Q = 4.0 / 3.0 * Q;
            tof = (eta * eta * eta * Q + 4.0 * m_lambda * eta) / 2.0 + N * Pi / Math.pow(rho, 1.5);
            return;
        } else { // We use Lancaster tof expresion
            double y = sqrt(rho);
            double g = x * z - m_lambda * E;
            double d = 0.0;
            if (E < 0) {
                double l = Math.acos(g);
                d = N * Pi + l;
            } else {
                double f = y * (z - m_lambda * x);
                d = Math.log(f + g);
            }
            tof = (x - m_lambda * z - d / y) / E;
            return;
        }
    }

    private void x2tof2(double x, int N){
        double a = 1.0 / (1.0 - x * x);
        if (a > 0) // ellipse
        {
            double alfa = 2.0 * Math.acos(x);
            double beta = 2.0 * Math.asin(sqrt(m_lambda * m_lambda / a));
            if (m_lambda < 0.0) beta = -beta;
            tof = ((a * sqrt(a) * ((alfa - Math.sin(alfa)) - (beta - Math.sin(beta)) + 2.0 * Pi * N)) / 2.0);
        } else {
            double alfa = 2.0 * acosh(x);
            double beta = 2.0 * asinh(sqrt(-m_lambda * m_lambda / a));
            if (m_lambda < 0.0) beta = -beta;
            tof = (-a * sqrt(-a) * ((beta - Math.sinh(beta)) - (alfa - Math.sinh(alfa))) / 2.0);
        }
    }

    private double hypergeometricF(double z, double tol){
        double Sj = 1.0;
        double Cj = 1.0;
        double err = 1.0;
        double Cj1 = 0.0;
        double Sj1 = 0.0;
        int j = 0;
        while (err > tol) {
            Cj1 = Cj * (3.0 + j) * (1.0 + j) / (2.5 + j) * z / (j + 1);
            Sj1 = Sj + Cj1;
            err = Math.abs(Cj1);
            Sj = Sj1;
            Cj = Cj1;
            j = j + 1;
        }
        return Sj;
    }

    private int householder(double T, double x0, int N, double eps, int iter_max) {
        int it = 0;
        double err = 1.0;
        double xnew = 0.0;
        double tof = 0.0, delta = 0.0, DT = 0.0, DDT = 0.0, DDDT = 0.0;
        while ((err > eps) && (it < iter_max)) {
            x2tof(x0, N);
            dTdx(x0, tof);
            delta = tof - T;
            double DT2 = DT * DT;
            xnew = x0 - delta * (DT2 - delta * DDT / 2.0) / (DT * (DT2 - delta * DDT) + DDDT * delta * delta / 6.0);
            err = Math.abs(x0 - xnew);
            x0 = xnew;
            it++;
        }
        return it;
    }

    public double[][] getM_v1(){ return m_v1;}

    public double[][] getM_v2(){ return m_v2;}

    private double acosh(double x){
        return Math.log(x + sqrt(x*x-1));
    }

    private double asinh(double x){
        return Math.log(x + sqrt(x*x+1));
    }
}
