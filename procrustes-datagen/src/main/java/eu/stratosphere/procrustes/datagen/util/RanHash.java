package eu.stratosphere.procrustes.datagen.util;

/**
 * c.f. "Press, William H. Numerical recipes 3rd edition: The art of scientific computing. Cambridge
 * university press, 2007.", pp. 352
 */
public class RanHash implements SymmetricPRNG {

    private static final double D_2_POW_NEG_64 = 5.4210108624275221700e-20;

    private long seed;

    private long currentPos;

    private double nextNextGaussian;

    private boolean haveNextNextGaussian = false;

    public RanHash() {}

    public RanHash(long seed) {
        seed(seed);
    }

    @Override
    public void seed(long seed) {
        this.seed = seed;
    }

    @Override
    public void skipTo(long pos) {
        currentPos = pos;
    }

    @Override
    public double next() {

        long x = seed + currentPos;

        x = 3935559000370003845L * x + 2691343689449507681L;
        x = x ^ (x >> 21);
        x = x ^ (x << 37);
        x = x ^ (x >> 4);
        x = 4768777513237032717L * x;
        x = x ^ (x << 20);
        x = x ^ (x >> 41);
        x = x ^ (x << 5);

        currentPos++;

        return x * D_2_POW_NEG_64 + 0.5;
    }

    public double nextGaussian() {
        return inverseNormalCDF(next());
    }

    public double nextPareto(double alpha) {
        return inverseParetoCDF(next(), alpha);
    }

    /**
     * Inverse pareto-distribution as taken from
     * http://www2.math.uu.se/research/telecom/software/stprobdist.html
     * 
     * @param p input from U[0, 1]
     * @param alpha shape parameter
     * @return pareto distributed random number
     */
    private double inverseParetoCDF(double p, double alpha) {
        return Math.pow(1 - p, -1 / alpha) - 1;
    }

    /**
     * Lower tail quantile for standard normal distribution function.
     * 
     * This function returns an approximation of the inverse cumulative standard normal distribution
     * function. I.e., given P, it returns an approximation to the X satisfying P = Pr{Z <= X} where
     * Z is a random variable from the standard normal distribution.
     * 
     * The algorithm uses a minimax approximation by rational functions and the result has a
     * relative error whose absolute value is less than 1.15e-9.
     * 
     * Author: Peter John Acklam (Javascript version by Alankar Misra @ Digital Sutras
     * (alankar@digitalsutras.com)) Time-stamp: 2003-05-05 05:15:14 E-mail: pjacklam@online.no WWW
     * URL: http://home.online.no/~pjacklam
     * 
     * An algorithm with a relative error less than 1.15*10-9 in the entire region.
     * 
     * @param p input from U[0, 1]
     * @return output from N(0, 1)
     */
    private double inverseNormalCDF(double p) {
        // Coefficients in rational approximations
        double[] a =
                {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01,
                        2.506628277459239e+00};

        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01};

        double[] c =
                {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00,
                        2.938163982698783e+00};

        double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00};

        // Define break-points.
        double plow = 0.02425;
        double phigh = 1 - plow;

        // Rational approximation for lower region:
        if (p < plow) {
            double q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }

        // Rational approximation for upper region:
        if (phigh < p) {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }

        // Rational approximation for central region:
        double q = p - 0.5;
        double r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
    }


    @Override
    public int nextInt(int k) {
        // omitting >= 0 check here
        return (int) Math.floor(next() * k);
    }
}
