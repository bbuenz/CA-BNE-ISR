package ch.uzh.ifi.ce.cabne.helpers;

import java.util.Arrays;

public class SampleCollector {
    private final int vars;
    private final double[] means;
    private final double[] runningVars;

    int numsamples;

    public SampleCollector(int numVariables) {
        vars = numVariables;
        means = new double[vars];
        runningVars = new double[vars * vars];
    }

    public void addSample(double... sample) {
        if (sample.length == vars) {
            numsamples++;
            double[] deltas = new double[vars];
            for (int i = 0; i < vars; ++i) {
                deltas[i] = sample[i] - means[i];
                means[i] += deltas[i] / numsamples;
            }
            for (int i = 0; i < vars; ++i) {
                for (int j = i; j < vars; ++j) {
                    runningVars[i * vars + j] += deltas[i] * deltas[j];
                    runningVars[j * vars + i] = runningVars[i * vars + j];
                }
            }
        }
    }


    public double[] getMeans() {
        return means;
    }

    public double[] getCovarianceMatrix() {
        return Arrays.stream(runningVars).map(d -> d / (numsamples - 1)).toArray();
    }

    public double[] computeFractionalMeanError(int numerator, int denominator) {
        double[] means = getMeans();
        double[] covariances = getCovarianceMatrix();
        double meanN = means[numerator];
        double meanD = means[denominator];
        double meanNSquared = meanN * meanN;
        double meanDSquared = meanD * meanD;
        double varN = covariances[numerator + vars * numerator];
        double varD = covariances[denominator + vars * denominator];
        double coVar = covariances[numerator + vars * denominator];
        double var = meanNSquared / meanDSquared * (varN / meanNSquared - 2 * coVar / (meanN * meanD) + varD / meanDSquared);
        return new double[]{meanN / meanD, Math.sqrt(var / numsamples)};
    }
}
