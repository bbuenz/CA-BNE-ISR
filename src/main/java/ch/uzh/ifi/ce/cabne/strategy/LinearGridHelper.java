package ch.uzh.ifi.ce.cabne.strategy;

public class LinearGridHelper extends QuadraticGridHelper {
	double vMax;
	int N;

	public LinearGridHelper(int n, double vMax) {
		// NOTE: could use this instantiation of the QuadraticGridHelper class, but it would lead to numerical errors.
		// It's better to override some methods and get 100% correct behaviour
		super(n, vMax, 0.99999); // need to still call this because it's the only constructor 
		this.vMax = vMax;
		N = n;
	}
	
	public double evalPolynomial(double x) {
		return (x / vMax) * (N-1);
	}
	
	public double invertPolynomial(double y) {
		return (y / (N-1)) * vMax;
	}
	
}
